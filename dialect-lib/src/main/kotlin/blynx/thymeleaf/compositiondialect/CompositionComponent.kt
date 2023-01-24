package blynx.thymeleaf.compositiondialect

import org.thymeleaf.context.ITemplateContext
import org.thymeleaf.processor.element.IElementModelStructureHandler

data class CompositionComponentContext(
    val attributes: Map<String, Any?>, 
    val slotNames: MutableSet<String?>, 
    val context: ITemplateContext,
    val structureHandler: IElementModelStructureHandler
)

open class CompositionComponent(
    private val componentContext: CompositionComponentContext,
) {
    companion object {
        const val path: String = ""
    }

    fun hasSlot(slotName: String): Boolean {
        return this.componentContext.slotNames.contains(slotName)
    }
}
