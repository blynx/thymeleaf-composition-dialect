package blynx.thymeleaf.compositiondialect;

import static blynx.thymeleaf.compositiondialect.CaseRenderer.at;
import static blynx.thymeleaf.compositiondialect.CaseRenderer.textOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.templateresolver.StringTemplateResolver;

/**
 * Two {@link CompositionDialect}s registered at different prefixes — the multi-prefix configuration. It is
 * <em>not</em> the recommended way to keep two modules' components apart: one dialect over several
 * {@link ComponentSource}s is (see {@link CompositionDialectTest} and {@code docs/reference/libraries.md}).
 * This is the net under a configuration that works and would otherwise be pinned by nothing.
 *
 * <p>What the renders below establish, in one sentence: <strong>tag names are separate buckets, the render
 * context is one shared thing, and the slot grammar is the only part bound to a prefix.</strong>
 *
 * <ul>
 *   <li><em>Separate</em> — registry, collision check and fragment cache are per dialect instance, so
 *       {@code c:panel} and {@code x:box} never see each other, and one tag name claimed under both
 *       prefixes is not a collision.
 *   <li><em>Shared</em> — {@code ${this}}, the {@link ComponentFrame} stack and published context
 *       variables are Thymeleaf's own template variables under names that carry no prefix, so both
 *       dialects push onto <em>one</em> stack. That is not an oversight: it is what makes nesting across
 *       the boundary come out right, because {@code c:_caller} and {@code x:_caller} unwind the same
 *       stack and content handed across it still reads its own writer. Per-dialect frames would break it.
 *   <li><em>Prefix-bound</em> — {@code c:slot}, {@code c:name} and the call-site {@code c:slot} attribute
 *       resolve under the prefix of the dialect that <em>registered the component</em>. A component
 *       template therefore belongs to its dialect's prefix, and the two mismatch tests at the bottom are
 *       what that costs.
 * </ul>
 */
class MultiPrefixTest {

    private static final String APP_PACKAGE = "blynx.thymeleaf.compositiondialect.casescomponents";
    private static final String APP_PATH = "casescomponents";
    private static final String OTHER_PACKAGE = "blynx.thymeleaf.compositiondialect.prefixcomponents";
    private static final String OTHER_PATH = "prefixcomponents";

    private static final TemplateEngine ENGINE = buildEngine();

    @Test
    void componentsFromTwoPrefixesRenderSideBySide() {
        String result = render("<c:panel title=\"app\">A</c:panel><x:box title=\"other\">B</x:box>");

        assertTrue(result.contains("data-title=\"app\""), result);
        assertTrue(result.contains("data-title=\"other\""), result);
        assertTrue(at(result, "id=\"panel\"") < at(result, "id=\"x-box\""), result);
    }

    /** Named and default slots on both sides of the boundary, each claimed by its own dialect. */
    @Test
    void componentsFromTwoPrefixesNestInEachOther() {
        String result = render("<c:layout>"
                + "<b c:slot=\"header\" id=\"outer-head\">A</b>"
                + "<x:box title=\"nested\">"
                + "<b x:slot=\"header\" id=\"inner-head\">B</b><b id=\"inner-body\">C</b>"
                + "</x:box>"
                + "</c:layout>");

        assertTrue(at(result, "layout-header") < at(result, "outer-head"), result);
        assertTrue(at(result, "outer-head") < at(result, "layout-main"), result);
        assertTrue(at(result, "x-box-header") < at(result, "inner-head"), result);
        assertTrue(at(result, "inner-head") < at(result, "x-box-body"), result);
        assertTrue(at(result, "x-box-body") < at(result, "inner-body"), result);
        assertTrue(result.contains("data-title=\"nested\""), result);
        // Each dialect consumed its own markers; neither left the other's behind.
        assertFalse(result.contains("c:slot"), result);
        assertFalse(result.contains("x:slot"), result);
    }

    /**
     * A value one dialect's component publishes is found by another dialect's component through a third —
     * these are the template's variables, and the template has only one set of them.
     */
    @Test
    void theRenderContextIsSharedAcrossPrefixes() {
        String result = render("<c:outline><x:box><c:outline><c:heading>t</c:heading></c:outline></x:box></c:outline>");

        assertTrue(result.contains("id=\"heading-2\""), result);
        assertTrue(at(result, "id=\"x-box\"") < at(result, "id=\"heading-2\""), result);
    }

    /**
     * The frame stack is shared too, which is the point: {@code x:handoff}'s template writes markup into a
     * {@code c:panel}'s slot, and {@code c:_caller} unwinds exactly one level of the one stack — landing on
     * the {@code x} component that wrote the content, not on the {@code c} component receiving it.
     */
    @Test
    void slotContentHandedAcrossPrefixesStillReadsItsWriter() {
        String result = render("<x:handoff label=\"written-in-x\" />");

        assertEquals("written-in-x", textOf(result, "x-handoff-mark"), result);
        assertTrue(result.contains("data-title=\"from-x\""), result);
    }

