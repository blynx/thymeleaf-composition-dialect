package blynx.thymeleaf.compositiondialect;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.reflections.Reflections;

/**
 * The single source of truth for component discovery, naming, and template-path rules.
 *
 * <p>Built from a classpath scan of one or more {@link ComponentSource}s (see {@link #scan}), it holds an
 * immutable, deterministically ordered list of {@link ComponentDescriptor}s plus indexes for the "what
 * components exist?" queries that Module Organization, the Component Playground, and Developer Tooling
 * need. {@link CompositionDialect} is a consumer of the registry rather than the place discovery lives.
 */
public final class ComponentRegistry {

    /**
     * Tag names this dialect's own grammar already claims, which a component may therefore not take.
     * A class named {@code Slot} would otherwise register a processor for {@code c:slot} alongside
     * {@link CompositionSlotPlacementProcessor}'s, and — that one running first, at a lower precedence —
     * every use of it would fail with the placement processor's "not recognized as a slot marker" error
     * instead of rendering.
     *
     * <p>{@code rest} is absent deliberately: {@code c:rest} is an attribute, and attribute and element
     * names are matched separately, so a {@code <c:rest>} component collides with nothing.
     * {@link CompositionCallerProcessor#TAG_NAME} is absent because {@link #toTagName} cannot produce it.
     */
    private static final Set<String> RESERVED_TAG_NAMES = Set.of("slot");

    private final List<ComponentDescriptor> components;
    private final Map<String, List<ComponentDescriptor>> byPrefix;
    private final Map<String, List<ComponentDescriptor>> byTagName;
    private final Map<String, List<ComponentDescriptor>> bySourcePackage;

    private ComponentRegistry(List<ComponentDescriptor> descriptors) {
        List<ComponentDescriptor> sorted = new ArrayList<>(descriptors);
        sorted.sort(Comparator.comparing(ComponentDescriptor::prefix).thenComparing(ComponentDescriptor::tagName));
        this.components = List.copyOf(sorted);
        this.byPrefix = groupBy(this.components, ComponentDescriptor::prefix);
        this.byTagName = groupBy(this.components, ComponentDescriptor::tagName);
        this.bySourcePackage = groupBy(this.components, descriptor -> descriptor.source().componentPackage());
    }

    /**
     * Discovers every {@link CompositionComponent} subtype in {@code source}'s package and builds a
     * registry, deriving each descriptor's tag name and template path via {@link #toTagName} and
     * {@link #buildComponentPath}.
     */
    public static ComponentRegistry scan(ComponentSource source, String prefix) {
        List<ComponentDescriptor> descriptors = new ArrayList<>();
        for (Class<? extends CompositionComponent> componentClass :
                new Reflections(source.componentPackage()).getSubTypesOf(CompositionComponent.class)) {
            // getSubTypesOf is transitive and unfiltered: an abstract intermediate base would otherwise
            // register like a real component and only fail — as a raw InstantiationError — if rendered.
            if (Modifier.isAbstract(componentClass.getModifiers())) {
                continue;
            }
            String tagName = toTagName(componentClass);
            String templatePath = buildComponentPath(source.componentsPath(), componentClass, tagName);
            Set<String> props = readDeclaredProps(componentClass);
            descriptors.add(new ComponentDescriptor(componentClass, source, prefix, tagName, templatePath, props));
        }
        return new ComponentRegistry(descriptors);
    }

    /** Scans every source and merges the results into the one registry a dialect is built from. */
    public static ComponentRegistry scan(Collection<ComponentSource> sources, String prefix) {
        return aggregate(sources.stream().map(source -> scan(source, prefix)).toList());
    }

    /** Convenience for the single-source case, where the path is the dialect's own {@code componentsPath}. */
    public static ComponentRegistry scan(String componentPackage, String componentsPath, String prefix) {
        return scan(new ComponentSource(componentPackage, componentsPath), prefix);
    }

    /**
     * Combines several registries into one unified view — how an application's own components and each
     * imported component library's end up in a single dialect. Merging alone reports nothing: a tag
     * claimed twice simply shows up as a multi-entry list in {@link #byTagName()}, and rejecting that is
     * {@link #requireNoCollisions()}'s job, run once over the merged result.
     */
    public static ComponentRegistry aggregate(Collection<ComponentRegistry> registries) {
        List<ComponentDescriptor> all = new ArrayList<>();
        for (ComponentRegistry registry : registries) {
            all.addAll(registry.components);
        }
        return new ComponentRegistry(all);
    }

    /** All known components, deterministically ordered by {@code (prefix, tagName)}. Immutable. */
    public List<ComponentDescriptor> components() {
        return components;
    }

    /**
     * Components grouped by dialect prefix. Immutable. Every component of every source shares the one
     * prefix, so this is a single entry unless registries built under different prefixes were aggregated
     * by hand; {@link #bySourcePackage()} is what separates one module's components from another's.
     */
    public Map<String, List<ComponentDescriptor>> byPrefix() {
        return byPrefix;
    }

    /**
     * Components grouped by tag name. Immutable. A key mapping to more than one descriptor is a
     * collision, whether the claimants came from one source or several.
     */
    public Map<String, List<ComponentDescriptor>> byTagName() {
        return byTagName;
    }

