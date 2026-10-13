package blynx.thymeleaf.compositiondialect;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

/**
 * Lays out the class path shapes a deployed application actually has — a directory, a plain jar, a Spring
 * Boot fat jar with libraries nested inside it, the same application extracted to a directory, and a jar
 * pulling others in through its manifest — under a {@code @TempDir}, so discovery can be pointed at each
 * one. The module's own test class path is only ever the first of those, which is how the others went
 * untested for as long as they did.
 */
final class ClasspathFixtures {

    /** The smallest thing that registers as a component: a declaration and a slot, no class behind it. */
    static final String COMPONENT = "<c:component/>\n<div><c:slot/></div>\n";

    /** A template that is only a template — walked over, never registered. */
    static final String PLAIN_TEMPLATE = "<p>not a component</p>\n";

    /**
     * What Spring Boot writes into a fat jar's manifest. Discovery may recognise a Boot layout by this
     * declared metadata rather than by path convention alone, so a fixture claiming to be a fat jar
     * carries it — otherwise the fixture would be testing a jar no build tool actually produces.
     */
    static final Map<String, String> BOOT_FAT_JAR_MANIFEST = Map.of(
            "Main-Class", "org.springframework.boot.loader.launch.JarLauncher",
            "Start-Class", "com.example.Application",
            "Spring-Boot-Classes", "BOOT-INF/classes/",
            "Spring-Boot-Lib", "BOOT-INF/lib/");

    private ClasspathFixtures() {
    }

    static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    static byte[] bytes(String content) {
        return content.getBytes(StandardCharsets.UTF_8);
    }

    /** A jar with exactly these entries, written in a stable order. */
    static void jar(Path file, Map<String, byte[]> entries) throws IOException {
        jar(file, null, entries);
    }

    /** {@link #jar(Path, Map)} with a manifest carrying the given main attributes ({@code Class-Path}, say). */
    static void jar(Path file, Map<String, String> manifestAttributes, Map<String, byte[]> entries)
            throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        JarOutputStream jar = manifestAttributes == null
                ? new JarOutputStream(bytes)
                : new JarOutputStream(bytes, manifest(manifestAttributes));
        try (jar) {
            for (Map.Entry<String, byte[]> entry : new TreeMap<>(entries).entrySet()) {
                jar.putNextEntry(new JarEntry(entry.getKey()));
                jar.write(entry.getValue());
                jar.closeEntry();
            }
        }
        Files.write(file, bytes.toByteArray());
    }

    private static Manifest manifest(Map<String, String> attributes) {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attributes.forEach((name, value) -> manifest.getMainAttributes().putValue(name, value));
        return manifest;
    }
}
