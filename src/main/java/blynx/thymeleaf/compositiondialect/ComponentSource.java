package blynx.thymeleaf.compositiondialect;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * One templates root to discover components under, together with the optional package a bare (non-dotted
 * or relatively-dotted) {@code impl} resolves against, and the optional tag namespace a library declares
 * its templates in.
 *
 * <p>A dialect is built from any number of these — typically the application's own components plus one per
 * imported component library. Discovery walks {@code templatesPath} for {@code *.html} files carrying a
 * {@code <c:component>} declaration; the tag name is the file's own basename, never derived from a class.
 * {@code defaultPackage} is purely a resolution shorthand for {@code impl} — see {@code <c:component
 * impl="…"/>} in the reference docs — and a source with no classes at all (a pure-HTML pattern library)
 * simply omits it.
 *
 * <pre>{@code
 * new CompositionDialect(List.of(
 *         new ComponentSource("components", "com.example.app.components"),
 *         ComponentSource.namespaced("webawesome", "wa")));
 * }</pre>
 *
 * <p>Every component of every source shares the one dialect prefix, so two sources whose templates both
 * produce the same tag name (same basename) collide; {@link ComponentRegistry#requireNoCollisions()}
 * reports it. A declared {@code tagNamespace} makes that collision structurally impossible for a library's
 * own tags: every template under a namespaced source must itself be named {@code <namespace>-*} — checked
 * at scan time — and two sources declaring the same namespace fail at construction, from the source list
 * alone, before anything is even walked. See {@link #namespaced}.
 */
public record ComponentSource(String templatesPath, String defaultPackage, String tagNamespace) {

    /** A source with no default package and no namespace — every {@code impl} must be fully-qualified. */
    public ComponentSource(String templatesPath) {
        this(templatesPath, null, null);
    }

    /** A source with no declared namespace — typically the application's own components. */
    public ComponentSource(String templatesPath, String defaultPackage) {
        this(templatesPath, defaultPackage, null);
    }

    /**
     * A library source that owns its tag space by construction: every template under {@code templatesPath}
     * must be named {@code <tagNamespace>-*}, enforced — not applied — so the file name is always the tag
     * name with no strip-then-lookup step. No default package, for a pure-HTML pattern library shipping no
     * classes at all.
     */
    public static ComponentSource namespaced(String templatesPath, String tagNamespace) {
        return new ComponentSource(templatesPath, null, tagNamespace);
    }

    /** {@link #namespaced(String, String)}, for a library whose components do have backing classes. */
    public static ComponentSource namespaced(String templatesPath, String tagNamespace, String defaultPackage) {
        return new ComponentSource(templatesPath, defaultPackage, tagNamespace);
    }

    public ComponentSource {
        if (templatesPath == null || templatesPath.isBlank()) {
            throw new IllegalArgumentException(
                    CompositionDialect.DIALECT_NAME + ": a component source needs a templates path to scan");
        }
        if (tagNamespace != null && tagNamespace.isBlank()) {
            throw new IllegalArgumentException(
                    CompositionDialect.DIALECT_NAME + ": a component source's tag namespace cannot be blank — "
                            + "omit it (null) for a source that declares none");
        }
    }

    private static final String DESCRIPTOR_RESOURCE = "META-INF/thymeleaf-composition/source.properties";

    /**
     * Every {@code ComponentSource} a jar on the classpath declares via
     * {@code META-INF/thymeleaf-composition/source.properties} — {@code templates-path} (required),
     * {@code namespace} and {@code default-package} (both optional). This is the whole of what a library
     * has to ship to be importable with zero Java code and no Spring dependency: no interface to
     * implement, no class to write.
     *
     * <p>{@code ClassLoader.getResources} naturally returns one descriptor per jar; two resolving to the
     * same {@code (templatesPath, tagNamespace)} pair — a shaded/fat-jar layout can return the same jar's
     * descriptor more than once — are deduplicated, keeping the first found.
     */
    public static List<ComponentSource> discoverFromClasspath() {
        return discoverFromClasspath(Thread.currentThread().getContextClassLoader());
    }

    /** {@link #discoverFromClasspath()} against a specific class loader, mainly so tests can isolate it. */
    public static List<ComponentSource> discoverFromClasspath(ClassLoader classLoader) {
        ClassLoader loader = classLoader != null ? classLoader : ComponentSource.class.getClassLoader();
        Map<List<String>, ComponentSource> byIdentity = new LinkedHashMap<>();
        Enumeration<URL> resources;
        try {
            resources = loader.getResources(DESCRIPTOR_RESOURCE);
        } catch (IOException e) {
            throw new IllegalStateException(
                    CompositionDialect.DIALECT_NAME + ": could not list " + DESCRIPTOR_RESOURCE + " descriptors", e);
        }
        while (resources.hasMoreElements()) {
            URL url = resources.nextElement();
            Properties properties = new Properties();
            try (InputStream in = url.openStream()) {
                properties.load(in);
            } catch (IOException e) {
                throw new IllegalStateException(CompositionDialect.DIALECT_NAME + ": could not read " + url, e);
            }
            String templatesPath = trimToNull(properties.getProperty("templates-path"));
            String namespace = trimToNull(properties.getProperty("namespace"));
            String defaultPackage = trimToNull(properties.getProperty("default-package"));
            if (templatesPath == null) {
                throw new IllegalStateException(
                        CompositionDialect.DIALECT_NAME + ": " + url + " declares no templates-path");
            }
            byIdentity.putIfAbsent(List.of(templatesPath, String.valueOf(namespace)),
                    new ComponentSource(templatesPath, defaultPackage, namespace));
        }
        return List.copyOf(new ArrayList<>(byIdentity.values()));
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
