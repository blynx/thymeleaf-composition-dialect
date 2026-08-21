package blynx.thymeleaf.compositiondialect;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import org.thymeleaf.IEngineConfiguration;
import org.thymeleaf.templateresolver.ITemplateResolver;
import org.thymeleaf.templateresolver.TemplateResolution;

/**
 * The single source of truth for component discovery, naming, and template-path rules.
 *
 * <p>Built from a template walk of one or more {@link ComponentSource}s (see {@link #scan}) — every
 * {@code *.html} resource under a source's {@code templatesPath} that carries a {@code <c:component>}
 * declaration is a component; a template with none is left alone, so partials and layouts may share the
 * tree. The tag name is always the template's own basename; a declared {@code impl} is resolved to a class
 * eagerly, here, so a typo'd class name fails at construction rather than at first render.
 *
 * <p>Holds an immutable, deterministically ordered list of {@link ComponentDescriptor}s plus indexes for
 * the "what components exist?" queries that Module Organization, the Component Playground, and Developer
 * Tooling need. {@link CompositionDialect} is a consumer of the registry rather than the place discovery
 * lives.
 */
public final class ComponentRegistry {

    /**
     * Tag names this dialect's own grammar already claims, which a component may therefore not take.
     * A template named {@code component.html}, {@code prop.html} — or one named {@code slot.html} — would
     * otherwise collide with the dialect's own directive/marker of the same name.
     *
     * <p>{@code rest} is absent deliberately: {@code c:rest} is an attribute, and attribute and element
     * names are matched separately, so a {@code <c:rest>} component collides with nothing.
     * {@link CompositionCallerProcessor#TAG_NAME} is absent because it cannot be a template basename.
     */
    private static final Set<String> RESERVED_TAG_NAMES = Set.of("slot", "component", "prop");

    /** Short names a {@code <c:prop type="…">} may use instead of a fully-qualified class name. */
    private static final Map<String, Class<?>> PRIMITIVE_TYPE_NAMES = Map.of(
            "string", String.class,
            "int", int.class,
            "long", long.class,
            "double", double.class,
            "boolean", boolean.class);

    private static final Pattern HTML_RESOURCE = Pattern.compile(".*\\.html$");

    private final List<ComponentDescriptor> components;
    private final Map<String, List<ComponentDescriptor>> byPrefix;
    private final Map<String, List<ComponentDescriptor>> byTagName;
    private final Map<ComponentSource, List<ComponentDescriptor>> bySource;
    private final Set<ComponentSource> requestedSources;

    private ComponentRegistry(List<ComponentDescriptor> descriptors, Set<ComponentSource> requestedSources) {
        List<ComponentDescriptor> sorted = new ArrayList<>(descriptors);
        sorted.sort(Comparator.comparing(ComponentDescriptor::prefix).thenComparing(ComponentDescriptor::tagName));
        this.components = List.copyOf(sorted);
        this.byPrefix = groupBy(this.components, ComponentDescriptor::prefix);
        this.byTagName = groupBy(this.components, ComponentDescriptor::tagName);
        this.bySource = groupBy(this.components, ComponentDescriptor::source);
        // Widened with every source actually claimed by a descriptor: aggregate() merges registries built
        // separately, and a caller cannot be trusted to have passed every one of them in explicitly.
        Set<ComponentSource> widened = new LinkedHashSet<>(requestedSources);
        this.components.forEach(descriptor -> widened.add(descriptor.source()));
        this.requestedSources = Set.copyOf(widened);
    }

    /**
     * Walks {@code source}'s {@code templatesPath} for every {@code *.html} resource carrying a
     * {@code <c:component>} declaration and builds a registry from what it finds. A resource with no
     * declaration is skipped — it is just a template, not a component.
     */
    public static ComponentRegistry scan(ComponentSource source, String prefix) {
        return scan(source, prefix, findHtmlResources());
    }

