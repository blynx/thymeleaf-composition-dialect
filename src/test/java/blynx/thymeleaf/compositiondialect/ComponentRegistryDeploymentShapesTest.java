package blynx.thymeleaf.compositiondialect;

import static blynx.thymeleaf.compositiondialect.ClasspathFixtures.BOOT_FAT_JAR_MANIFEST;
import static blynx.thymeleaf.compositiondialect.ClasspathFixtures.COMPONENT;
import static blynx.thymeleaf.compositiondialect.ClasspathFixtures.PLAIN_TEMPLATE;
import static blynx.thymeleaf.compositiondialect.ClasspathFixtures.bytes;
import static blynx.thymeleaf.compositiondialect.ClasspathFixtures.jar;
import static blynx.thymeleaf.compositiondialect.ClasspathFixtures.write;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Discovery end to end — sources in, descriptors out — against the class path shapes an application is
 * actually deployed in, rather than the exploded directory this module's own tests run on. The question
 * each one answers is the same: does an application's own component <em>and</em> an imported library's
 * both register, with the template paths the resolver will later be asked for?
 */
class ComponentRegistryDeploymentShapesTest {

    private static final List<ComponentSource> APP_AND_LIBRARY = List.of(
            new ComponentSource("comps"),
            ComponentSource.namespaced("wa", "wa"));

    @Test
    void aBootFatJarRegistersTheApplicationsComponentsAndAnImportedLibrarys(@TempDir Path tmp) throws IOException {
        Path lib = tmp.resolve("lib.jar");
        jar(lib, Map.of("templates/wa/wa-button.html", bytes(COMPONENT)));
        Path app = tmp.resolve("app.jar");
        jar(app, BOOT_FAT_JAR_MANIFEST, Map.of(
                "BOOT-INF/classes/templates/comps/card.html", bytes(COMPONENT),
                "BOOT-INF/classes/templates/comps/nested/badge.html", bytes(COMPONENT),
                "BOOT-INF/classes/templates/pages/home.html", bytes(PLAIN_TEMPLATE),
                "BOOT-INF/lib/lib.jar", Files.readAllBytes(lib)));

        ComponentRegistry registry = ComponentRegistry.scan(APP_AND_LIBRARY, "c", List.of(app));

        assertEquals(List.of("badge", "card", "wa-button"), tagNames(registry));
        // The path the template resolver is asked for: below the templates root, with no trace of where
        // on the class path the file was found.
        assertEquals("comps/nested/badge", templatePath(registry, "badge"));
        assertEquals("wa/wa-button", templatePath(registry, "wa-button"));
        registry.requireNoEmptySources();
    }

    @Test
    void anExtractedBootApplicationRegistersBoth(@TempDir Path root) throws IOException {
        write(root.resolve("BOOT-INF/classes/templates/comps/card.html"), COMPONENT);
        jar(root.resolve("BOOT-INF/lib/lib.jar"), Map.of("templates/wa/wa-button.html", bytes(COMPONENT)));

        ComponentRegistry registry = ComponentRegistry.scan(APP_AND_LIBRARY, "c", List.of(root));

        assertEquals(List.of("card", "wa-button"), tagNames(registry));
        registry.requireNoEmptySources();
    }

    @Test
    void aJarWhoseManifestNamesTheLibraryRegistersBoth(@TempDir Path tmp) throws IOException {
        jar(tmp.resolve("lib/lib.jar"), Map.of("templates/wa/wa-button.html", bytes(COMPONENT)));
        Path app = tmp.resolve("app.jar");
        jar(app, Map.of("Class-Path", "lib/lib.jar"), Map.of("templates/comps/card.html", bytes(COMPONENT)));

        ComponentRegistry registry = ComponentRegistry.scan(APP_AND_LIBRARY, "c", List.of(app));

        assertEquals(List.of("card", "wa-button"), tagNames(registry));
    }

    /** A template that only happens to live under the root is walked over — declaration or nothing. */
    @Test
    void aTemplateWithoutADeclarationIsNotAComponentInAJarEither(@TempDir Path tmp) throws IOException {
        Path jar = tmp.resolve("app.jar");
        jar(jar, Map.of(
                "templates/comps/card.html", bytes(COMPONENT),
                "templates/comps/fragment.html", bytes(PLAIN_TEMPLATE)));

        ComponentRegistry registry = ComponentRegistry.scan(List.of(new ComponentSource("comps")), "c", List.of(jar));

        assertEquals(List.of("card"), tagNames(registry));
    }

    private static List<String> tagNames(ComponentRegistry registry) {
        return registry.components().stream().map(ComponentDescriptor::tagName).toList();
    }

    private static String templatePath(ComponentRegistry registry, String tagName) {
        return registry.findByTagName("c", tagName).orElseThrow().templatePath();
    }
}
