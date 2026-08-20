package blynx.thymeleaf.compositiondialect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/**
 * Checking up front that every component the classpath walk found is also reachable through the
 * application's <em>actual</em> Thymeleaf resolver chain. The two can disagree — a
 * {@code spring.thymeleaf.prefix} pointing somewhere the classpath walk cannot see, or a resolver's own
 * resolvable-pattern restriction leaving a discovered template unreachable — and that gap is otherwise
 * found only when that one component first renders, which may be a long way from startup.
 *
 * <p>Existence is all this settles. Whether a template parses, and whether its slot markers are
 * well-formed, still waits for the first render: that needs a template context, which only exists inside
 * one. See {@link ComponentRegistry#requireResolvableTemplates}.
 */
class TemplateVerificationTest {

    private static final String APP_PATH = "casescomponents";
    private static final String APP_PACKAGE = "blynx.thymeleaf.compositiondialect.casescomponents";
    private static final String OTHER_PATH = "testcomponents";
    private static final String OTHER_PACKAGE = "blynx.thymeleaf.compositiondialect.testcomponents";

    @Test
    void aComponentSetWhoseTemplatesAllExistPasses() {
        CompositionDialect dialect = new CompositionDialect(APP_PATH, APP_PACKAGE);

        assertEquals(List.of(), dialect.getRegistry()
                .unresolvableTemplates(engineResolving(APP_PATH).getConfiguration()));
    }

    /**
     * The classpath walk found {@code testcomponents/card.html} — it is really there — but the engine's
     * own resolver only accepts paths under {@code casescomponents/}, exactly what a
     * {@code spring.thymeleaf.prefix} or resolvable-pattern mismatch looks like from here.
     */
    @Test
    void aComponentTheResolverChainCannotReachIsReportedByTagName() {
        CompositionDialect dialect = new CompositionDialect(OTHER_PATH, OTHER_PACKAGE);
        TemplateEngine engine = engineResolving(APP_PATH);

        List<ComponentDescriptor> unresolvable =
                dialect.getRegistry().unresolvableTemplates(engine.getConfiguration());

        assertEquals(6, unresolvable.size(), unresolvable.toString());
        assertTrue(unresolvable.stream().anyMatch(d -> d.tagName().equals("card")), unresolvable.toString());
    }

    @Test
    void requiringResolvableTemplatesFailsNamingTheTagAndThePathItLookedFor() {
        CompositionDialect dialect = new CompositionDialect(OTHER_PATH, OTHER_PACKAGE);
        TemplateEngine engine = engineResolving(APP_PATH);

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> dialect.getRegistry().requireResolvableTemplates(engine.getConfiguration()));

        assertTrue(thrown.getMessage().contains("<c:card>"), thrown.getMessage());
        assertTrue(thrown.getMessage().contains(OTHER_PATH + "/card.html"), thrown.getMessage());
    }

    /**
     * A path no resolver even claims counts as missing too, not as resolvable-by-someone-else — which is
     * what a wrong {@code templatesPath}, or a resolver prefix pointing at the wrong root, looks like.
     */
    @Test
    void aTemplatesPathNoResolverEvenClaimsCountsAsMissing() {
        CompositionDialect dialect = new CompositionDialect(OTHER_PATH, OTHER_PACKAGE);
        TemplateEngine engine = engineResolving("nowhere");

        assertEquals(dialect.getRegistry().components().size(),
                dialect.getRegistry().unresolvableTemplates(engine.getConfiguration()).size());
    }

    private static TemplateEngine engineResolving(String resolvablePath) {
        ClassLoaderTemplateResolver templates = new ClassLoaderTemplateResolver();
        templates.setPrefix("templates/");
        templates.setSuffix(".html");
        templates.setResolvablePatterns(Set.of(resolvablePath + "/**"));
        templates.setCharacterEncoding("UTF-8");

        TemplateEngine engine = new TemplateEngine();
        engine.addTemplateResolver(templates);
        return engine;
    }
}
