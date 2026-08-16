package blynx.thymeleaf.compositiondialect;

import org.thymeleaf.context.ITemplateContext;
import org.thymeleaf.engine.AttributeName;
import org.thymeleaf.exceptions.TemplateProcessingException;
import org.thymeleaf.model.IProcessableElementTag;
import org.thymeleaf.processor.element.AbstractAttributeTagProcessor;
import org.thymeleaf.processor.element.IElementTagStructureHandler;
import org.thymeleaf.templatemode.TemplateMode;

/**
 * Implements the {@code c:rest} attribute: spreads every attribute that the component did not
 * read onto the element it is placed on.
 *
 * <p>Whose attributes those are is decided by whichever component is rendering — the same unprefixed
 * {@code this} the rest of the dialect reads. So this means "the component whose template I am written
 * in", not "a component registered under my prefix"; under two dialects at different prefixes, either
 * spelling acts on whatever is rendering. See {@code MultiPrefixTest}.
 */
public class CompositionRestAttributesTagProcessor extends AbstractAttributeTagProcessor {

    public static final int PRECEDENCE = 200;
    private static final String ATTRIBUTE_NAME = "rest";

    public CompositionRestAttributesTagProcessor(String dialectPrefix) {
        super(TemplateMode.HTML, dialectPrefix, null, false, ATTRIBUTE_NAME, true, PRECEDENCE, true);
    }

    @Override
    protected void doProcess(ITemplateContext context, IProcessableElementTag tag, AttributeName attributeName,
                             String attributeValue, IElementTagStructureHandler structureHandler) {
        // No component rendering means this was written where there is nothing to spread — a page, or
        // content being passed to a component, which steps back out to where it was written. Rejected
        // rather than quietly doing nothing, as the same misplacement already is for <c:slot>.
        if (!(context.getVariable(CompositionElementModelProcessor.COMPONENT_VARIABLE)
                instanceof CompositionComponent component)) {
            throw new TemplateProcessingException(CompositionDialect.DIALECT_NAME
                    + ": " + getDialectPrefix() + ":" + ATTRIBUTE_NAME + " was used where no component is "
                    + "rendering. It spreads the attributes of the component whose template it is written "
                    + "in, so it only means something inside a component's own template — not in a page, "
                    + "and not in content being passed to a component.");
        }
        component.restAttributes().forEach((key, value) ->
                structureHandler.setAttribute(key, value != null ? value.toString() : ""));
    }
}
