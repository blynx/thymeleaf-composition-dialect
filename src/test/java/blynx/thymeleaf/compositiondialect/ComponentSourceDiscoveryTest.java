package blynx.thymeleaf.compositiondialect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@code META-INF/thymeleaf-composition/source.properties} discovery, against a {@link URLClassLoader}
 * built from a fresh {@code @TempDir} rather than this module's own test classpath — so a descriptor
 * written for this test cannot leak into every other test that happens to call {@code
 * ComponentSource.discoverFromClasspath()} (most visibly {@code CompositionDialectAutoConfigurationTest},
 * which calls it on every run).
 */
class ComponentSourceDiscoveryTest {

    @Test
    void aDescriptorBecomesAComponentSource(@TempDir Path root) throws IOException {
        writeDescriptor(root, """
                templates-path=webawesome
                namespace=wa
                default-package=com.example.webawesome
                """);

        List<ComponentSource> discovered = ComponentSource.discoverFromClasspath(classLoaderOver(root));

        assertEquals(List.of(new ComponentSource("webawesome", "com.example.webawesome", "wa")), discovered);
    }

    @Test
    void namespaceAndDefaultPackageAreBothOptional(@TempDir Path root) throws IOException {
        writeDescriptor(root, "templates-path=plain-html-lib\n");

        List<ComponentSource> discovered = ComponentSource.discoverFromClasspath(classLoaderOver(root));

        assertEquals(List.of(new ComponentSource("plain-html-lib")), discovered);
    }

    @Test
    void aDescriptorWithNoTemplatesPathFailsLoudly(@TempDir Path root) throws IOException {
        writeDescriptor(root, "namespace=wa\n");

        assertThrows(IllegalStateException.class,
                () -> ComponentSource.discoverFromClasspath(classLoaderOver(root)));
    }

    /**
     * Two classpath roots resolving to the same {@code (templatesPath, tagNamespace)} pair — a shaded or
     * fat-jar layout can return the same jar's descriptor more than once — are deduplicated to one.
     */
    @Test
    void duplicateDescriptorsAreDeduplicated(@TempDir Path rootA, @TempDir Path rootB) throws IOException {
        writeDescriptor(rootA, "templates-path=webawesome\nnamespace=wa\n");
        writeDescriptor(rootB, "templates-path=webawesome\nnamespace=wa\n");

        List<ComponentSource> discovered = ComponentSource.discoverFromClasspath(classLoaderOver(rootA, rootB));

        assertEquals(1, discovered.size(), discovered.toString());
        assertEquals(new ComponentSource("webawesome", null, "wa"), discovered.getFirst());
    }

    private static void writeDescriptor(Path root, String content) throws IOException {
        Path descriptor = root.resolve("META-INF/thymeleaf-composition/source.properties");
        Files.createDirectories(descriptor.getParent());
        Files.writeString(descriptor, content);
    }

    private static URLClassLoader classLoaderOver(Path... roots) {
        URL[] urls = new URL[roots.length];
        for (int i = 0; i < roots.length; i++) {
            urls[i] = toUrl(roots[i]);
        }
        return new URLClassLoader(urls, ComponentSourceDiscoveryTest.class.getClassLoader());
    }

    private static URL toUrl(Path path) {
        try {
            return path.toUri().toURL();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
