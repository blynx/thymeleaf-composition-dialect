package blynx.thymeleaf.compositiondialect

import org.thymeleaf.context.ITemplateContext
import org.thymeleaf.engine.TemplateModel
import org.thymeleaf.exceptions.TemplateProcessingException
import org.thymeleaf.model.IModel
import org.thymeleaf.model.ITemplateEvent
import org.thymeleaf.model.IProcessableElementTag
import org.thymeleaf.model.IStandaloneElementTag
import org.thymeleaf.model.IOpenElementTag
import org.thymeleaf.model.ICloseElementTag
import org.thymeleaf.processor.element.AbstractElementModelProcessor
import org.thymeleaf.processor.element.IElementModelStructureHandler
import org.thymeleaf.standard.expression.StandardExpressions
import org.thymeleaf.standard.expression.IStandardExpressionParser
import org.thymeleaf.standard.processor.StandardReplaceTagProcessor
import org.thymeleaf.templatemode.TemplateMode


class CompositionElementModelProcessor(
    dialectPrefix: String, private val elementName: String, private val componentClass: Class<out CompositionComponent>, componentsPath: String?
) : AbstractElementModelProcessor(
    TemplateMode.HTML, dialectPrefix, elementName, true, null, false, PRECEDENCE
) {
    private val slotTagName = "$dialectPrefix:slot"
    private val slotNameAttributeName = "$dialectPrefix:name"
    private val componentPath = buildComponentPath(componentsPath, componentClass, elementName)

    companion object {
        private const val PRECEDENCE: Int = StandardReplaceTagProcessor.PRECEDENCE
    }

    override fun doProcess(context: ITemplateContext, tag: IModel, structureHandler: IElementModelStructureHandler) {
        val expressionParser = StandardExpressions.getExpressionParser(context.configuration)
        val fragmentModel = loadFragmentModel(context)

        val slots = extractSlots(tag)
        val newModel = prepareModel(context, fragmentModel, slots)

        val attrs = extractAttrs(tag.get(0) as IProcessableElementTag, context, expressionParser)
        val componentContext = CompositionComponentContext(attrs, slots.keys, context, structureHandler)
        val componentInstance = componentClass.constructors.first().newInstance(componentContext)
        structureHandler.setLocalVariable(elementName, componentInstance)

        tag.reset() // clear the model reference
        tag.addModel(newModel)
    }



    private fun loadFragmentModel(context: ITemplateContext): TemplateModel {
        try {
            return context.configuration.templateManager.parseStandalone(context, componentPath, null, null, true, true)
        } catch (e: Exception) {
            throw TemplateProcessingException("${CompositionDialect.DIALECT_NAME}: Could not load template for component \"$elementName\" from \"$componentPath.html\" (relative to thymeleaf templates path)")
        }
    }



    private fun extractSlots(tag: IModel): HashMap<String?, ArrayList<ITemplateEvent>> {
        val slots = HashMap<String?, ArrayList<ITemplateEvent>>()
        var slotName: String? = null
        var level = 0
        for (i in 1 until tag.size() - 1) {
            if (tag.get(i) is IOpenElementTag) {
                level++
            } 
            else if (tag.get(i) is ICloseElementTag) {
                level--
            }
            if ((level == 1) && (tag.get(i) is IProcessableElementTag) && (tag.get(i) as IProcessableElementTag).hasAttribute(slotTagName)) {
                slotName = (tag.get(i) as IProcessableElementTag).getAttributeValue(slotTagName)
            }
            slots.getOrPut(slotName) { ArrayList() }.add(tag.get(i))
            if ((level == 0) && (tag.get(i) is ICloseElementTag)) {
                slotName = null // Reset slotName when back on root level
            }
        }
        return slots
    }



    private fun prepareModel(context: ITemplateContext, fragmentModel: TemplateModel, slots: HashMap<String?, ArrayList<ITemplateEvent>>): IModel {
        val modelFactory = context.modelFactory
        val newModel = modelFactory.createModel()

        // Copy all elements into a new Model
        for (i in 1 until fragmentModel.size()-1) {
            val fragmentPart = fragmentModel.get(i)
            if ((fragmentPart is IStandaloneElementTag) &&  ((fragmentPart).elementCompleteName == slotTagName)) {
                val slotName = fragmentPart.getAttributeValue(slotNameAttributeName)
                if (slots.containsKey(slotName)) {
                    for (j in 0 until slots[slotName]!!.size) {
                        newModel.add(slots[slotName]!![j])
                    }
                }
            }
            else {
                newModel.add(fragmentPart)
            }
        }
        return newModel
    }



    private fun extractAttrs(rootElement: IProcessableElementTag, context: ITemplateContext, expressionParser: IStandardExpressionParser): HashMap<String, Any?> {
        val attrs = HashMap<String, Any?>()
        rootElement.allAttributes.forEach {
            val plainAttributeName = it.attributeDefinition.attributeName.attributeName

            // Process and populate prefixed attribute variables
            if(it.attributeDefinition.attributeName.prefix == dialectPrefix) {
                // It seems not efficient to extract and reassign the values.
                // Why doesn't it work like here??:
                // https://github.com/thymeleaf/thymeleaf/blob/120a0e9cc5d768a7b21abb19b4f4122bdc019206/lib/thymeleaf/src/main/java/org/thymeleaf/standard/processor/AbstractStandardFragmentInsertionTagProcessor.java#L264-L271
                val valueContent = expressionParser.parseExpression(context, it.value).execute(context)
                // structureHandler.setLocalVariable(plainAttributeName, valueContent)
                attrs[plainAttributeName] = valueContent
            }
            else {
                attrs[plainAttributeName] = it.value
            }
        }
        return attrs
    }



    private fun buildComponentPath(componentsPath: String?, componentClass: Class<out CompositionComponent>, elementName: String): String {
        val pathParts = mutableListOf<String>()
        if (componentsPath?.isNotEmpty() == true) { pathParts.add(componentsPath.trim('/')) }
        // get component path field of reflected Component class
        val componentPath: String = (componentClass.getField("path").get(componentClass) as String).trim('/')
        if (componentPath.isNotEmpty()) { pathParts.add(componentPath) }
        pathParts.add(elementName)
        return  pathParts.joinToString("/")
    }
}
