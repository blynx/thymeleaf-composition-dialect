package blynx.thymeleaf.compositiondialect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/**
 * The Spring Boot path, which nothing else in the suite runs. Property names reach
 * {@link CompositionDialectProperties} by relaxed binding rather than by any compiled reference, so a
 * wrong or renamed one binds to nothing and the application starts with a silently empty setting — no
 * compile error, no exception. These render nothing; they only assert what the auto-configuration built.
 */
class CompositionDialectAutoConfigurationTest {

    private static final String APP_PATH = "testcomponents";
    private static final String APP_PACKAGE = "blynx.thymeleaf.compositiondialect.testcomponents";
    private static final String LIBRARY_PATH = "libcomponents";
    private static final String LIBRARY_PACKAGE = "blynx.thymeleaf.compositiondialect.libcomponents";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CompositionDialectAutoConfiguration.class));

    /** Tag names the dialect ended up registering, which is the observable result of the whole setup. */
    private static List<String> tagNames(CompositionDialect dialect) {
        return dialect.getRegistry().components().stream().map(ComponentDescriptor::tagName).toList();
    }

    private static String templatePathOf(CompositionDialect dialect, String tagName) {
        return dialect.getRegistry().findByTagName(dialect.getPrefix(), tagName).orElseThrow().templatePath();
    }

    @Test
    void theTemplatesPathIsBoundAndScanned() {
        runner.withPropertyValues(
                        "thymeleaf.composition.templates-path=" + APP_PATH,
                        "thymeleaf.composition.default-package=" + APP_PACKAGE)
                .run(context -> {
                    CompositionDialect dialect = context.getBean(CompositionDialect.class);

                    assertEquals(List.of("card", "heading", "label", "magic-headings", "plain", "wrapper"),
                            tagNames(dialect));
                    assertEquals(APP_PATH + "/card", templatePathOf(dialect, "card"));
                });
    }

    @Test
    void thePrefixDefaultsToCAndIsBoundWhenSet() {
        runner.withPropertyValues("thymeleaf.composition.templates-path=" + APP_PATH,
                        "thymeleaf.composition.default-package=" + APP_PACKAGE)
                .run(context -> assertEquals("c", context.getBean(CompositionDialect.class).getPrefix()));

        // <c:component> is itself prefix-bound dialect grammar, so a custom prefix needs templates that
        // spell their declaration with it — prefixcomponents already declares <x:component> for exactly
        // this reason (see MultiPrefixTest).
        runner.withPropertyValues(
                        "thymeleaf.composition.templates-path=prefixcomponents",
                        "thymeleaf.composition.default-package=blynx.thymeleaf.compositiondialect.prefixcomponents",
                        "thymeleaf.composition.prefix=x")
                .run(context -> assertEquals("x", context.getBean(CompositionDialect.class).getPrefix()));
    }

    @Test
    void aLibrarysComponentSourceBeanIsScannedAlongsideTheApplicationsOwn() {
        runner.withUserConfiguration(LibraryConfiguration.class)
                .withPropertyValues(
                        "thymeleaf.composition.templates-path=" + APP_PATH,
                        "thymeleaf.composition.default-package=" + APP_PACKAGE)
                .run(context -> {
                    CompositionDialect dialect = context.getBean(CompositionDialect.class);

                    assertTrue(tagNames(dialect).contains("card"), "the application's own");
                    assertTrue(tagNames(dialect).contains("ds-card"), "the library's");
                    assertEquals("libcomponents/ds-card", templatePathOf(dialect, "ds-card"));
                });
    }

    /** Importing a design system and writing no components of your own is an ordinary way to use this. */
    @Test
    void anApplicationWithNoTemplatesPathStillGetsTheLibrarys() {
        runner.withUserConfiguration(LibraryConfiguration.class)
                .run(context -> {
                    CompositionDialect dialect = context.getBean(CompositionDialect.class);

                    assertEquals(List.of("ds-badge", "ds-card"), tagNames(dialect));
                });
    }

    @Test
    void aBlankTemplatesPathCountsAsAbsentRatherThanAsAPathToScan() {
        runner.withUserConfiguration(LibraryConfiguration.class)
                .withPropertyValues("thymeleaf.composition.templates-path=")
                .run(context -> assertEquals(List.of("ds-badge", "ds-card"),
                        tagNames(context.getBean(CompositionDialect.class))));
    }

    @Test
    void noTemplatesPathAndNoLibraryFailsWithAnActionableMessage() {
        runner.run(context -> {
            assertNotNull(context.getStartupFailure());

            String message = String.valueOf(rootCauseOf(context.getStartupFailure()).getMessage());
            assertTrue(message.contains("no components to scan"), message);
            assertTrue(message.contains("thymeleaf.composition.templates-path"), message);
            assertTrue(message.contains("ComponentSource"), message);
        });
    }

    @Test
    void anApplicationsOwnDialectBeanBacksTheAutoConfigurationOff() {
        runner.withUserConfiguration(OwnDialectConfiguration.class)
                .withPropertyValues(
                        "thymeleaf.composition.templates-path=" + APP_PATH,
                        "thymeleaf.composition.default-package=" + APP_PACKAGE)
                .run(context -> {
                    CompositionDialect dialect = context.getBean(CompositionDialect.class);

                    // The hand-built one, scanning only the library — not the property's own path.
                    assertEquals(List.of("ds-badge", "ds-card"), tagNames(dialect));
                    assertFalse(tagNames(dialect).contains("card"), "auto-configuration did not back off");
                });
    }

    /**
     * The template check runs as part of bringing the context up, so a component whose template the
     * application's own resolver chain cannot reach fails startup rather than the first request that needs
     * it. Here the engine's own resolver only accepts {@code libcomponents/**}, so every one of the
     * application's own components — found by the classpath walk, but not by this resolver — is missing
     * from its point of view.
     */
    @Test
    void aComponentTheResolverChainCannotReachFailsStartup() {
        runner.withUserConfiguration(TemplateEngineConfiguration.class)
                .withPropertyValues(
                        "thymeleaf.composition.templates-path=" + APP_PATH,
                        "thymeleaf.composition.default-package=" + APP_PACKAGE)
                .run(context -> {
                    assertNotNull(context.getStartupFailure());

                    String message = String.valueOf(rootCauseOf(context.getStartupFailure()).getMessage());
                    assertTrue(message.contains("no template found"), message);
                    assertTrue(message.contains("<c:card>"), message);
                });
    }

    @Test
    void templatesThatAllResolveStartCleanly() {
        runner.withUserConfiguration(LibraryConfiguration.class, TemplateEngineConfiguration.class)
                .run(context -> assertNull(context.getStartupFailure()));
    }

    @Test
    void theTemplateCheckCanBeTurnedOff() {
        runner.withUserConfiguration(TemplateEngineConfiguration.class)
                .withPropertyValues(
                        "thymeleaf.composition.templates-path=" + APP_PATH,
                        "thymeleaf.composition.default-package=" + APP_PACKAGE,
                        "thymeleaf.composition.verify-templates=false")
                .run(context -> assertNull(context.getStartupFailure()));
    }

    /** Every other test here runs without one, which is why the check has to tolerate its absence. */
    @Test
    void withNoTemplateEngineThereIsNothingToVerifyAgainst() {
        runner.withPropertyValues(
                        "thymeleaf.composition.templates-path=" + APP_PATH,
                        "thymeleaf.composition.default-package=" + APP_PACKAGE)
                .run(context -> assertNull(context.getStartupFailure()));
    }

    private static Throwable rootCauseOf(Throwable thrown) {
        Throwable root = thrown;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root;
    }

    /** Stands in for the engine Spring Boot's own Thymeleaf auto-configuration would have provided. */
    @Configuration(proxyBeanMethods = false)
    static class TemplateEngineConfiguration {

        @Bean
        TemplateEngine templateEngine() {
            ClassLoaderTemplateResolver templates = new ClassLoaderTemplateResolver();
            templates.setPrefix("templates/");
            templates.setSuffix(".html");
            templates.setResolvablePatterns(Set.of(LIBRARY_PATH + "/**"));
            templates.setCharacterEncoding("UTF-8");

            TemplateEngine engine = new TemplateEngine();
            engine.addTemplateResolver(templates);
            return engine;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class LibraryConfiguration {

        @Bean
        ComponentSource designSystemComponents() {
            return new ComponentSource(LIBRARY_PATH, LIBRARY_PACKAGE);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class OwnDialectConfiguration {

        @Bean
        CompositionDialect compositionDialect() {
            return new CompositionDialect(new ComponentSource(LIBRARY_PATH, LIBRARY_PACKAGE));
        }
    }
}
