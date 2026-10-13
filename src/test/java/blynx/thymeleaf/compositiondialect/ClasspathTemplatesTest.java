package blynx.thymeleaf.compositiondialect;

import static blynx.thymeleaf.compositiondialect.ClasspathFixtures.BOOT_FAT_JAR_MANIFEST;
import static blynx.thymeleaf.compositiondialect.ClasspathFixtures.COMPONENT;
import static blynx.thymeleaf.compositiondialect.ClasspathFixtures.PLAIN_TEMPLATE;
import static blynx.thymeleaf.compositiondialect.ClasspathFixtures.bytes;
import static blynx.thymeleaf.compositiondialect.ClasspathFixtures.jar;
import static blynx.thymeleaf.compositiondialect.ClasspathFixtures.write;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Template discovery against each shape a class path root can take. ClassGraph does the walking; what is
 * pinned here is the contract {@link ComponentRegistry} relies on — which paths come back, exactly, and
 * that only wanted ones are read. A fat jar's {@code BOOT-INF/classes/} prefix is asserted kept: the
 * registry tolerates any prefix, and nothing at this level should be rewriting paths on its behalf.
 */
class ClasspathTemplatesTest {

    private static final Predicate<String> ANY = path -> true;

    @Test
    void anExplodedDirectoryReportsPathsRelativeToItWithForwardSlashes(@TempDir Path root) throws IOException {
        write(root.resolve("templates/comps/card.html"), COMPONENT);
        write(root.resolve("templates/comps/sub/deep.html"), COMPONENT);
        write(root.resolve("templates/comps/notes.txt"), "ignored");

        Map<String, byte[]> found = ClasspathTemplates.under(List.of(root), ANY);

        assertEquals(Set.of("templates/comps/card.html", "templates/comps/sub/deep.html"), found.keySet());
        assertArrayEquals(bytes(COMPONENT), found.get("templates/comps/card.html"));
    }

    @Test
    void aPlainJarReportsItsEntries(@TempDir Path tmp) throws IOException {
        Path jar = tmp.resolve("lib.jar");
        jar(jar, Map.of(
                "templates/comps/card.html", bytes(COMPONENT),
                "templates/comps/style.css", bytes("ignored")));

        Map<String, byte[]> found = ClasspathTemplates.under(List.of(jar), ANY);

        assertEquals(Set.of("templates/comps/card.html"), found.keySet());
    }

    /**
     * The one that was silently broken before: a component library is a jar <em>inside</em> the
     * application's fat jar, and the only class path entry the JVM was started with is that fat jar.
     */
    @Test
    void aBootFatJarYieldsItsOwnClassesAndTheLibrariesNestedInsideIt(@TempDir Path tmp) throws IOException {
        Path lib = tmp.resolve("lib.jar");
        jar(lib, Map.of("templates/wa/wa-button.html", bytes(COMPONENT)));
        Path app = tmp.resolve("app.jar");
        jar(app, BOOT_FAT_JAR_MANIFEST, Map.of(
                "BOOT-INF/classes/templates/comps/card.html", bytes(COMPONENT),
                "BOOT-INF/lib/lib.jar", Files.readAllBytes(lib),
                "org/springframework/boot/loader/launch/JarLauncher.class", bytes("not really")));

        Map<String, byte[]> found = ClasspathTemplates.under(List.of(app), ANY);

        // The application's own path keeps its prefix; the nested library's is as the library shipped it.
        assertEquals(Set.of("BOOT-INF/classes/templates/comps/card.html", "templates/wa/wa-button.html"),
                found.keySet());
        assertArrayEquals(bytes(COMPONENT), found.get("templates/wa/wa-button.html"));
    }

    /** The same application after {@code java -Djarmode=tools -jar app.jar extract --layers}, run in place. */
    @Test
    void anExtractedBootApplicationYieldsTheJarsLyingBesideItsClasses(@TempDir Path root) throws IOException {
        write(root.resolve("BOOT-INF/classes/templates/comps/card.html"), COMPONENT);
        jar(root.resolve("BOOT-INF/lib/lib.jar"), Map.of("templates/wa/wa-button.html", bytes(COMPONENT)));

        Map<String, byte[]> found = ClasspathTemplates.under(List.of(root), ANY);

        assertEquals(Set.of("BOOT-INF/classes/templates/comps/card.html", "templates/wa/wa-button.html"),
                found.keySet());
    }

    /** Boot's other extracted layout, and plain {@code java -jar} generally: libraries named by the manifest. */
    @Test
    void aJarsManifestClassPathIsFollowed(@TempDir Path tmp) throws IOException {
        jar(tmp.resolve("lib/lib.jar"), Map.of("templates/wa/wa-button.html", bytes(COMPONENT)));
        Path app = tmp.resolve("app.jar");
        jar(app, Map.of("Class-Path", "lib/lib.jar"), Map.of("templates/comps/card.html", bytes(COMPONENT)));

        Map<String, byte[]> found = ClasspathTemplates.under(List.of(app), ANY);

        assertEquals(Set.of("templates/comps/card.html", "templates/wa/wa-button.html"), found.keySet());
    }

    /** As a class loader would serve it: whichever root comes first on the class path owns the path. */
    @Test
    void theFirstRootWinsWhenTwoCarryTheSamePath(@TempDir Path tmp) throws IOException {
        Path first = tmp.resolve("first");
        Path second = tmp.resolve("second");
        write(first.resolve("templates/comps/card.html"), "first");
        write(second.resolve("templates/comps/card.html"), "second");

        assertArrayEquals(bytes("first"),
                ClasspathTemplates.under(List.of(first, second), ANY).get("templates/comps/card.html"));
        assertArrayEquals(bytes("second"),
                ClasspathTemplates.under(List.of(second, first), ANY).get("templates/comps/card.html"));
    }

    /** Only what a source could want is read at all: an application's other templates are not touched. */
    @Test
    void onlyWantedPathsAreReported(@TempDir Path root) throws IOException {
        write(root.resolve("templates/comps/card.html"), COMPONENT);
        write(root.resolve("templates/pages/home.html"), PLAIN_TEMPLATE);

        Map<String, byte[]> found = ClasspathTemplates.under(List.of(root), path -> path.contains("/comps/"));

        assertEquals(Set.of("templates/comps/card.html"), found.keySet());
    }

    /** A class path routinely names things that are not there; the JVM ignores them, so does this. */
    @Test
    void aRootThatDoesNotExistIsSkipped(@TempDir Path tmp) throws IOException {
        Path present = tmp.resolve("present");
        write(present.resolve("templates/comps/card.html"), COMPONENT);

        Map<String, byte[]> found = ClasspathTemplates.under(
                List.of(tmp.resolve("absent"), tmp.resolve("absent.jar"), present), ANY);

        assertEquals(Set.of("templates/comps/card.html"), found.keySet());
    }
}
