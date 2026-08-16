package blynx.thymeleaf.compositiondialect;

import org.thymeleaf.context.ITemplateContext;
import org.thymeleaf.exceptions.TemplateProcessingException;
import org.thymeleaf.model.IProcessableElementTag;
import org.thymeleaf.processor.element.IElementTagProcessor;
import org.thymeleaf.processor.element.IElementTagStructureHandler;
import org.thymeleaf.processor.element.MatchingAttributeName;
import org.thymeleaf.processor.element.MatchingElementName;
import org.thymeleaf.templatemode.TemplateMode;

/**
 * Catches every tag under this dialect's prefix that no other processor claimed. {@link ComponentRegistry}
 * registers one {@link CompositionElementModelProcessor} per known component, each matching one specific
 * tag name at {@link CompositionElementModelProcessor#PRECEDENCE}; a tag it claims is fully replaced before
 * this processor's turn, and the dialect's own grammar ({@code c:slot}, {@code c:_caller}) is consumed at
 * lower precedence still. So whatever survives to reach here, at the last precedence in the dialect, names
 * no component and no marker — by definition a typo, a missing {@link ComponentSource}, or a tag meant for
 * a different dialect prefix entirely.
 *
 * <p>Without this, such a tag was not an error at all: nothing matched it, so it passed through as literal,
 * unprocessed markup in the rendered output.
 *
 * <p>{@link org.thymeleaf.processor.element.AbstractElementTagProcessor} has no constructor for "any
 * element name under my prefix" — only one exact name, or every element in the document regardless of
 * prefix. {@link MatchingElementName#forAllElementsWithPrefix} is the one that actually scopes the wildcard
 * to this dialect, so this class implements {@link IElementTagProcessor} directly to reach it.
 *
 * <p>Registered for both {@link TemplateMode#HTML} and {@link TemplateMode#XML}, like
 * {@link CompositionSlotPlacementProcessor}: components are HTML-only, so under XML mode every tag under
 * this prefix is, by the same reasoning, unresolved.
 */
class CompositionUnresolvedTagProcessor implements IElementTagProcessor {

    /** Runs immediately after {@link CompositionElementModelProcessor}, the last real claimant of a tag. */
    static final int PRECEDENCE = CompositionElementModelProcessor.PRECEDENCE + 1;

    private final TemplateMode templateMode;
    private final MatchingElementName matchingElementName;

    CompositionUnresolvedTagProcessor(TemplateMode templateMode, String dialectPrefix) {
        this.templateMode = templateMode;
        this.matchingElementName = MatchingElementName.forAllElementsWithPrefix(templateMode, dialectPrefix);
    }

    @Override
    public TemplateMode getTemplateMode() {
        return templateMode;
    }

    @Override
    public int getPrecedence() {
        return PRECEDENCE;
    }

    @Override
    public MatchingElementName getMatchingElementName() {
        return matchingElementName;
    }

    @Override
    public MatchingAttributeName getMatchingAttributeName() {
        return null;
    }

    @Override
    public void process(ITemplateContext context, IProcessableElementTag tag, IElementTagStructureHandler structureHandler) {
        String message = CompositionDialect.DIALECT_NAME + ": <" + tag.getElementCompleteName()
                + "> is not a registered component tag. Check that it is spelled correctly and that its "
                + "component class is discovered by one of this dialect's ComponentSources.";
        if (tag.hasLocation()) {
            throw new TemplateProcessingException(message, tag.getTemplateName(), tag.getLine(), tag.getCol());
        }
        throw new TemplateProcessingException(message);
    }
}
