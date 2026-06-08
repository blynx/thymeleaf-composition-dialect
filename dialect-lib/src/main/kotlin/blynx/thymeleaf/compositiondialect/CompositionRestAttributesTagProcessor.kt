package blynx.thymeleaf.compositiondialect

import org.thymeleaf.context.ITemplateContext
import org.thymeleaf.engine.AttributeName
import org.thymeleaf.model.IProcessableElementTag
import org.thymeleaf.processor.element.AbstractAttributeTagProcessor
import org.thymeleaf.processor.element.IElementTagStructureHandler
import org.thymeleaf.templatemode.TemplateMode

class CompositionRestAttributesTagProcessor(dialectPrefix: String) : AbstractAttributeTagProcessor(
    TemplateMode.HTML, dialectPrefix, null, false, "rest", true, PRECEDENCE, true
) {
    companion object {
        const val PRECEDENCE = 200
    }

    override fun doProcess(
        context: ITemplateContext,
        tag: IProcessableElementTag,
        attributeName: AttributeName,
        attributeValue: String,
        structureHandler: IElementTagStructureHandler,
    ) {
        val component = context.getVariable("this") as? CompositionComponent ?: return
        component.restAttributes.forEach { (key, value) ->
            structureHandler.setAttribute(key, value?.toString() ?: "")
        }
    }
}
