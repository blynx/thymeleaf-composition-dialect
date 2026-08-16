package blynx.thymeleaf.compositiondialect;

import org.thymeleaf.context.ITemplateContext;
import org.thymeleaf.engine.AttributeName;
import org.thymeleaf.model.IProcessableElementTag;
import org.thymeleaf.processor.element.AbstractAttributeTagProcessor;
import org.thymeleaf.processor.element.IElementTagStructureHandler;
import org.thymeleaf.templatemode.TemplateMode;

/**
 * Implements the {@code c:rest} attribute: spreads every attribute that the component did not
 * read onto the element it is placed on.
 */
public class CompositionRestAttributesTagProcessor extends AbstractAttributeTagProcessor {

    public static final int PRECEDENCE = 200;

    public CompositionRestAttributesTagProcessor(String dialectPrefix) {
        super(TemplateMode.HTML, dialectPrefix, null, false, "rest", true, PRECEDENCE, true);
    }

    @Override
    protected void doProcess(ITemplateContext context, IProcessableElementTag tag, AttributeName attributeName,
                             String attributeValue, IElementTagStructureHandler structureHandler) {
        if (!(context.getVariable("this") instanceof CompositionComponent component)) {
            return;
        }
        component.restAttributes().forEach((key, value) ->
                structureHandler.setAttribute(key, value != null ? value.toString() : ""));
    }
}