    /**
     * Scans every source and merges the results into the one registry a dialect is built from. Namespace
     * uniqueness is checked first, from the source list alone — before anything is walked — since two
     * libraries claiming the same namespace is wrong regardless of what either one's templates turn out to
     * contain.
     */
    public static ComponentRegistry scan(Collection<ComponentSource> sources, String prefix) {
        requireNoDuplicateNamespaces(sources);
        // One classpath walk shared across every source, rather than one per source.
        Set<String> htmlResources = findHtmlResources();
        return aggregate(sources.stream().map(source -> scan(source, prefix, htmlResources)).toList());
    }

    private static ComponentRegistry scan(ComponentSource source, String prefix, Set<String> htmlResources) {
        ComponentDeclarationReader reader = new ComponentDeclarationReader(prefix);
        Pattern underRoot = Pattern.compile(
                "(?:^|.*/)" + Pattern.quote(source.templatesPath()) + "/((?:.*/)?)([^/]+)\\.html$");

        List<ComponentDescriptor> descriptors = new ArrayList<>();
        for (String resource : htmlResources) {
            Matcher matcher = underRoot.matcher(resource);
            if (!matcher.matches()) {
                continue;
            }
            String subPath = matcher.group(1);
            String tagName = matcher.group(2);
            String templatePath = source.templatesPath() + "/" + subPath + tagName;
            String describeForErrors = templatePath + ".html";

            ComponentDeclarationReader.Declaration declaration = readDeclaration(reader, resource, describeForErrors);
            if (declaration == null) {
                continue;
            }
            requireNamespaceIfDeclared(source, tagName, describeForErrors);
            descriptors.add(toDescriptor(declaration, source, prefix, tagName, templatePath, describeForErrors));
        }
        return new ComponentRegistry(descriptors, Set.of(source));
    }

    /**
     * Fails at construction, from the source list alone, if two sources declare the same {@code
     * tagNamespace} — detectable with no enumeration at all, since a namespace owning a tag's space by
     * construction is meaningless if two sources both claim to own the same one.
     */
    private static void requireNoDuplicateNamespaces(Collection<ComponentSource> sources) {
        Map<String, List<ComponentSource>> byNamespace = new LinkedHashMap<>();
        for (ComponentSource source : sources) {
            if (source.tagNamespace() != null) {
                byNamespace.computeIfAbsent(source.tagNamespace(), k -> new ArrayList<>()).add(source);
            }
        }
        byNamespace.forEach((namespace, claimants) -> {
            if (claimants.size() > 1) {
                throw new IllegalStateException(CompositionDialect.DIALECT_NAME + ": the \"" + namespace
                        + "\" tag namespace is declared by more than one source: "
                        + claimants.stream().map(ComponentSource::templatesPath).collect(Collectors.joining(", "))
                        + " — a namespace is a library's own to claim once.");
            }
        });
    }

    /**
     * Enforced, not applied: a namespaced source writes its own {@code <namespace>-*} file names, and this
     * only checks it did. Kept as a hard scan-time failure — the same "boot fails with a clear message"
     * treatment as an unresolvable {@code impl} — because the alternative is a library silently shipping a
     * tag outside the territory its namespace promised.
     */
    private static void requireNamespaceIfDeclared(ComponentSource source, String tagName, String describeForErrors) {
        String namespace = source.tagNamespace();
        if (namespace == null || tagName.startsWith(namespace + "-")) {
            return;
        }
        throw new IllegalStateException(CompositionDialect.DIALECT_NAME + ": " + describeForErrors
                + " is under a source namespaced \"" + namespace + "\", so its tag must be named \""
                + namespace + "-*\" — rename the file to " + namespace + "-" + tagName + ".html.");
    }

    private static ComponentDeclarationReader.Declaration readDeclaration(
            ComponentDeclarationReader reader, String resource, String describeForErrors) {
        try (InputStream in = openResource(resource)) {
            if (in == null) {
                throw new IllegalStateException(CompositionDialect.DIALECT_NAME
                        + ": found " + resource + " while walking the classpath, but could not open it");
            }
            return reader.read(in, describeForErrors);
        } catch (IOException e) {
            throw new IllegalStateException(CompositionDialect.DIALECT_NAME
                    + ": could not read " + describeForErrors, e);
        }
    }