    /**
     * {@code c:rest} inside an {@code x} component's template spreads the <em>x</em> component's unread
     * attributes. It reads the same unprefixed {@code this} everything else does, so a grammar attribute
     * means "the component currently rendering" rather than "a component of my prefix".
     */
    @Test
    void aGrammarAttributeActsOnWhicheverComponentIsRendering() {
        String result = render("<x:relay data-extra=\"E\" class=\"k\" />");

        assertTrue(result.contains("data-extra=\"E\""), result);
        assertTrue(result.contains("class=\"k\""), result);
        assertFalse(result.contains("c:rest"), result);
    }

    /**
     * A marker spelled with the wrong prefix is not silently emitted — the other dialect's own placement
     * processor catches it, with the template and position. (It <em>is</em> emitted verbatim if that other
     * prefix is not registered at all, but so is any unclaimed tag; that is the general missing-validation
     * gap rather than anything about prefixes.)
     */
    @Test
    void aSlotMarkerSpelledWithTheWrongPrefixFailsWhereItIsWritten() {
        // No content passed, deliberately: content would trip requireOnlyDeclaredSlots first — an
        // unrecognized marker leaves the component declaring no slots — and the marker itself is what this
        // is about. That error names the template too, so neither order leaves you guessing.
        Exception thrown = assertThrows(Exception.class, () -> render("<x:mismatched />"));

        String message = rootMessage(thrown);
        assertTrue(message.contains("<c:slot>"), message);
        assertTrue(message.contains("not recognized as a slot marker"), message);
        assertTrue(message.contains(OTHER_PATH + "/mismatched"), message);
    }

    /**
     * The cost of the prefix binding, stated plainly. Unlike the marker element above, a call-site
     * {@code slot} attribute under the wrong prefix has no catch-all to land in: the receiving component
     * looks for its own prefix, does not find it, and the content falls into the default slot with the
     * attribute still on it. Deliberately left as it is — a {@code c:slot} too deep to be consumed is
     * already inert-and-kept ({@code cases/slots/c-slot-deeper-than-a-direct-child-is-inert}), and
     * recognizing a foreign prefix would need a dialect to know about its siblings.
     */
    @Test
    void aSlotAttributeSpelledWithTheWrongPrefixIsIgnoredAndKept() {
        String result = render("<x:box>"
                + "<b c:slot=\"header\" id=\"meant-for-header\">H</b><b id=\"plain-body\">B</b>"
                + "</x:box>");

        assertTrue(result.contains("<header id=\"x-box-header\"></header>"), result);
        assertTrue(at(result, "x-box-body") < at(result, "meant-for-header"), result);
        assertTrue(at(result, "meant-for-header") < at(result, "plain-body"), result);
        assertTrue(result.contains("c:slot=\"header\""), result);
    }

    /**
     * Two prefixes are two namespaces, so the same tag name under each is not the clash
     * {@link ComponentRegistry#requireNoCollisions()} rejects — each dialect only ever sees its own.
     * Registry-level rather than rendered: one package's templates can only spell one prefix's markers.
     */
    @Test
    void aTagNameClaimedUnderTwoPrefixesIsNotACollision() {
        ComponentRegistry app = new CompositionDialect(APP_PACKAGE, APP_PATH, "App", "c").getRegistry();
        ComponentRegistry other = new CompositionDialect(APP_PACKAGE, APP_PATH, "Other", "x").getRegistry();

        assertTrue(app.findByTagName("c", "panel").isPresent());
        assertTrue(other.findByTagName("x", "panel").isPresent());
        assertTrue(app.findByTagName("x", "panel").isEmpty());
    }

    private static String render(String page) {
        return ENGINE.process(page, new Context());
    }

    private static String rootMessage(Throwable thrown) {
        Throwable root = thrown;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return String.valueOf(root.getMessage());
    }

    private static TemplateEngine buildEngine() {
        ClassLoaderTemplateResolver components = new ClassLoaderTemplateResolver();
        components.setOrder(1);
        components.setPrefix("templates/");
        components.setSuffix(".html");
        components.setResolvablePatterns(Set.of(APP_PATH + "/**", OTHER_PATH + "/**"));
        components.setCharacterEncoding("UTF-8");
        components.setCacheable(false);

        // Pages are written inline: each one is the whole of what its test is about.
        StringTemplateResolver pages = new StringTemplateResolver();
        pages.setOrder(2);
        pages.setCacheable(false);

        TemplateEngine engine = new TemplateEngine();
        engine.addTemplateResolver(components);
        engine.addTemplateResolver(pages);
        engine.addDialect(new CompositionDialect(APP_PACKAGE, APP_PATH, "App components", "c"));
        engine.addDialect(new CompositionDialect(OTHER_PACKAGE, OTHER_PATH, "Other components", "x"));
        return engine;
    }
}
