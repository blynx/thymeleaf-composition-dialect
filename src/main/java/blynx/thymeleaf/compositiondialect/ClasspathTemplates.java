package blynx.thymeleaf.compositiondialect;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.Resource;
import io.github.classgraph.ScanResult;

/**
 * Finds the {@code *.html} resources on the class path, for {@link ComponentRegistry} to read component
 * declarations from. ClassGraph does the finding, and what it is here for is not the walk — that is a
 * few dozen lines of JDK — but knowing where the class path <em>is</em> at runtime. Under Spring Boot's
 * launcher the application's classes and its libraries sit inside one jar; under a servlet container or an
 * application server, {@code java.class.path} is the container's own bootstrap and says nothing about the
 * application at all. ClassGraph resolves the roots from the context class loader and carries handlers
 * for those loaders (Boot's, Tomcat's, JBoss's, OSGi's, the module system's, among others), so an imported
 * component library is found wherever the deployment has put it — a jar nested inside a fat jar, one
 * lying beside extracted classes, one named by a manifest's {@code Class-Path}, or one in a webapp's
 * {@code WEB-INF/lib}.
 *
 * <p>Class scanning is left off ({@code enableClassInfo()} is never called), so only the class path
 * structure and resource paths are indexed, and a resource's bytes are loaded only when it is wanted.
 * Paths are reported relative to the class path element they were found in and never rewritten: a fat
 * jar's own templates come back as {@code BOOT-INF/classes/templates/…}, a nested library's as
 * {@code templates/…}. The registry matches on the tail of the path, so the prefix is never something it
 * needs to know. Where the same path exists under more than one root the first wins, which is also the
 * one the class loader would serve.
 */
final class ClasspathTemplates {

    private ClasspathTemplates() {
    }

    /** Every wanted {@code *.html} resource, wherever the runtime class path turns out to be. */
    static Map<String, byte[]> onRuntimeClasspath(Predicate<String> wanted) {
        return collect(new ClassGraph(), wanted);
    }

    /**
     * {@link #onRuntimeClasspath} against specific roots — directories or jar files — so a test can lay out
     * a jar, a Boot fat jar or an extracted application and check what is found in it.
     */
    static Map<String, byte[]> under(List<Path> roots, Predicate<String> wanted) {
        List<File> files = roots.stream().map(Path::toFile).toList();
        return collect(new ClassGraph().overrideClasspath(files), wanted);
    }

    private static Map<String, byte[]> collect(ClassGraph classGraph, Predicate<String> wanted) {
        Map<String, byte[]> found = new LinkedHashMap<>();
        try (ScanResult scan = classGraph.scan()) {
            for (Resource resource : scan.getResourcesWithExtension("html")) {
                String path = resource.getPathRelativeToClasspathElement();
                if (!wanted.test(path) || found.containsKey(path)) {
                    continue;
                }
                try {
                    found.put(path, resource.load());
                } catch (IOException e) {
                    throw new IllegalStateException(
                            CompositionDialect.DIALECT_NAME + ": could not read " + resource, e);
                } finally {
                    resource.close();
                }
            }
        }
        return found;
    }
}