    private static ComponentDescriptor toDescriptor(ComponentDeclarationReader.Declaration declaration,
                                                     ComponentSource source, String prefix, String tagName,
                                                     String templatePath, String describeForErrors) {
        boolean hasImpl = declaration.impl() != null && !declaration.impl().isBlank();
        boolean hasProps = !declaration.props().isEmpty();
        if (hasImpl && hasProps) {
            throw new IllegalStateException(CompositionDialect.DIALECT_NAME + ": " + describeForErrors
                    + " declares both impl and props — a class's own shape is what declares its props, so "
                    + "props on the same declaration as impl could only ever mean something contradictory. "
                    + "Drop props here; declare the class's props on the class itself.");
        }

        Optional<Class<? extends CompositionComponent>> componentClass = hasImpl
                ? Optional.of(resolveImpl(declaration.impl(), source.defaultPackage(), describeForErrors))
                : Optional.empty();
        List<PropSpec> classlessPropSpecs = componentClass.isPresent()
                ? List.of()
                : declaration.props().stream()
                        .map(prop -> toPropSpec(prop, describeForErrors))
                        .toList();
        Set<String> props = componentClass.map(ComponentRegistry::readDeclaredProps)
                .orElseGet(() -> classlessPropSpecs.stream().map(PropSpec::kebabName)
                        .collect(Collectors.toCollection(LinkedHashSet::new)));

        return new ComponentDescriptor(componentClass, source, prefix, tagName, templatePath, props, classlessPropSpecs);
    }

    private static PropSpec toPropSpec(ComponentDeclarationReader.PropDeclaration prop, String describeForErrors) {
        return new PropSpec(prop.name(), prop.required(), prop.defaultValue(),
                resolvePropType(prop.type(), prop.name(), describeForErrors));
    }

    /**
     * Resolves a {@code <c:prop type="…">}: one of the short names for a primitive/{@code String}, or a
     * fully-qualified class name otherwise. Resolved here, at scan time, for the same reason {@code impl}
     * is — a typo'd type fails loudly at startup rather than at first render.
     */
    private static Class<?> resolvePropType(String typeName, String propName, String describeForErrors) {
        if (typeName == null) {
            return null;
        }
        Class<?> primitive = PRIMITIVE_TYPE_NAMES.get(typeName);
        if (primitive != null) {
            return primitive;
        }
        Class<?> resolved = tryLoad(typeName);
        if (resolved == null) {
            throw new IllegalStateException(CompositionDialect.DIALECT_NAME + ": " + describeForErrors
                    + " declares prop \"" + propName + "\" with type=\"" + typeName + "\", which could not be "
                    + "resolved as a fully-qualified class name (and is not one of the short names: "
                    + String.join(", ", PRIMITIVE_TYPE_NAMES.keySet()) + ").");
        }
        return resolved;
    }

    /**
     * Resolves a declared {@code impl} to a class: relative to {@code defaultPackage} first (covering both
     * a bare simple name and a relative dotted path), then as a fully-qualified name. Done here, at scan
     * time, so a typo'd or moved class fails loudly at startup rather than at first render.
     */
    private static Class<? extends CompositionComponent> resolveImpl(
            String impl, String defaultPackage, String describeForErrors) {
        String relative = defaultPackage != null && !defaultPackage.isBlank() ? defaultPackage + "." + impl : null;
        Class<?> resolved = relative != null ? tryLoad(relative) : null;
        if (resolved == null) {
            resolved = tryLoad(impl);
        }
        if (resolved == null) {
            throw new IllegalStateException(CompositionDialect.DIALECT_NAME + ": " + describeForErrors
                    + " declares impl=\"" + impl + "\", which could not be resolved"
                    + (relative != null
                            ? " — tried \"" + relative + "\" (relative to this source's default package) and \""
                                    + impl + "\" (as a fully-qualified name)."
                            : " as a fully-qualified class name."));
        }
        if (!CompositionComponent.class.isAssignableFrom(resolved)) {
            throw new IllegalStateException(CompositionDialect.DIALECT_NAME + ": " + describeForErrors
                    + " declares impl=\"" + impl + "\" (" + resolved.getName() + "), which does not implement "
                    + CompositionComponent.class.getSimpleName() + ".");
        }
        @SuppressWarnings("unchecked")
        Class<? extends CompositionComponent> componentClass = (Class<? extends CompositionComponent>) resolved;
        return componentClass;
    }

