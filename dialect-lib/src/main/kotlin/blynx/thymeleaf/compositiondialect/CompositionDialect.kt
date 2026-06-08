package blynx.thymeleaf.compositiondialect

import org.reflections.Reflections
import org.thymeleaf.dialect.AbstractProcessorDialect
import org.thymeleaf.processor.IProcessor
import org.thymeleaf.standard.StandardDialect

class CompositionDialect @JvmOverloads constructor(
    private val componentPackage: String,
    private val componentsPath: String? = null,
    name: String = DIALECT_NAME, prefix: String = DIALECT_PREFIX, processorPrecedence: Int = StandardDialect.PROCESSOR_PRECEDENCE
) : AbstractProcessorDialect(
    name, prefix, processorPrecedence
) {
    companion object {
        const val DIALECT_NAME = "Composition Dialect"
        private const val DIALECT_PREFIX = "c"
    }

    override fun getProcessors(dialectPrefix: String): HashSet<IProcessor> {
        val componentClasses = Reflections(componentPackage).getSubTypesOf(CompositionComponent::class.java)

        val processors = componentClasses.mapTo(HashSet<IProcessor>()) { componentClass ->
            val tagName = componentClass.simpleName.replace(Regex("(?!^)(?=[A-Z][a-z])"), "-").lowercase()
            CompositionElementModelProcessor(dialectPrefix, tagName, componentClass, componentsPath)
        }
        processors.add(CompositionRestAttributesTagProcessor(dialectPrefix))
        return processors
    }
}
