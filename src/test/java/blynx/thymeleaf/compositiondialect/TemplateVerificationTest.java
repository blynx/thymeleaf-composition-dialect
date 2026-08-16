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
 * Checking up front that every component has a template. A missing one is otherwise found only when that
 * one component first renders — which may be a long way from startup, and is per component, so a page
 * nobody opened during testing is where it surfaces.
 *
 * <p>Existence is all this settles. Whether a template parses, and whether its slot markers are
 * well-formed, still waits for the first render: that needs a template context, which only exists inside
 * one. See {@link ComponentRegistry#requireResolvableTemplates}.
 */
class TemplateVerificationTest {

    private static final String APP_PACKAGE = "blynx.thymeleaf.compositiondialect.casescomponents";
    private static final String APP_PATH = "casescomponents";
    private static final String MISSING_PACKAGE =
            "blynx.thymeleaf.compositiondialect.registryfixtures.missingtemplate";

    @Test
    void aComponentSetWhoseTemplatesAllExistPasses() {
        CompositionDialect dialect = new CompositionDialect(APP_PACKAGE, APP_PATH);

        assertEquals(List.of(), dialect.getRegistry()
                .unresolvableTemplates(engineWith(dialect, APP_PATH).getConfiguration()));
    }

    @Test
    void aComponentWithNoTemplateIsReportedByName() {
        CompositionDialect dialect = new CompositionDialect(MISSING_PACKAGE, APP_PATH);
        TemplateEngine engine = engineWith(dialect, APP_PATH);

        List<ComponentDescriptor> unresolvable =
                dialect.getRegistry().unresolvableTemplates(engine.getConfiguration());

        assertEquals(1, unresolvable.size(), unresolvable.toString());
        assertEquals("ghost", unresolvable.getFirst().tagName());
    }

    @Test
    void requiringResolvableTemplatesFailsWithTheClassAndThePathItLookedFor() {
        CompositionDialect dialect = new CompositionDialect(MISSING_PACKAGE, APP_PATH);
        TemplateEngine engine = engineWith(dialect, APP_PATH);

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> dialect.getRegistry().requireResolvableTemplates(engine.getConfiguration()));

        assertTrue(thrown.getMessage().contains("<c:ghost>"), thrown.getMessage());
        assertTrue(thrown.getMessage().contains("Ghost"), thrown.getMessage());
        assertTrue(thrown.getMessage().contains(APP_PATH + "/ghost.html"), thrown.getMessage());
    }

    /**
     * A path no resolver claims counts as missing too, not as resolvable-by-someone-else — which is what a
     * wrong {@code templatesPath} looks like from here, and the mistake most worth catching at startup.
     */
    @Test
    void aTemplatesPathNoResolverEvenClaimsCountsAsMissing() {
        CompositionDialect dialect = new CompositionDialect(APP_PACKAGE, "nowhere");
        TemplateEngine engine = engineWith(dialect, APP_PATH);

        assertEquals(dialect.getRegistry().components().size(),
                dialect.getRegistry().unresolvableTemplates(engine.getConfiguration()).size());
    }

    private static TemplateEngine engineWith(CompositionDialect dialect, String resolvablePath) {
        ClassLoaderTemplateResolver templates = new ClassLoaderTemplateResolver();
        templates.setPrefix("templates/");
        templates.setSuffix(".html");
        templates.setResolvablePatterns(Set.of(resolvablePath + "/**"));
        templates.setCharacterEncoding("UTF-8");

        TemplateEngine engine = new TemplateEngine();
        engine.addTemplateResolver(templates);
        engine.addDialect(dialect);
        return engine;
    }
}