    private static Class<?> tryLoad(String fqcn) {
        try {
            return Class.forName(fqcn);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    /**
     * Every {@code *.html} resource on the classpath, keyed by its full resource path (e.g.
     * {@code templates/casescomponents/panel.html}) exactly as {@code ClassLoader.getResourceAsStream}
     * needs it. Scoped by {@code java.class.path} rather than {@link ClasspathHelper#forClassLoader()}: the
     * latter only sees {@code URLClassLoader}s, which the JVM does not guarantee (and Gradle's own test
     * worker does not use), so it silently returns nothing on a modern JDK.
     */
    private static Set<String> findHtmlResources() {
        Reflections reflections = new Reflections(new ConfigurationBuilder()
                .setUrls(ClasspathHelper.forJavaClassPath())
                .setScanners(Scanners.Resources));
        return reflections.getResources(HTML_RESOURCE);
    }

    private static InputStream openResource(String resource) {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        return (loader != null ? loader : ComponentRegistry.class.getClassLoader()).getResourceAsStream(resource);
    }

    /** Convenience for the single-source case. */
    public static ComponentRegistry scan(String templatesPath, String defaultPackage, String prefix) {
        return scan(new ComponentSource(templatesPath, defaultPackage), prefix);
    }

    /**
     * Combines several registries into one unified view — how an application's own components and each
     * imported component library's end up in a single dialect. Merging alone reports nothing: a tag
     * claimed twice simply shows up as a multi-entry list in {@link #byTagName()}, and rejecting that is
     * {@link #requireNoCollisions()}'s job, run once over the merged result.
     */
    public static ComponentRegistry aggregate(Collection<ComponentRegistry> registries) {
        List<ComponentDescriptor> all = new ArrayList<>();
        Set<ComponentSource> sources = new LinkedHashSet<>();
        for (ComponentRegistry registry : registries) {
            all.addAll(registry.components);
            sources.addAll(registry.requestedSources);
        }
        return new ComponentRegistry(all, sources);
    }

    /** All known components, deterministically ordered by {@code (prefix, tagName)}. Immutable. */
    public List<ComponentDescriptor> components() {
        return components;
    }

    /**
     * Components grouped by dialect prefix. Immutable. Every component of every source shares the one
     * prefix, so this is a single entry unless registries built under different prefixes were aggregated
     * by hand; {@link #bySource()} is what separates one module's components from another's.
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
     * Components grouped by the {@link ComponentSource} they were found under. Immutable. One entry per
     * module — the application's own components, and each imported library's.
     */
    public Map<ComponentSource, List<ComponentDescriptor>> bySource() {
        return bySource;
    }

    /**
     * Tags claimed by more than one component template, keyed by {@link ComponentDescriptor#qualifiedName()}.
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
     * Fails fast if any tag is claimed by more than one component template. Without this, one silently
     * wins a {@code HashSet} of processors built from the registry and the other becomes permanently
     * unreachable, with no error at all.
     *
     * <p>Run over the merged registry rather than per source: a component library and the application
     * importing it both owning a {@code button.html} is exactly the clash worth catching, and neither
     * source can see it alone. Subdirectories under a source's own {@code templatesPath} stay cosmetic to
     * the path, not the tag: {@code alpha/card.html} and {@code bravo/card.html} under the same source
     * collide too, for the same reason.
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

    /**
     * Fails fast if any source contributed no components at all — almost certainly a wrong
     * {@code templatesPath}, rather than a source that genuinely has nothing: a misconfigured source
     * otherwise looks identical to an empty one, and the mistake is only found once a tag from it is used
     * and reported as unresolved with no hint of why.
     */
    public void requireNoEmptySources() {
        List<ComponentSource> empty = requestedSources.stream()
                .filter(source -> bySource.getOrDefault(source, List.of()).isEmpty())
                .toList();
        if (empty.isEmpty()) {
            return;
        }
        StringBuilder message = new StringBuilder(CompositionDialect.DIALECT_NAME
                + ": source(s) that declared a templates path but contributed no components:\n");
        for (ComponentSource source : empty) {
            message.append("  ").append(source.templatesPath())
                    .append(source.tagNamespace() != null ? " (namespace \"" + source.tagNamespace() + "\")" : "")
                    .append("\n");
        }
        message.append("Almost certainly a wrong templatesPath, or a resolver prefix pointing away from it — "
                + "not a source that legitimately has nothing.");
        throw new IllegalStateException(message.toString());
    }

    /**
     * The explanatory clause for an unresolved tag, naming the likely cause rather than leaving "check the
     * spelling" as the only hint: a misspelling inside a namespace this registry knows about, a namespace
     * nothing here provides, or neither — a plain lookup among the application's own, unnamespaced tags.
     */
    String describeUnresolvedTag(String tagName) {
        int dash = tagName.indexOf('-');
        if (dash <= 0) {
            return "not found among your own components";
        }
        String candidate = tagName.substring(0, dash);
        List<String> offeredByNamespace = components.stream()
                .filter(descriptor -> candidate.equals(descriptor.source().tagNamespace()))
                .map(ComponentDescriptor::tagName)
                .sorted()
                .toList();
        if (!offeredByNamespace.isEmpty()) {
            return "the \"" + candidate + "\" namespace offers: " + String.join(", ", offeredByNamespace);
        }
        boolean namespaceKnown = requestedSources.stream().anyMatch(source -> candidate.equals(source.tagNamespace()));
        if (namespaceKnown) {
            return "the \"" + candidate + "\" namespace is declared but offers no components at all — check "
                    + "its source's templatesPath";
        }
        return "nothing provides the \"" + candidate + "\" namespace — is a dependency missing?";
    }

    /**
     * Components whose template no resolver can find, in registry order; empty when every one resolves.
     * Needs the engine's configuration, so it can only run once the engine exists — unlike the collision
     * and reserved-name checks, which the dialect can make on its own at construction.
     *
     * <p>Every descriptor here was already found by walking the classpath directly, so this is not
     * checking "does the file exist" — it is checking that the <em>application's actual resolver chain</em>
     * agrees with what the classpath walk found. Those can disagree: {@code spring.thymeleaf.prefix} may
     * point somewhere the classpath walk cannot see (a filesystem path, a database), or a resolver's own
     * inclusion/exclusion patterns may leave a discovered template unreachable at render time.
     */
    public List<ComponentDescriptor> unresolvableTemplates(IEngineConfiguration configuration) {
        return components.stream().filter(descriptor -> !resolves(configuration, descriptor.templatePath())).toList();
    }

    /**
     * Fails fast if any component's template is unreachable through the application's actual resolver
     * chain, even though the classpath walk that built this registry found it. Without this, the first
     * render of that one component fails, which can be a long way from startup and easy to miss until
     * someone opens the page that uses it.
     */
    public void requireResolvableTemplates(IEngineConfiguration configuration) {
        List<ComponentDescriptor> unresolvable = unresolvableTemplates(configuration);
        if (unresolvable.isEmpty()) {
            return;
        }
        StringBuilder message = new StringBuilder(CompositionDialect.DIALECT_NAME
                + ": no template found for component(s):\n");
        for (ComponentDescriptor descriptor : unresolvable) {
            message.append("  <").append(descriptor.qualifiedName()).append("> expects ")
                    .append(descriptor.templatePath()).append(".html\n");
        }
        message.append("Each was found while walking the classpath, but the application's own Thymeleaf "
                + "resolver chain cannot resolve it — check that its prefix/suffix and any resolvable-pattern "
                + "restrictions actually cover this source's templatesPath.");
        throw new IllegalStateException(message.toString());
    }

    /** Mirrors how the engine resolves a template: first resolver that claims the path decides. */
    private static boolean resolves(IEngineConfiguration configuration, String templatePath) {
        for (ITemplateResolver resolver : configuration.getTemplateResolvers()) {
            TemplateResolution resolution = resolver.resolveTemplate(configuration, null, templatePath, null);
            if (resolution == null) {
                continue;
            }
            // A resolver configured to check existence has already done it; otherwise ask the resource.
            return resolution.isTemplateResourceExistenceVerified() || resolution.getTemplateResource().exists();
        }
        return false;
    }

    /** The descriptor for a component class, if it is registered. */
    public Optional<ComponentDescriptor> findByClass(Class<? extends CompositionComponent> componentClass) {
        return components.stream()
                .filter(descriptor -> descriptor.componentClass().filter(componentClass::equals).isPresent())
                .findFirst();
    }

    /** The descriptor for a fully-qualified {@code prefix:tagName}, if it is registered. */
    public Optional<ComponentDescriptor> findByTagName(String prefix, String tagName) {
        return Optional.ofNullable(descriptorFor(prefix, tagName));
    }

    /**
     * The same lookup as {@link #findByTagName}, without wrapping the answer. This runs once per component
     * occurrence on the render path, where the stream-and-{@link Optional} form allocated a pipeline, two
     * lambdas and an {@code Optional} for what is a hash lookup and a one-element scan.
     */
    ComponentDescriptor descriptorFor(String prefix, String tagName) {
        List<ComponentDescriptor> claimants = byTagName.get(tagName);
        if (claimants == null) {
            return null;
        }
        for (int i = 0; i < claimants.size(); i++) {
            ComponentDescriptor descriptor = claimants.get(i);
            if (descriptor.prefix().equals(prefix)) {
                return descriptor;
            }
        }
        return null;
    }

    private static String describeClaimant(ComponentDescriptor descriptor) {
        return descriptor.templatePath() + ".html";
    }

    /**
     * What to do about a collision, which depends on who owns the clashing components. Two templates under
     * one source are one author's own problem; two sources clashing usually is not, because the tag names a
     * component library ships are fixed by whoever wrote it.
     */
    private static String remedyFor(List<ComponentDescriptor> descriptors) {
        Set<ComponentSource> sources = descriptors.stream()
                .map(ComponentDescriptor::source)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (sources.size() == 1) {
            return "Rename one of them.";
        }
        return "These come from different component sources ("
                + sources.stream().map(ComponentSource::templatesPath).collect(Collectors.joining(", "))
                + "), so rename whichever template you own — an imported library's tag names are its "
                + "author's to change.";
    }

    /** camelCase or PascalCase to kebab-case, e.g. {@code autoHideSeconds -> auto-hide-seconds}. */
    private static String kebabCase(String name) {
        return name.replaceAll("(?!^)(?=[A-Z][a-z])", "-").toLowerCase(Locale.ROOT);
    }

    /**
     * kebab-case to camelCase, e.g. {@code auto-hide-seconds -> autoHideSeconds} — the inverse of {@link
     * #kebabCase}, needed because the read site for a prop is always camel (see docs), including on a
     * classless component's {@link AnonymousComponent}, whose backing map is therefore camel-keyed.
     */
    static String camelCase(String kebab) {
        StringBuilder result = new StringBuilder(kebab.length());
        boolean upperNext = false;
        for (int i = 0; i < kebab.length(); i++) {
            char c = kebab.charAt(i);
            if (c == '-') {
                upperNext = true;
            } else if (upperNext) {
                result.append(Character.toUpperCase(c));
                upperNext = false;
            } else {
                result.append(c);
            }
        }
        return result.toString();
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

    private static <K> Map<K, List<ComponentDescriptor>> groupBy(
            List<ComponentDescriptor> descriptors, Function<ComponentDescriptor, K> key) {
        // LinkedHashMap over the already-sorted descriptor list keeps keys in (prefix, tagName) order;
        // Map.copyOf would drop that ordering, so wrap an unmodifiable view instead.
        Map<K, List<ComponentDescriptor>> grouped = new LinkedHashMap<>();
        for (ComponentDescriptor descriptor : descriptors) {
            grouped.computeIfAbsent(key.apply(descriptor), k -> new ArrayList<>()).add(descriptor);
        }
        grouped.replaceAll((k, v) -> List.copyOf(v));
        return Collections.unmodifiableMap(grouped);
    }
}
