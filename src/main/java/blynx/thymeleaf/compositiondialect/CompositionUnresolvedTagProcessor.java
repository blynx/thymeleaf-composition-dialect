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
 * Catches every tag under this dialect's prefix in {@link TemplateMode#XML}, where it is registered alone
 * — components are HTML-only, so {@link CompositionElementModelProcessor} (the wildcard processor that
 * claims and resolves every {@code c:}-prefixed tag in HTML mode, folding in the equivalent HTML check
 * itself) never runs in XML mode. Under XML mode every tag under this prefix is, by that same reasoning,
 * unresolved.
 *
 * <p>Without this, such a tag was not an error at all: nothing matched it, so it passed through as literal,
 * unprocessed markup in the rendered output.
 *
 * <p>{@link org.thymeleaf.processor.element.AbstractElementTagProcessor} has no constructor for "any
 * element name under my prefix" — only one exact name, or every element in the document regardless of
 * prefix. {@link MatchingElementName#forAllElementsWithPrefix} is the one that actually scopes the wildcard
 * to this dialect, so this class implements {@link IElementTagProcessor} directly to reach it.
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