    /**
     * Components grouped by the package of the {@link ComponentSource} they were scanned from. Immutable.
     * One entry per module — the application's own components, and each imported library's.
     */
    public Map<String, List<ComponentDescriptor>> bySourcePackage() {
        return bySourcePackage;
    }

    /**
     * Tags claimed by more than one component class, keyed by {@link ComponentDescriptor#qualifiedName()}.
     * Immutable; empty when there are none.
     */
    public Map<String, List<ComponentDescriptor>> collisions() {
        Map<String, List<ComponentDescriptor>> byQualifiedName = groupBy(components, ComponentDescriptor::qualifiedName);
        Map<String, List<ComponentDescriptor>> collisions = new LinkedHashMap<>();
        byQualifiedName.forEach((qualifiedName, descriptors) -> {
            if (descriptors.size() > 1) {
                collisions.put(qualifiedName, descriptors);
            }
        });
        return Collections.unmodifiableMap(collisions);
    }

    /**
     * Fails fast if any tag is claimed by more than one component class. Without this, one silently wins
     * a {@code HashSet} of processors built from the registry and the other becomes permanently
     * unreachable, with no error at all.
     *
     * <p>Run over the merged registry rather than per source: a component library and the application
     * importing it both owning a {@code Button} is exactly the clash worth catching, and neither source
     * can see it alone.
     */
    public void requireNoCollisions() {
        Map<String, List<ComponentDescriptor>> collisions = collisions();
        if (collisions.isEmpty()) {
            return;
        }
        StringBuilder message = new StringBuilder(CompositionDialect.DIALECT_NAME
                + ": duplicate component tag(s) detected — each must be unique:\n");
        collisions.forEach((qualifiedName, descriptors) -> {
            message.append("  <").append(qualifiedName).append("> is claimed by ")
                    .append(descriptors.stream().map(ComponentRegistry::describeClaimant)
                            .collect(Collectors.joining(", ")))
                    .append("\n");
            message.append("    ").append(remedyFor(descriptors)).append("\n");
        });
        throw new IllegalStateException(message.toString());
    }

    /**
     * Fails fast if a component claims a tag this dialect's own grammar already uses. Such a component
     * registers successfully and then fails on every single use, with an error about slot markers that
     * says nothing about the real cause.
     */
    public void requireNoReservedTagNames() {
        List<ComponentDescriptor> reserved = components.stream()
                .filter(descriptor -> RESERVED_TAG_NAMES.contains(descriptor.tagName()))
                .toList();
        if (reserved.isEmpty()) {
            return;
        }
        StringBuilder message = new StringBuilder(CompositionDialect.DIALECT_NAME
                + ": component tag(s) reserved by the dialect itself:\n");
        for (ComponentDescriptor descriptor : reserved) {
            message.append("  <").append(descriptor.qualifiedName()).append("> is claimed by ")
                    .append(describeClaimant(descriptor)).append("\n");
        }
        message.append("Rename the component(s); ")
                .append(RESERVED_TAG_NAMES.stream().sorted().collect(Collectors.joining(", ")))
                .append(" belong to the dialect's own grammar.");
        throw new IllegalStateException(message.toString());
    }

    /** The descriptor for a component class, if it is registered. */
    public Optional<ComponentDescriptor> findByClass(Class<? extends CompositionComponent> componentClass) {
        return components.stream().filter(descriptor -> descriptor.componentClass().equals(componentClass)).findFirst();
    }

    /** The descriptor for a fully-qualified {@code prefix:tagName}, if it is registered. */
    public Optional<ComponentDescriptor> findByTagName(String prefix, String tagName) {
        return byTagName.getOrDefault(tagName, List.of()).stream()
                .filter(descriptor -> descriptor.prefix().equals(prefix))
                .findFirst();
    }

    /** PascalCase simple class name to kebab-case tag name, e.g. {@code MagicHeadings -> magic-headings}. */
    public static String toTagName(Class<?> componentClass) {
        return kebabCase(componentClass.getSimpleName());
    }

    private static String describeClaimant(ComponentDescriptor descriptor) {
        return descriptor.componentClass().getName();
    }

    /**
     * What to do about a collision, which depends on who owns the clashing components. Two classes in one
     * source are one author's own problem; two sources clashing usually is not, because the tag names a
     * component library ships are fixed by whoever wrote it.
     */
    private static String remedyFor(List<ComponentDescriptor> descriptors) {
        Set<String> sources = descriptors.stream()
                .map(descriptor -> descriptor.source().componentPackage())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (sources.size() == 1) {
            return "Rename one of them.";
        }
        return "These come from different component sources (" + String.join(", ", sources)
                + "), so rename whichever one you own — an imported library's tag names are its author's "
                + "to change. A library keeps its components apart by naming its classes for itself, "
                + "e.g. DsButton for <" + descriptors.getFirst().prefix() + ":ds-button>.";
    }

    /** camelCase or PascalCase to kebab-case, e.g. {@code autoHideSeconds -> auto-hide-seconds}. */
    private static String kebabCase(String name) {
        return name.replaceAll("(?!^)(?=[A-Z][a-z])", "-").toLowerCase(Locale.ROOT);
    }

