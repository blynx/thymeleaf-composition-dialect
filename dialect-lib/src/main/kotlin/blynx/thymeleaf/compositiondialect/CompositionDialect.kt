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
    companion object{
        const val DIALECT_NAME = "Composition Dialect"
        private const val DIALECT_PREFIX = "c"
    }

    override fun getProcessors(dialectPrefix: String): HashSet<IProcessor> {

        val componentClasses = Reflections(componentPackage).getSubTypesOf(CompositionComponent::class.java)

        return componentClasses.map {
            // TODO: more fancy handling of name: camelcase to kebab case or snake case something like that, maybe even configurable
            val tagName = it.simpleName.lowercase()
            val processor = CompositionElementModelProcessor(DIALECT_PREFIX, tagName, it, componentsPath)
            processor
        }.toHashSet()
    }
}
