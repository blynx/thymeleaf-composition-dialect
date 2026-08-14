package blynx.thymeleaf.compositiondialect;

import java.util.HashSet;
import java.util.Set;

import org.thymeleaf.dialect.AbstractProcessorDialect;
import org.thymeleaf.processor.IProcessor;
import org.thymeleaf.standard.StandardDialect;
import org.thymeleaf.templatemode.TemplateMode;

/**
 * Thymeleaf dialect that registers one element processor per {@link CompositionComponent}
 * subclass found in {@code componentPackage}, plus the {@code c:rest} attribute processor.
 *
 * <p>Component discovery lives in {@link ComponentRegistry}, built once at construction; this dialect
 * is a consumer of it. The registry is queryable via {@link #getRegistry()}.
 */
public class CompositionDialect extends AbstractProcessorDialect {

    public static final String DIALECT_NAME = "Composition Dialect";
    private static final String DIALECT_PREFIX = "c";

    private final ComponentRegistry registry;

    public CompositionDialect(String componentPackage) {
        this(componentPackage, null);
    }

    public CompositionDialect(String componentPackage, String componentsPath) {
        this(componentPackage, componentsPath, DIALECT_NAME);
    }

    public CompositionDialect(String componentPackage, String componentsPath, String name) {
        this(componentPackage, componentsPath, name, DIALECT_PREFIX);
    }

    public CompositionDialect(String componentPackage, String componentsPath, String name, String prefix) {
        this(componentPackage, componentsPath, name, prefix, StandardDialect.PROCESSOR_PRECEDENCE);
    }

    public CompositionDialect(String componentPackage, String componentsPath, String name, String prefix,
                              int processorPrecedence) {
        super(name, prefix, processorPrecedence);
        this.registry = ComponentRegistry.scan(componentPackage, componentsPath, prefix);
    }

    /** The components this dialect discovered at startup. */
    public ComponentRegistry getRegistry() {
        return registry;
    }

    @Override
    public Set<IProcessor> getProcessors(String dialectPrefix) {
        Set<IProcessor> processors = new HashSet<>();
        for (ComponentDescriptor descriptor : registry.components()) {
            processors.add(new CompositionElementModelProcessor(dialectPrefix, descriptor));
        }
        processors.add(new CompositionRestAttributesTagProcessor(dialectPrefix));
        processors.add(new CompositionCallerProcessor(dialectPrefix));
        processors.add(new CompositionSlotPlacementProcessor(TemplateMode.HTML, dialectPrefix));
        processors.add(new CompositionSlotPlacementProcessor(TemplateMode.XML, dialectPrefix));
        return processors;
    }
}
