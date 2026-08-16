package blynx.thymeleaf.compositiondialect;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.thymeleaf.dialect.AbstractProcessorDialect;
import org.thymeleaf.processor.IProcessor;
import org.thymeleaf.standard.StandardDialect;
import org.thymeleaf.templatemode.TemplateMode;

/**
 * Thymeleaf dialect that registers one element processor per {@link CompositionComponent} subclass found
 * in its {@link ComponentSource}s, plus the {@code c:rest} attribute processor.
 *
 * <p>One dialect covers every source — the application's own components and each imported component
 * library's — rather than one dialect per library. Sharing the single prefix is what makes the dialect's
 * own grammar ({@code c:slot}, {@code c:name}, {@code c:rest}) canonical: a library's shipped templates
 * spell their slot markers the same way every other template does, so nothing about the prefix is baked
 * into the HTML a library publishes. What keeps two modules' components apart is the tag name, which a
 * library claims by naming its classes for itself — {@code DsButton} giving {@code <c:ds-button>}.
 *
 * <p>Component discovery lives in {@link ComponentRegistry}, built once at construction; this dialect is a
 * consumer of it. The registry is queryable via {@link #getRegistry()}.
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
        this(List.of(new ComponentSource(componentPackage, componentsPath)), name, prefix, processorPrecedence);
    }

    /** Every given source, under the default name and prefix. */
    public CompositionDialect(ComponentSource... sources) {
        this(List.of(sources));
    }

    /** Every given source, under the default name and prefix. */
    public CompositionDialect(List<ComponentSource> sources) {
        this(sources, DIALECT_NAME, DIALECT_PREFIX, StandardDialect.PROCESSOR_PRECEDENCE);
    }

    public CompositionDialect(List<ComponentSource> sources, String name, String prefix) {
        this(sources, name, prefix, StandardDialect.PROCESSOR_PRECEDENCE);
    }

    public CompositionDialect(List<ComponentSource> sources, String name, String prefix, int processorPrecedence) {
        super(name, prefix, processorPrecedence);
        this.registry = ComponentRegistry.scan(sources, prefix);
        // Both run over the merged registry: a tag claimed by two sources is invisible to either alone.
        this.registry.requireNoCollisions();
        this.registry.requireNoReservedTagNames();
    }

    /** The components this dialect discovered at startup, across every source. */
    public ComponentRegistry getRegistry() {
        return registry;
    }

    @Override
    public Set<IProcessor> getProcessors(String dialectPrefix) {
        Set<IProcessor> processors = new HashSet<>();
        for (ComponentDescriptor descriptor : registry.components()) {
            processors.add(new CompositionElementModelProcessor(dialectPrefix, descriptor));
        }
        // The grammar, registered once no matter how many sources contributed components. Two dialects
        // sharing a prefix would register these twice over, and a doubled c:_caller would unwind two
        // frames per hand-off instead of one.
        processors.add(new CompositionRestAttributesTagProcessor(dialectPrefix));
        processors.add(new CompositionCallerProcessor(dialectPrefix));
        processors.add(new CompositionSlotPlacementProcessor(TemplateMode.HTML, dialectPrefix));
        processors.add(new CompositionSlotPlacementProcessor(TemplateMode.XML, dialectPrefix));
        processors.add(new CompositionUnresolvedTagProcessor(TemplateMode.HTML, dialectPrefix));
        processors.add(new CompositionUnresolvedTagProcessor(TemplateMode.XML, dialectPrefix));
        return processors;
    }
}
