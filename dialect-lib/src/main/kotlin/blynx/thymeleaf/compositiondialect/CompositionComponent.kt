package blynx.thymeleaf.compositiondialect

import org.thymeleaf.context.ITemplateContext
import org.thymeleaf.processor.element.IElementModelStructureHandler

class CompositionComponentContext(
    val attributes: Map<String, Any?>,
    val slotNames: Set<String>,
    val context: ITemplateContext,
    val structureHandler: IElementModelStructureHandler
)

open class CompositionComponent(
    private val componentContext: CompositionComponentContext,
) {
    companion object {
        const val DEFAULT_SLOT: String = ""
        const val path: String = ""
    }

    @JvmOverloads
    fun hasSlot(slotName: String = DEFAULT_SLOT): Boolean {
        return this.componentContext.slotNames.contains(slotName)
    }
}
