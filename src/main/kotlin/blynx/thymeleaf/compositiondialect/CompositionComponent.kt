package blynx.thymeleaf.compositiondialect

import java.util.Locale

class TrackingAttributes(private val raw: Map<String, Any?>) {
    private val accessed = mutableSetOf<String>()

    operator fun get(name: String): Any? {
        accessed.add(name)
        return raw[name]
    }

    fun containsKey(name: String): Boolean {
        accessed.add(name)
        return raw.containsKey(name)
    }

    fun rest(): Map<String, Any?> = raw.filterKeys { it !in accessed }
}

class CompositionComponentContext(
    val attributes: TrackingAttributes,
    val slotNames: Set<String>,
    val locale: Locale,
    private val messageResolver: (String, Array<out Any?>) -> String?,
    private val variableReader: (String) -> Any?,
    private val variableWriter: (String, Any?) -> Unit,
) {
    fun message(code: String, vararg params: Any?): String = messageResolver(code, params) ?: code
    fun variable(name: String): Any? = variableReader(name)
    fun setVariable(name: String, value: Any?) = variableWriter(name, value)
}

open class CompositionComponent(
    private val componentContext: CompositionComponentContext,
) {
    companion object {
        const val DEFAULT_SLOT: String = ""
        const val path: String = ""
    }

    val restAttributes: Map<String, Any?>
        get() = componentContext.attributes.rest()

    @JvmOverloads
    fun hasSlot(slotName: String = DEFAULT_SLOT): Boolean = componentContext.slotNames.contains(slotName)
}
