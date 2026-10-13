package blynx.thymeleaf.compositiondialect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.tools.ToolProvider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Spring Boot DevTools loads the application's own classes in a child "restart" class loader and leaves
 * library jars — this dialect among them — in the parent. A component's {@code impl} is then only visible
 * through the thread's context class loader, never through the dialect's own. Reproduced here by compiling
 * a component into a directory only a child loader knows about.
 */
class ComponentRegistryClassLoaderTest {

    private static final String IMPL = "devtools.Greeter";

    @Test
    void anImplOnlyTheContextClassLoaderCanSeeIsResolved(@TempDir Path dir) throws Exception {
        compileComponentInto(dir);
        ClasspathFixtures.write(dir.resolve("templates/comps/greeter.html"),
                "<c:component impl=\"" + IMPL + "\"/>\n<div><c:slot/></div>\n");
        URLClassLoader restart = new URLClassLoader(
                new URL[] {dir.toUri().toURL()}, ComponentRegistry.class.getClassLoader());
        // The premise: the dialect's own loader cannot see the class at all.
        assertThrows(ClassNotFoundException.class, () -> Class.forName(IMPL));

        Thread thread = Thread.currentThread();
        ClassLoader before = thread.getContextClassLoader();
        thread.setContextClassLoader(restart);
        try {
            ComponentRegistry registry = ComponentRegistry.scan(
                    List.of(new ComponentSource("comps")), "c", List.of(dir));

            Class<?> resolved = registry.findByTagName("c", "greeter").orElseThrow().componentClass().orElseThrow();
            assertEquals(IMPL, resolved.getName());
            assertSame(restart, resolved.getClassLoader());
        } finally {
            thread.setContextClassLoader(before);
            restart.close();
        }
    }

    private static void compileComponentInto(Path dir) throws IOException {
        Path source = dir.resolve("src/devtools/Greeter.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
                package devtools;

                import blynx.thymeleaf.compositiondialect.CompositionComponent;
                import blynx.thymeleaf.compositiondialect.CompositionComponentContext;

                public record Greeter(CompositionComponentContext context) implements CompositionComponent {
                }
                """);
        int status = ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-d", dir.toString(),
                "-classpath", System.getProperty("java.class.path"),
                source.toString());
        assertEquals(0, status, "compiling the fixture component");
    }
}
