package blynx.thymeleaf.compositiondialect;

import static blynx.thymeleaf.compositiondialect.CaseRenderer.at;
import static blynx.thymeleaf.compositiondialect.CaseRenderer.count;
import static blynx.thymeleaf.compositiondialect.CaseRenderer.failureOf;
import static blynx.thymeleaf.compositiondialect.CaseRenderer.render;
import static blynx.thymeleaf.compositiondialect.CaseRenderer.textOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The behavioural spec, one case per template under {@code src/test/resources/templates/cases}. Each case
 * file explains its own idea; the test here only says what the outcome must be. Read the template first —
 * that is the point of the arrangement.
 *
 * <p>A case that keeps its rendered result beside it as {@code <the-case>.expected.html} is compared
 * against that file too, by {@code render} itself, so the whole output is checked and not only the part a
 * test names. {@link #everyExpectedOutputMatchesItsCase()} renders every one of them, so none can sit
 * beside a case nobody renders.
 */
@DisplayName("cases")
class CasesTest {

    @Nested
    @DisplayName("slots")
    class Slots {

        @Test
        void defaultSlot() {
            String out = render("slots/default-slot");

            assertTrue(at(out, "id=\"panel\"") < at(out, "id=\"given\""), out);
        }

        @Test
        void namedSlots() {
            String out = render("slots/named-slots");

            // The layout's order, not the order the page wrote them in.
            assertTrue(at(out, "id=\"head\"") < at(out, "id=\"body\""), out);
            assertTrue(at(out, "id=\"body\"") < at(out, "id=\"foot\""), out);
            assertFalse(out.contains("c:slot"), out);
        }

        @Test
        void anAbsentNamedSlotCanBeDetected() {
            String out = render("slots/an-absent-named-slot-can-be-detected");

            assertTrue(out.contains("id=\"body\""), out);
            assertFalse(out.contains("layout-footer"), out);
        }

        @Test
        void nonContiguousContentForOneSlot() {
            String out = render("slots/non-contiguous-content-for-one-slot");

            assertTrue(at(out, "id=\"head-1\"") < at(out, "id=\"head-2\""), out);
            assertTrue(at(out, "id=\"head-2\"") < at(out, "id=\"body\""), out);
        }

        @Test
        void cSlotDeeperThanADirectChildIsInert() {
            String out = render("slots/c-slot-deeper-than-a-direct-child-is-inert");

            assertTrue(out.contains("c:slot=\"header\""), out);
        }

        @Test
        void aNestedComponentClaimsItsOwnSlots() {
            String out = render("slots/a-nested-component-claims-its-own-slots");

            assertTrue(at(out, "id=\"outer-head\"") < at(out, "id=\"inner-head\""), out);
            assertTrue(at(out, "id=\"inner-head\"") < at(out, "id=\"inner-body\""), out);
        }

        @Test
        void contentHandedOnThroughTwoComponents() {
            String out = render("slots/content-handed-on-through-two-components");

            assertTrue(at(out, "id=\"handoff\"") < at(out, "id=\"panel\""), out);
            assertTrue(at(out, "id=\"panel\"") < at(out, "id=\"travelled\""), out);
        }

        @Test
        void cSlotOnAVoidElement() {
            String out = render("slots/c-slot-on-a-void-element");

            assertTrue(at(out, "id=\"picture\"") < at(out, "id=\"body\""), out);
            assertFalse(out.contains("c:slot"), out);
        }

        @Test
        void thAttributesOnSlotContent() {
            String out = render("slots/th-attributes-on-slot-content");

            assertEquals(1, count(out, "<p>one</p>"), out);
            assertEquals(1, count(out, "<p>two</p>"), out);
            assertFalse(out.contains("dropped"), out);
        }

        @Test
        void thTextOnTheComponentTagFillsTheDefaultSlot() {
            assertTrue(render("slots/th-text-on-the-component-tag-fills-the-default-slot").contains(">evaluated<"));
        }

        @Test
        void thTextOverridesExplicitDefaultSlotChildren() {
            String out = render("slots/th-text-overrides-explicit-default-slot-children");

            assertTrue(out.contains(">evaluated<"), out);
            assertFalse(out.contains("fallback"), out);
        }

        @Test
        void thUtextOnTheComponentTagFillsTheDefaultSlotUnescaped() {
            assertTrue(render("slots/th-utext-on-the-component-tag-fills-the-default-slot-unescaped")
                    .contains("<em>raw</em>"));
        }

        @Test
        void aSlotsFallbackContentRendersWhenNothingIsPassed() {
            String out = render("slots/a-slots-fallback-content-renders-when-nothing-is-passed");

            assertTrue(out.contains(">Untitled<"), out);
            assertTrue(out.contains(">Empty<"), out);
            assertTrue(out.contains("data-has-body=\"false\""), out);
            assertTrue(out.contains("<footer id=\"card-footer\"></footer>"), out);
        }

        @Test
        void aSlotsFallbackContentIsSuppressedWhenContentIsGiven() {
            String out = render("slots/a-slots-fallback-content-is-suppressed-when-content-is-given");

            assertFalse(out.contains("Untitled"), out);
            assertFalse(out.contains("Empty"), out);
            assertTrue(out.contains("Given header"), out);
            assertTrue(out.contains("Given body"), out);
            assertTrue(out.contains("data-has-body=\"true\""), out);
        }

        @Test
        void whitespaceOnlyDefaultSlotContentDoesNotSuppressTheFallback() {
            String out = render("slots/whitespace-only-default-slot-content-does-not-suppress-the-fallback");

            assertTrue(out.contains(">Empty<"), out);
            assertTrue(out.contains("data-has-body=\"false\""), out);
            assertTrue(out.contains("Given header"), out);
        }

        @Test
        void aMarkerSpelledWithADashIsRecognizedToo() {
            String out = render("slots/a-marker-spelled-with-a-dash-is-recognized-too");

            assertTrue(out.contains("given"), out);
            assertFalse(out.contains("c-slot"), out);
        }

        @Test
        void aNestedCSlotInsideAFallbackIsRejected() {
            String message = failureOf("slots/a-nested-c-slot-inside-a-fallback-is-rejected");

            assertTrue(message.contains("fallback"), message);
        }

        @Test
        void cSlotOutsideAComponentTemplateFails() {
            String message = failureOf("slots/c-slot-outside-a-component-template-fails");

            assertTrue(message.contains("slot"), message);
        }
    }

    @Nested
    @DisplayName("scope of ${this}")
    class ThisScope {

        @Test
        void aComponentReadsItsOwnInstance() {
            assertTrue(render("scope/a-component-reads-its-own-instance").contains("data-title=\"mine\""));
        }

        @Test
        void atTemplateLevelThereIsNoThis() {
            assertEquals("NONE", textOf(render("scope/at-template-level-there-is-no-this"), "at-page"));
        }

        @Test
        void slotContentDoesNotReadTheReceivingComponent() {
            String out = render("scope/slot-content-does-not-read-the-receiving-component");

            assertEquals("NONE", textOf(out, "at-page"), out);
            assertEquals("NONE", textOf(out, "in-slot"), out);
            assertEquals("NONE", textOf(out, "deeper"), out);
            // The components themselves still read their own instances.
            assertTrue(out.contains("data-title=\"receiver\""), out);
            assertTrue(out.contains("data-title=\"inner\""), out);
        }

        @Test
        void aComponentsOwnMarkupKeepsItsInstance() {
            assertTrue(render("scope/a-components-own-markup-keeps-its-instance")
                    .contains("data-title=\"handoff\""));
        }

        @Test
        void anInsertedFragmentReadsWhereItIsInserted() {
            String out = render("scope/an-inserted-fragment-reads-where-it-is-inserted");

            assertEquals("NONE", textOf(out, "at-page"), out);
            assertEquals("NONE", textOf(out, "in-slot"), out);
            assertEquals("inserting", textOf(out, "in-inserter"), out);
        }

        @Test
        void aFallbackReadsTheComponentsOwnThis() {
            assertEquals("fallback-scope", textOf(render("scope/a-fallback-reads-the-components-own-this"),
                    "in-fallback"));
        }
    }

    @Nested
    @DisplayName("control flow")
    class ControlFlow {

        @Test
        void thIfSuppressesAComponent() {
            String out = render("control-flow/th-if-suppresses-a-component");

            assertEquals(1, count(out, "id=\"panel\""), out);
            assertTrue(out.contains("data-title=\"kept\""), out);
            assertFalse(out.contains("dropped"), out);
        }

        @Test
        void thEachRepeatsAComponent() {
            String out = render("control-flow/th-each-repeats-a-component");

            assertEquals(2, count(out, "id=\"panel\""), out);
            assertTrue(out.contains("data-title=\"one\""), out);
            assertTrue(out.contains("data-title=\"two\""), out);
        }
    }

    @Nested
    @DisplayName("context published by a component")
    class PublishedContext {

        @Test
        void aComponentPublishesAValueForItsContent() {
            String out = render("context/a-component-publishes-a-value-for-its-content");

            assertEquals(2, count(out, "id=\"heading-1\""), out);
            assertEquals(1, count(out, "id=\"heading-2\""), out);
        }

        @Test
        void anExplicitAttributeOverridesThePublishedValue() {
            String out = render("context/an-explicit-attribute-overrides-the-published-value");

            assertEquals("inherited", textOf(out, "heading-1"), out);
            assertEquals("explicit", textOf(out, "heading-5"), out);
        }

        @Test
        void publishedValuesAndThisScopeInOppositeDirections() {
            String out = render("context/published-values-and-this-scope-in-opposite-directions");

            assertTrue(out.contains("id=\"heading-2\""), out);
            assertEquals("NONE", textOf(out, "written-by"), out);
        }
    }

    @Nested
    @DisplayName("attributes")
    class Attributes {

        @Test
        void plainAndExpressionAttributes() {
            String out = render("attributes/plain-and-expression-attributes");

            assertTrue(out.contains("data-title=\"literal\""), out);
            assertTrue(out.contains("data-title=\"evaluated\""), out);
        }

        @Test
        void unreadAttributesAreSpreadWithCRest() {
            String out = render("attributes/unread-attributes-are-spread-with-c-rest");

            assertTrue(out.contains("quoted=\"a\""), out);
            assertTrue(out.contains("unquoted=\"b\""), out);
            assertTrue(out.contains("data-dashed=\"c\""), out);
            assertTrue(out.contains("valueless"), out);
        }

        @Test
        void thTextIsNotSpreadByCRest() {
            String out = render("attributes/th-text-is-not-spread-by-c-rest");

            assertTrue(out.contains("quoted=\"a\""), out);
            assertFalse(out.contains("text="), out);
        }
    }

    /**
     * Not a case: renders every case that keeps an {@code .expected.html}, which is what compares the two.
     * The tests above each render the cases they are about, so this adds nothing for those — it is here so
     * that an expected output cannot be left beside a case no test renders, or beside one renamed away, and
     * sit there being believed.
     */
    @Test
    @DisplayName("every expected output matches its case")
    void everyExpectedOutputMatchesItsCase() throws Exception {
        String suffix = CaseRenderer.EXPECTED_SUFFIX + ".html";
        Path cases = Path.of(Objects.requireNonNull(
                getClass().getClassLoader().getResource("templates/cases")).toURI());

        List<String> casePaths;
        try (var files = Files.walk(cases)) {
            casePaths = files
                    .filter(file -> file.getFileName().toString().endsWith(suffix))
                    .map(file -> cases.relativize(file).toString().replace(java.io.File.separatorChar, '/'))
                    .map(name -> name.substring(0, name.length() - suffix.length()))
                    .sorted()
                    .toList();
        }

        assertFalse(casePaths.isEmpty(), "no expected output found under " + cases);
        for (String casePath : casePaths) {
            assertTrue(Files.exists(cases.resolve(casePath + ".html")),
                    "expected output with no case beside it: " + casePath + suffix);
            try {
                render(casePath);
            } catch (AssertionError comparisonFailed) {
                throw comparisonFailed;
            } catch (Exception rendersNothing) {
                fail(casePath + " does not render, so it should keep no expected output", rendersNothing);
            }
        }
    }
}