    /**
     * The template path a component's fragment is loaded from: the optional {@code componentsPath}
     * root, the component's {@code pathPrefix}, its own {@code path} sub-path, and the tag name — joined
     * with {@code /}. Both come from {@link Composition} — on the class itself for {@code path}, on the
     * class then its package for {@code pathPrefix} — and default to {@code ""} (no extra path segment)
     * when {@code @Composition} is absent or leaves that member unset.
     */
    public static String buildComponentPath(String componentsPath, Class<? extends CompositionComponent> componentClass,
                                            String tagName) {
        List<String> pathParts = new ArrayList<>();
        if (componentsPath != null && !componentsPath.isEmpty()) {
            pathParts.add(trimSlashes(componentsPath));
        }
        String pathPrefix = resolvePathPrefix(componentClass);
        if (!pathPrefix.isEmpty()) {
            pathParts.add(pathPrefix);
        }
        String componentPath = resolvePath(componentClass);
        if (!componentPath.isEmpty()) {
            pathParts.add(componentPath);
        }
        pathParts.add(tagName);
        return String.join("/", pathParts);
    }

    private static String resolvePath(Class<? extends CompositionComponent> componentClass) {
        Composition onType = componentClass.getAnnotation(Composition.class);
        return onType != null ? trimSlashes(onType.path()) : "";
    }

    private static String resolvePathPrefix(Class<? extends CompositionComponent> componentClass) {
        Composition onType = componentClass.getAnnotation(Composition.class);
        if (onType != null && !onType.pathPrefix().isEmpty()) {
            return trimSlashes(onType.pathPrefix());
        }
        Package componentPackage = componentClass.getPackage();
        Composition onPackage = componentPackage == null ? null : componentPackage.getAnnotation(Composition.class);
        return onPackage != null ? trimSlashes(onPackage.pathPrefix()) : "";
    }

    /**
     * The component's declared props — resolved once here so it is introspectable without rendering.
     * A record's props are its record components (other than the one holding its
     * {@link CompositionComponentContext}), one-to-one, needing no declaration at all. A class declares
     * props by annotating fields with {@link Prop}; a class with no {@code @Prop} fields has no props.
     */
    private static Set<String> readDeclaredProps(Class<? extends CompositionComponent> componentClass) {
        return componentClass.isRecord() ? recordProps(componentClass) : annotatedProps(componentClass);
    }

    private static Set<String> recordProps(Class<? extends CompositionComponent> componentClass) {
        Set<String> props = new LinkedHashSet<>();
        for (RecordComponent component : componentClass.getRecordComponents()) {
            if (CompositionComponentContext.class.isAssignableFrom(component.getType())) {
                continue;
            }
            props.add(propName(componentClass, component.getName()));
        }
        return Set.copyOf(props);
    }

    private static Set<String> annotatedProps(Class<? extends CompositionComponent> componentClass) {
        Set<String> props = new LinkedHashSet<>();
        for (Field field : componentClass.getDeclaredFields()) {
            if (field.isAnnotationPresent(Prop.class)) {
                props.add(propName(componentClass, field.getName()));
            }
        }
        return Set.copyOf(props);
    }

    /**
     * The attribute name a field or record component of the given Java name binds from: {@link Prop#value()}
     * when its backing field carries one, otherwise the name itself kebab-cased. A record component's
     * {@code @Prop} lands on its backing field (not the {@link RecordComponent} itself, which {@code @Prop}'s
     * {@code FIELD} target does not reach), so both cases read the same declared field.
     *
     * <p>Package-private rather than {@code private}: {@link CompositionElementModelProcessor}'s record
     * props binder needs the exact same derivation, in record-component order, to match each canonical
     * constructor parameter to the attribute it binds from.
     */
    static String propName(Class<?> componentClass, String javaName) {
        Field field;
        try {
            field = componentClass.getDeclaredField(javaName);
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException(CompositionDialect.DIALECT_NAME
                    + ": Could not read the \"" + javaName + "\" field of component " + componentClass.getName(), e);
        }
        Prop prop = field.getAnnotation(Prop.class);
        return prop != null && !prop.value().isEmpty() ? prop.value() : kebabCase(javaName);
    }

    private static String trimSlashes(String value) {
        return value.replaceAll("^/+|/+$", "");
    }

    private static Map<String, List<ComponentDescriptor>> groupBy(
            List<ComponentDescriptor> descriptors, Function<ComponentDescriptor, String> key) {
        // LinkedHashMap over the already-sorted descriptor list keeps keys in (prefix, tagName) order;
        // Map.copyOf would drop that ordering, so wrap an unmodifiable view instead.
        Map<String, List<ComponentDescriptor>> grouped = new LinkedHashMap<>();
        for (ComponentDescriptor descriptor : descriptors) {
            grouped.computeIfAbsent(key.apply(descriptor), k -> new ArrayList<>()).add(descriptor);
        }
        grouped.replaceAll((k, v) -> List.copyOf(v));
        return Collections.unmodifiableMap(grouped);
    }
}
