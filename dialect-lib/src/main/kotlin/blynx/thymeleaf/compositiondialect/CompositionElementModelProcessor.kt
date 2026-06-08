package blynx.thymeleaf.compositiondialect

import org.thymeleaf.context.ITemplateContext
import org.thymeleaf.engine.TemplateModel
import org.thymeleaf.exceptions.TemplateProcessingException
import org.thymeleaf.model.IModel
import org.thymeleaf.model.IModelFactory
import org.thymeleaf.model.ITemplateEvent
import org.thymeleaf.model.IProcessableElementTag
import org.thymeleaf.model.IStandaloneElementTag
import org.thymeleaf.model.IOpenElementTag
import org.thymeleaf.model.ICloseElementTag
import org.thymeleaf.processor.element.AbstractElementModelProcessor
import org.thymeleaf.processor.element.IElementModelStructureHandler
import org.thymeleaf.standard.expression.StandardExpressions
import org.thymeleaf.standard.processor.StandardReplaceTagProcessor
import org.thymeleaf.templatemode.TemplateMode
import java.lang.reflect.Constructor


class CompositionElementModelProcessor(
    dialectPrefix: String, private val elementName: String, private val componentClass: Class<out CompositionComponent>, componentsPath: String?
) : AbstractElementModelProcessor(
    TemplateMode.HTML, dialectPrefix, elementName, true, null, false, PRECEDENCE
) {
    private val slotTagName = "$dialectPrefix:slot"
    private val slotNameAttributeName = "$dialectPrefix:name"
    private val componentPath = buildComponentPath(componentsPath, componentClass, elementName)
    private val componentConstructor: Constructor<out CompositionComponent> =
        componentClass.getConstructor(CompositionComponentContext::class.java)

    @Volatile private var cachedFragment: FragmentInfo? = null

    private class FragmentInfo(
        val segments: Array<IModel>,           // events between slot markers; segments.size == slotMarkerNames.size + 1
        val slotMarkerNames: Array<String>,
    )

    companion object {
        private const val PRECEDENCE: Int = StandardReplaceTagProcessor.PRECEDENCE
    }

    override fun doProcess(context: ITemplateContext, tag: IModel, structureHandler: IElementModelStructureHandler) {
        val fragment = getOrLoadFragment(context)
        val slots = extractSlots(tag, context.modelFactory)
        val attrs = extractAttrs(tag.get(0) as IProcessableElementTag, context)

        val componentContext = CompositionComponentContext(
            TrackingAttributes(attrs),
            slots.keys,
            context.locale,
            { code, params -> context.getMessage(CompositionElementModelProcessor::class.java, code, params, false) },
            { name -> context.getVariable(name) },
            { name, value -> structureHandler.setLocalVariable(name, value) }
        )
        val componentInstance = try {
            componentConstructor.newInstance(componentContext)
        } catch (e: Exception) {
            throw TemplateProcessingException("${CompositionDialect.DIALECT_NAME}: Could not instantiate component \"$elementName\" (${componentClass.name})", e)
        }
        structureHandler.setLocalVariable("this", componentInstance)

        tag.reset()
        renderFragmentInto(tag, fragment, slots)
    }

    private fun getOrLoadFragment(context: ITemplateContext): FragmentInfo {
        val cached = cachedFragment
        if (cached != null) return cached

        val templateModel = try {
            context.configuration.templateManager.parseStandalone(context, componentPath, null, null, true, true)
        } catch (e: Exception) {
            throw TemplateProcessingException("${CompositionDialect.DIALECT_NAME}: Could not load template for component \"$elementName\" from \"$componentPath.html\" (relative to thymeleaf templates path)")
        }

        // Pre-split the fragment into Model segments separated by slot markers.
        // At render time each segment is bulk-copied via addModel (System.arraycopy)
        // instead of inserted event-by-event.
        val modelFactory = context.modelFactory
        val segments = ArrayList<IModel>()
        val names = ArrayList<String>()

        var currentSegment = modelFactory.createModel()
        for (i in 1 until templateModel.size() - 1) {
            val event = templateModel.get(i)
            if (event is IStandaloneElementTag && event.elementCompleteName == slotTagName) {
                segments.add(currentSegment)
                names.add(event.getAttributeValue(slotNameAttributeName) ?: CompositionComponent.DEFAULT_SLOT)
                currentSegment = modelFactory.createModel()
            } else {
                currentSegment.add(event)
            }
        }
        segments.add(currentSegment)

        val info = FragmentInfo(segments.toTypedArray(), names.toTypedArray())
        cachedFragment = info
        return info
    }

    private fun renderFragmentInto(target: IModel, fragment: FragmentInfo, slots: Map<String, List<ITemplateEvent>>) {
        val segments = fragment.segments
        val slotNames = fragment.slotMarkerNames

        target.addModel(segments[0])
        for (i in slotNames.indices) {
            val content = slots[slotNames[i]]
            if (content != null) {
                for (event in content) target.add(event)
            }
            target.addModel(segments[i + 1])
        }
    }

    private fun extractSlots(tag: IModel, modelFactory: IModelFactory): HashMap<String, ArrayList<ITemplateEvent>> {
        val slots = HashMap<String, ArrayList<ITemplateEvent>>(4)
        var slotName: String = CompositionComponent.DEFAULT_SLOT
        var level = 0
        for (i in 1 until tag.size() - 1) {
            var event: ITemplateEvent = tag.get(i)
            if (event is IOpenElementTag) {
                level++
            } else if (event is ICloseElementTag) {
                level--
            }
            if (event is IProcessableElementTag && event.hasAttribute(slotTagName)) {
                if (level == 1) {
                    slotName = event.getAttributeValue(slotTagName) ?: CompositionComponent.DEFAULT_SLOT
                }
                event = modelFactory.removeAttribute(event, slotTagName)
            }
            slots.getOrPut(slotName) { ArrayList() }.add(event)
            if ((level == 0) && (event is ICloseElementTag)) {
                slotName = CompositionComponent.DEFAULT_SLOT
            }
        }
        return slots
    }

    private fun extractAttrs(rootElement: IProcessableElementTag, context: ITemplateContext): HashMap<String, Any?> {
        val allAttributes = rootElement.allAttributes
        val attrs = HashMap<String, Any?>(allAttributes.size)
        val expressionParser = StandardExpressions.getExpressionParser(context.configuration)
        for (attr in allAttributes) {
            val plainAttributeName = attr.attributeDefinition.attributeName.attributeName
            if (attr.attributeDefinition.attributeName.prefix == dialectPrefix) {
                attrs[plainAttributeName] = expressionParser.parseExpression(context, attr.value).execute(context)
            } else {
                attrs[plainAttributeName] = attr.value
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
