package blynx.thymeleaf.compositiondialect;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.thymeleaf.dialect.AbstractProcessorDialect;
import org.thymeleaf.processor.IProcessor;
import org.thymeleaf.standard.StandardDialect;
import org.thymeleaf.templatemode.TemplateMode;

/**
 * Thymeleaf dialect that registers one wildcard element-model processor claiming every tag under its
 * prefix, resolved against every component template found under its {@link ComponentSource}s, plus the
 * {@code c:rest} attribute processor.
 *
 * <p>One dialect covers every source — the application's own components and each imported component
 * library's — rather than one dialect per library. Sharing the single prefix is what makes the dialect's
 * own grammar ({@code c:slot}, {@code c:rest}, {@code c:component}) canonical: a library's shipped templates
 * spell their slot markers the same way every other template does, so nothing about the prefix is baked
 * into the HTML a library publishes. What keeps two modules' components apart is the tag name, which is
 * always the template's own file basename — a library claims its own tags by naming its files for itself,
 * e.g. {@code ds-button.html} giving {@code <c:ds-button>}.
 *
 * <p>Component discovery lives in {@link ComponentRegistry}, built once at construction; this dialect is a
 * consumer of it. The registry is queryable via {@link #getRegistry()}.
 */
public class CompositionDialect extends AbstractProcessorDialect {

    public static final String DIALECT_NAME = "Composition Dialect";
    private static final String DIALECT_PREFIX = "c";

    private final ComponentRegistry registry;

    public CompositionDialect(String templatesPath) {
        this(templatesPath, null);
    }

    public CompositionDialect(String templatesPath, String defaultPackage) {
        this(templatesPath, defaultPackage, DIALECT_NAME);
    }

    public CompositionDialect(String templatesPath, String defaultPackage, String name) {
        this(templatesPath, defaultPackage, name, DIALECT_PREFIX);
    }

    public CompositionDialect(String templatesPath, String defaultPackage, String name, String prefix) {
        this(templatesPath, defaultPackage, name, prefix, StandardDialect.PROCESSOR_PRECEDENCE);
    }

    public CompositionDialect(String templatesPath, String defaultPackage, String name, String prefix,
                              int processorPrecedence) {
        this(List.of(new ComponentSource(templatesPath, defaultPackage)), name, prefix, processorPrecedence);
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
        // All three run over the merged registry: a tag claimed by two sources is invisible to either
        // alone, and a source contributing nothing looks identical to an empty one from outside itself.
        this.registry.requireNoCollisions();
        this.registry.requireNoReservedTagNames();
        this.registry.requireNoEmptySources();
    }

    /** The components this dialect discovered at startup, across every source. */
    public ComponentRegistry getRegistry() {
        return registry;
    }

    @Override
    public Set<IProcessor> getProcessors(String dialectPrefix) {
        Set<IProcessor> processors = new HashSet<>();
        // One wildcard processor claims every tag under the prefix and looks it up in the registry,
        // replacing what used to be one registered processor per component.
        processors.add(new CompositionElementModelProcessor(dialectPrefix, registry));
        // The grammar, registered once no matter how many sources contributed components. Two dialects
        // sharing a prefix would register these twice over, and a doubled c:_caller would unwind two
        // frames per hand-off instead of one.
        processors.add(new CompositionRestAttributesTagProcessor(dialectPrefix));
        processors.add(new CompositionCallerProcessor(dialectPrefix));
        processors.add(new CompositionSlotPlacementProcessor(TemplateMode.HTML, dialectPrefix));
        processors.add(new CompositionSlotPlacementProcessor(TemplateMode.XML, dialectPrefix));
        // Components are HTML-only, so the wildcard processor above is HTML-only too and already reports
        // an unresolved HTML tag itself. Kept for XML mode alone, so a <c:card> in an XML template still
        // fails loudly instead of passing through as literal markup.
        processors.add(new CompositionUnresolvedTagProcessor(TemplateMode.XML, dialectPrefix));
        return processors;
    }
}
