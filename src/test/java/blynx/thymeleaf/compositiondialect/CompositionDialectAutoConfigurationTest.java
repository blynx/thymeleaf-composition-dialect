package blynx.thymeleaf.compositiondialect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The Spring Boot path, which nothing else in the suite runs. Property names reach
 * {@link CompositionDialectProperties} by relaxed binding rather than by any compiled reference, so a
 * wrong or renamed one binds to nothing and the application starts with a silently empty setting — no
 * compile error, no exception. These render nothing; they only assert what the auto-configuration built.
 */
class CompositionDialectAutoConfigurationTest {

    private static final String APP_PACKAGE = "blynx.thymeleaf.compositiondialect.testcomponents";
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
    void theComponentPackageIsBoundAndScanned() {
        runner.withPropertyValues("thymeleaf.composition.component-package=" + APP_PACKAGE)
                .run(context -> {
                    CompositionDialect dialect = context.getBean(CompositionDialect.class);

                    assertEquals(List.of("card", "heading", "label", "magic-headings", "plain", "wrapper"),
                            tagNames(dialect));
                });
    }

    /**
     * The property this pins down was renamed from {@code components-path}. Nothing else would notice:
     * the templates path only shows up in the path a fragment is loaded from, and an unbound property
     * leaves it null, which is a legal value meaning "templates sit at the root".
     */
    @Test
    void theTemplatesPathIsBoundAndPrefixesEveryTemplatePath() {
        runner.withPropertyValues(
                        "thymeleaf.composition.component-package=" + APP_PACKAGE,
                        "thymeleaf.composition.templates-path=testcomponents")
                .run(context -> {
                    CompositionDialect dialect = context.getBean(CompositionDialect.class);

                    assertEquals("testcomponents/card", templatePathOf(dialect, "card"));
                });
    }

    @Test
    void anAbsentTemplatesPathLeavesTemplatesAtTheRoot() {
        runner.withPropertyValues("thymeleaf.composition.component-package=" + APP_PACKAGE)
                .run(context -> {
                    CompositionDialect dialect = context.getBean(CompositionDialect.class);

                    assertEquals("card", templatePathOf(dialect, "card"));
                });
    }

    @Test
    void thePrefixDefaultsToCAndIsBoundWhenSet() {
        runner.withPropertyValues("thymeleaf.composition.component-package=" + APP_PACKAGE)
                .run(context -> assertEquals("c", context.getBean(CompositionDialect.class).getPrefix()));

        runner.withPropertyValues(
                        "thymeleaf.composition.component-package=" + APP_PACKAGE,
                        "thymeleaf.composition.prefix=ui")
                .run(context -> assertEquals("ui", context.getBean(CompositionDialect.class).getPrefix()));
    }

    @Test
    void aLibrarysComponentSourceBeanIsScannedAlongsideTheApplicationsOwn() {
        runner.withUserConfiguration(LibraryConfiguration.class)
                .withPropertyValues("thymeleaf.composition.component-package=" + APP_PACKAGE)
                .run(context -> {
                    CompositionDialect dialect = context.getBean(CompositionDialect.class);

                    assertTrue(tagNames(dialect).contains("card"), "the application's own");
                    assertTrue(tagNames(dialect).contains("ds-card"), "the library's");
                    assertEquals("libcomponents/ds-card", templatePathOf(dialect, "ds-card"));
                });
    }

    /** Importing a design system and writing no components of your own is an ordinary way to use this. */
    @Test
    void anApplicationWithNoComponentPackageStillGetsTheLibrarys() {
        runner.withUserConfiguration(LibraryConfiguration.class)
                .run(context -> {
                    CompositionDialect dialect = context.getBean(CompositionDialect.class);

                    assertEquals(List.of("ds-badge", "ds-card"), tagNames(dialect));
                });
    }

    @Test
    void aBlankComponentPackageCountsAsAbsentRatherThanAsAPackageToScan() {
        runner.withUserConfiguration(LibraryConfiguration.class)
                .withPropertyValues("thymeleaf.composition.component-package=")
                .run(context -> assertEquals(List.of("ds-badge", "ds-card"),
                        tagNames(context.getBean(CompositionDialect.class))));
    }

    @Test
    void noComponentPackageAndNoLibraryFailsWithAnActionableMessage() {
        runner.run(context -> {
            assertNotNull(context.getStartupFailure());

            String message = String.valueOf(rootCauseOf(context.getStartupFailure()).getMessage());
            assertTrue(message.contains("no components to scan"), message);
            assertTrue(message.contains("thymeleaf.composition.component-package"), message);
            assertTrue(message.contains("ComponentSource"), message);
        });
    }

    @Test
    void anApplicationsOwnDialectBeanBacksTheAutoConfigurationOff() {
        runner.withUserConfiguration(OwnDialectConfiguration.class)
                .withPropertyValues("thymeleaf.composition.component-package=" + APP_PACKAGE)
                .run(context -> {
                    CompositionDialect dialect = context.getBean(CompositionDialect.class);

                    // The hand-built one, scanning only the library — not the property's package.
                    assertEquals(List.of("ds-badge", "ds-card"), tagNames(dialect));
                    assertFalse(tagNames(dialect).contains("card"), "auto-configuration did not back off");
                });
    }

    private static Throwable rootCauseOf(Throwable thrown) {
        Throwable root = thrown;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root;
    }

    @Configuration(proxyBeanMethods = false)
    static class LibraryConfiguration {

        @Bean
        ComponentSource designSystemComponents() {
            return new ComponentSource(LIBRARY_PACKAGE, "libcomponents");
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class OwnDialectConfiguration {

        @Bean
        CompositionDialect compositionDialect() {
            return new CompositionDialect(new ComponentSource(LIBRARY_PACKAGE, "libcomponents"));
        }
    }
}
