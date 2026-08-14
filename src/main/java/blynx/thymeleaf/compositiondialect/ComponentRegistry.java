package blynx.thymeleaf.compositiondialect;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.reflections.Reflections;

/**
 * The single source of truth for component discovery, naming, and template-path rules.
 *
 * <p>Built once from a classpath scan (see {@link #scan}), it holds an immutable, deterministically
 * ordered list of {@link ComponentDescriptor}s plus indexes for the "what components exist?" queries
 * that Module Organization, the Component Playground, and Developer Tooling need. {@link CompositionDialect}
 * is a consumer of the registry rather than the place discovery lives.
 */
public final class ComponentRegistry {

    private final List<ComponentDescriptor> components;
    private final Map<String, List<ComponentDescriptor>> byPrefix;
    private final Map<String, List<ComponentDescriptor>> byTagName;

    private ComponentRegistry(List<ComponentDescriptor> descriptors) {
        List<ComponentDescriptor> sorted = new ArrayList<>(descriptors);
        sorted.sort(Comparator.comparing(ComponentDescriptor::prefix).thenComparing(ComponentDescriptor::tagName));
        this.components = List.copyOf(sorted);
        this.byPrefix = groupBy(this.components, ComponentDescriptor::prefix);
        this.byTagName = groupBy(this.components, ComponentDescriptor::tagName);
    }

    /**
     * Discovers every {@link CompositionComponent} subtype in {@code componentPackage} and builds a
     * registry, deriving each descriptor's tag name and template path via {@link #toTagName} and
     * {@link #buildComponentPath}.
     */
    public static ComponentRegistry scan(String componentPackage, String componentsPath, String prefix) {
        List<ComponentDescriptor> descriptors = new ArrayList<>();
        for (Class<? extends CompositionComponent> componentClass :
                new Reflections(componentPackage).getSubTypesOf(CompositionComponent.class)) {
            // getSubTypesOf is transitive and unfiltered: an abstract intermediate base would otherwise
            // register like a real component and only fail — as a raw InstantiationError — if rendered.
            if (Modifier.isAbstract(componentClass.getModifiers())) {
                continue;
            }
            String tagName = toTagName(componentClass);
            String templatePath = buildComponentPath(componentsPath, componentClass, tagName);
            descriptors.add(new ComponentDescriptor(componentClass, prefix, tagName, templatePath));
        }
        return new ComponentRegistry(descriptors);
    }

    /**
     * Combines several registries into one unified view — the substrate for the cross-dialect,
     * one-prefix-per-module direction. A tag name that appears under more than one prefix simply
     * shows up as a multi-entry list in {@link #byTagName()}; detecting and rejecting collisions is
     * Module Organization's concern, not this method's.
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

    /** Components grouped by dialect prefix. Immutable. */
    public Map<String, List<ComponentDescriptor>> byPrefix() {
        return byPrefix;
    }

    /**
     * Components grouped by tag name. Immutable. A key mapping to more than one descriptor is a
     * cross-prefix tag collision.
     */
    public Map<String, List<ComponentDescriptor>> byTagName() {
        return byTagName;
    }

    /**
     * Tags claimed by more than one component class, keyed by {@link ComponentDescriptor#qualifiedName()}
     * rather than raw tag name — a tag reused across different prefixes is the intentional cross-dialect
     * substrate {@link #aggregate} supports, not a collision. Immutable; empty when there are none.
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
     */
    public void requireNoCollisions() {
        Map<String, List<ComponentDescriptor>> collisions = collisions();
        if (collisions.isEmpty()) {
            return;
        }
        StringBuilder message = new StringBuilder(CompositionDialect.DIALECT_NAME
                + ": duplicate component tag(s) detected — each must be unique:\n");
        collisions.forEach((qualifiedName, descriptors) -> message.append("  <").append(qualifiedName)
                .append("> is claimed by ")
                .append(descriptors.stream().map(d -> d.componentClass().getName()).collect(Collectors.joining(", ")))
                .append("\n"));
        message.append("Rename the clashing component(s).");
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
        return componentClass.getSimpleName()
                .replaceAll("(?!^)(?=[A-Z][a-z])", "-")
                .toLowerCase(Locale.ROOT);
    }

    /**
     * The template path a component's fragment is loaded from: the optional {@code componentsPath}
     * root, the component's static {@code pathPrefix} (read reflectively, so a shared abstract base's
     * declaration composes down to every subclass that doesn't redeclare it), the component's own static
     * {@code path} sub-path (read reflectively so subclasses may shadow it), and the tag name — joined
     * with {@code /}.
     */
    public static String buildComponentPath(String componentsPath, Class<? extends CompositionComponent> componentClass,
                                            String tagName) {
        List<String> pathParts = new ArrayList<>();
        if (componentsPath != null && !componentsPath.isEmpty()) {
            pathParts.add(trimSlashes(componentsPath));
        }
        String pathPrefix = readStaticStringField(componentClass, "pathPrefix");
        if (!pathPrefix.isEmpty()) {
            pathParts.add(pathPrefix);
        }
        String componentPath = readStaticStringField(componentClass, "path");
        if (!componentPath.isEmpty()) {
            pathParts.add(componentPath);
        }
        pathParts.add(tagName);
        return String.join("/", pathParts);
    }

    private static String readStaticStringField(Class<? extends CompositionComponent> componentClass, String fieldName) {
        try {
            return trimSlashes((String) componentClass.getField(fieldName).get(componentClass));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(CompositionDialect.DIALECT_NAME
                    + ": Could not read the static \"" + fieldName + "\" field of component " + componentClass.getName(), e);
        }
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
