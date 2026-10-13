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
        void aCallSiteSlotAttributeSpelledWithADashIsRecognizedToo() {
            String out = render("slots/a-call-site-slot-attribute-spelled-with-a-dash-is-recognized-too");

            assertTrue(at(out, "id=\"head\"") < at(out, "id=\"body\""), out);
            assertFalse(out.contains("data-c-slot"), out);
        }

        @Test
        void aNestedSlotRendersItsOwnFallbackWhenNeitherItNorItsParentIsGiven() {
            String out = render("slots/a-nested-slot-renders-its-own-fallback-when-neither-it-nor-its-parent-is-given");

            assertTrue(out.contains("No data available."), out);
            assertTrue(out.contains("Try adjusting your filters."), out);
            assertTrue(out.contains("(icon)"), out);
        }

        @Test
        void fillingTheParentSlotSuppressesANestedMarkerInsideItsFallback() {
            String out = render("slots/filling-the-parent-slot-suppresses-a-nested-marker-inside-its-fallback");

            assertTrue(out.contains("Custom row"), out);
            assertFalse(out.contains("No data available."), out);
            assertFalse(out.contains("Try adjusting your filters."), out);
            assertFalse(out.contains("(icon)"), out);
        }

        @Test
        void theCallerCanTargetANestedSlotByNameWithoutFillingTheParent() {
            String out = render("slots/the-caller-can-target-a-nested-slot-by-name-without-filling-the-parent");

            assertTrue(out.contains("id=\"empty-message\""), out);
            assertTrue(out.contains("Nothing here"), out);
            assertFalse(out.contains("No data available."), out);
            assertFalse(out.contains("Try adjusting your filters."), out);
            assertTrue(out.contains("(icon)"), out);
        }

        @Test
        void contentForANestedSlotIsNotDroppedWhenItsSiblingNestedMarkersExist() {
            String out = render("slots/content-for-a-nested-slot-is-not-dropped-when-its-sibling-nested-markers-exist");

            assertTrue(out.contains("A checkmark"), out);
            assertFalse(out.contains("(icon)"), out);
            assertTrue(out.contains("No data available."), out);
            assertTrue(out.contains("Try adjusting your filters."), out);
        }

        @Test
        void aNestedSlotTwoLevelsDeepIsReachable() {
            String out = render("slots/a-nested-slot-two-levels-deep-is-reachable");

            assertTrue(out.contains("No data available."), out);
            assertTrue(out.contains(">Custom detail<"), out);
            assertFalse(out.contains("Try adjusting your filters."), out);
        }

        @Test
        void aNestedSlotsNameStillAppearsInTheDeclaresListOnATypo() {
            String message = failureOf("slots/a-nested-slots-name-still-appears-in-the-declares-list-on-a-typo");

            assertTrue(message.contains("slot \"empty-detial\""), message);
            assertTrue(message.contains("slot \"empty-detail\""), message);
        }

        @Test
        void hasSlotWorksForANestedSlotsNameRegardlessOfTemplateNestingDepth() {
            String out = render("slots/has-slot-works-for-a-nested-slots-name-regardless-of-template-nesting-depth");

            assertTrue(out.contains("data-has-detail=\"true\""), out);
        }

        @Test
        void cSlotOutsideAComponentTemplateFails() {
            String message = failureOf("slots/c-slot-outside-a-component-template-fails");

            assertTrue(message.contains("slot"), message);
        }

        @Test
        void contentGoesToEveryMarkerOfItsName() {
            String out = render("slots/content-goes-to-every-marker-of-its-name");

            assertEquals(2, count(out, "id=\"given\""), out);
            assertFalse(out.contains("fallback"), out);
        }

        @Test
        void aSlotMarkerInAConditionalBranchStillReceivesTheContent() {
            String out = render("slots/a-slot-marker-in-a-conditional-branch-still-receives-the-content");

            assertTrue(out.contains("<h2>given</h2>"), out);
            assertFalse(out.contains("fallback two"), out);
            assertTrue(out.contains("fallback rest"), out);
        }

        @Test
        void contentForASlotTheComponentDoesNotDeclareFails() {
            String message = failureOf("slots/content-for-a-slot-the-component-does-not-declare-fails");

            assertTrue(message.contains("slot \"headr\""), message);
            assertTrue(message.contains("slot \"header\""), message);
            assertTrue(message.contains("slot \"footer\""), message);
        }

        @Test
        void bodyContentForAComponentWithNoDefaultSlotFails() {
            String message = failureOf("slots/body-content-for-a-component-with-no-default-slot-fails");

            assertTrue(message.contains("a default slot"), message);
            assertTrue(message.contains("slot \"tag\""), message);
        }

        @Test
        void thIfOnAMarkerFails() {
            String message = failureOf("slots/th-if-on-a-marker-fails");

            assertTrue(message.contains("th:if"), message);
            assertTrue(message.contains("casescomponents/gated-marker.html"), message);
            assertTrue(message.contains("th:block"), message);
        }

        @Test
        void anAttributeOtherThanNameOnAMarkerFails() {
            String message = failureOf("slots/an-attribute-other-than-name-on-a-marker-fails");

            assertTrue(message.contains("class"), message);
            assertTrue(message.contains("casescomponents/classy-marker.html"), message);
        }

        @Test
        void aComputedSlotNameOnAMarkerFailsWithoutTheRenameHint() {
            String message = failureOf("slots/a-computed-slot-name-on-a-marker-fails");

            assertTrue(message.contains("th:name"), message);
            assertTrue(message.contains("th:block"), message);
            // The rename hint belongs to c:name alone: th:name is not a misspelling of it.
            assertFalse(message.contains("unprefixed"), message);
        }

        @Test
        void aPrefixedNameOnAMarkerFailsPointingAtThePlainOne() {
            String message = failureOf("slots/a-prefixed-name-on-a-marker-fails-pointing-at-the-plain-one");

            assertTrue(message.contains("c:name"), message);
            assertTrue(message.contains("unprefixed"), message);
        }
    }

    @Nested
    @DisplayName("component tag resolution")
    class ComponentResolution {

        @Test
        void anUnresolvedComponentTagFails() {
            String message = failureOf("components/an-unresolved-component-tag-fails");

            assertTrue(message.contains("c:no-such-thing"), message);
        }

        @Test
        void anUnresolvedTagInAFalseBranchIsNotChecked() {
            String out = render("components/an-unresolved-tag-in-a-false-branch-is-not-checked");

            assertTrue(out.contains("<div id=\"wrapper\">"), out);
            assertFalse(out.contains("no-such-thing"), out);
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
        void inlinedSlotContentReadsTheCallersThis() {
            String out = render("scope/inlined-slot-content-reads-the-callers-this");

            assertTrue(out.contains("written-by-inliner"), out);
            assertTrue(out.contains("data-title=\"inliner\""), out);
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

        @Test
        void anExplicitLevelOnOutlineItselfAlsoOverridesTheComputedNesting() {
            String out = render("context/an-explicit-level-on-outline-itself-also-overrides-the-computed-nesting");

            assertEquals("explicit", textOf(out, "heading-99"), out);
            assertEquals("computed from the explicit one", textOf(out, "heading-100"), out);
        }

        @Test
        void outlineLevelCompoundsThroughNestedSlots() {
            String out = render("context/outline-level-compounds-through-nested-slots");

            // heading-1: once directly in layout's slot, once inside the outline-agnostic panel — both
            // transparently seeing layout's own outline, not resetting or losing it through either slot hop.
            assertEquals(2, count(out, "id=\"heading-1\""), out);
            assertTrue(out.contains("direct in layout's slot"), out);
            assertTrue(out.contains("inside a panel that is inside layout's slot"), out);

            // heading-2: once for a fresh outline nested directly in layout's slot, once for a fresh outline
            // nested inside the panel — both computed off the same ambient layout outline either way.
            assertEquals(2, count(out, "id=\"heading-2\""), out);
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
        void restAttributesCanBeReadAsAMap() {
            String out = render("attributes/rest-attributes-can-be-read-as-a-map");

            assertTrue(out.contains("data-kept=a"), out);
            assertTrue(out.contains("hidden=b"), out);
            assertFalse(out.contains("title"), out);
            assertEquals(2, count(out, "<li"), out);
        }

        @Test
        void aClasslessComponentsBareHasSlotAndRestAttributesWork() {
            String out = render("attributes/a-classless-components-bare-hasslot-and-restattributes-work");

            assertTrue(out.contains(">true<"), out);
            assertTrue(out.contains("data-kept=a"), out);
            assertTrue(out.contains(">content<"), out);
        }

        @Test
        void thTextIsNotSpreadByCRest() {
            String out = render("attributes/th-text-is-not-spread-by-c-rest");

            assertTrue(out.contains("quoted=\"a\""), out);
            assertFalse(out.contains("text="), out);
        }

        @Test
        void aDeclaredPropIsExcludedEvenThoughItsRead() {
            String out = render("attributes/a-declared-prop-is-excluded-even-though-its-read");

            assertTrue(out.contains("class=\"btn-danger\""), out);
            assertTrue(out.contains("type=\"submit\""), out);
            assertTrue(out.contains("disabled=\"true\""), out);
            assertFalse(out.contains("variant"), out);
        }

        @Test
        void aRecordsComponentsAreItsProps() {
            String out = render("attributes/a-records-components-are-its-props");

            assertTrue(out.contains("class=\"alert alert-warning\""), out);
            assertTrue(out.contains("data-auto-hide=\"10\""), out);
            assertTrue(out.contains("id=\"quota-alert\""), out);
            assertTrue(out.contains("alert-close"), out);
            assertTrue(out.contains("Storage almost full."), out);
            assertFalse(out.contains("closable"), out);
            assertFalse(out.contains("level="), out);
            assertFalse(out.contains("auto-hide-seconds="), out);
        }

        @Test
        void aRecordsAbsentPropsDefaultByType() {
            String out = render("attributes/a-records-absent-props-default-by-type");

            assertTrue(out.contains("class=\"alert alert-info\""), out);
            assertFalse(out.contains("data-auto-hide"), out);
            assertFalse(out.contains("alert-close"), out);
            assertTrue(out.contains("Nothing to report."), out);
        }

        @Test
        void thAttributesOnAComponentTagBecomeProps() {
            String out = render("attributes/th-attributes-on-a-component-tag-become-props");

            assertTrue(out.contains("title=\"evaluated\""), out);
            assertTrue(out.contains("disabled=\"true\""), out);
            assertTrue(out.contains("plain=\"kept\""), out);
            assertFalse(out.contains("${"), out);
        }

        @Test
        void anUnsupportedThAttributeOnAComponentTagFails() {
            String message = failureOf("attributes/an-unsupported-th-attribute-on-a-component-tag-fails");

            assertTrue(message.contains("th:attr"), message);
            assertTrue(message.contains("<c:relay>"), message);
        }

        @Test
        void anOuterThClassappendMergesWithTheComponentsOwn() {
            String out = render("attributes/an-outer-th-classappend-merges-with-the-components-own");

            assertTrue(out.contains("class=\"btn-danger urgent\""), out);
        }

        @Test
        void anOuterThStyleappendMergesWithTheComponentsOwn() {
            String out = render("attributes/an-outer-th-styleappend-merges-with-the-components-own");

            assertTrue(out.contains("style=\"margin:0; color:red; font-weight:bold;\""), out);
        }

        @Test
        void aDeclaredClassPropKeepsTheCallersPlainClassOutOfTheClassappendMerge() {
            String out = render("attributes/a-declared-class-prop-keeps-the-callers-plain-class-out-of-the-classappend-merge");

            assertTrue(out.contains("class=\"base component urgent\""), out);
            assertFalse(out.contains("ignored"), out);
        }

        @Test
        void anOuterThAttrappendOnAComponentTagFails() {
            String message = failureOf("attributes/an-outer-th-attrappend-on-a-component-tag-fails");

            assertTrue(message.contains("th:attrappend"), message);
            assertTrue(message.contains("<c:attrappend-demo>"), message);
        }

        @Test
        void anOuterThAttrprependOnAComponentTagFails() {
            String message = failureOf("attributes/an-outer-th-attrprepend-on-a-component-tag-fails");

            assertTrue(message.contains("th:attrprepend"), message);
            assertTrue(message.contains("<c:attrprepend-demo>"), message);
        }

        @Test
        void cRestOutsideAComponentFails() {
            String message = failureOf("attributes/c-rest-outside-a-component-fails");

            assertTrue(message.contains("c:rest"), message);
            assertTrue(message.contains("no component is rendering"), message);
        }

        @Test
        void aCPrefixedAttributeOnAComponentTagAlwaysFails() {
            String message = failureOf("attributes/a-c-prefixed-attribute-on-a-component-tag-always-fails");

            assertTrue(message.contains("c:title"), message);
            assertTrue(message.contains("<c:panel>"), message);
        }
    }

    @Nested
    @DisplayName("components from more than one source")
    class Modules {

        @Test
        void aLibraryComponentUsesItsOwnComponents() {
            String out = render("modules/a-library-component-uses-its-own-components");

            assertTrue(at(out, "id=\"ds-card\"") < at(out, "id=\"ds-badge\""), out);
            assertEquals("from the library", textOf(out, "ds-badge"), out);
        }

        @Test
        void componentsFromTwoSourcesNestInEachOther() {
            String out = render("modules/components-from-two-sources-nest-in-each-other");

            // An application component inside the library's card…
            assertTrue(at(out, "data-title=\"outer card\"") < at(out, "data-title=\"inner panel\""), out);
            assertTrue(at(out, "data-title=\"inner panel\"") < at(out, "id=\"app-in-lib\""), out);
            // …and the library's card inside an application component.
            assertTrue(at(out, "data-title=\"outer panel\"") < at(out, "data-title=\"inner card\""), out);
            assertTrue(at(out, "data-title=\"inner card\"") < at(out, "id=\"lib-in-app\""), out);
        }

        @Test
        void thisDoesNotCrossAModuleBoundary() {
            String out = render("modules/this-does-not-cross-a-module-boundary");

            assertEquals("NONE", textOf(out, "at-page"), out);
            assertEquals("NONE", textOf(out, "in-lib-slot"), out);
            // The library's own markup still reads its own instance, on both sides of its internal call.
            assertTrue(out.contains("data-title=\"receiver\""), out);
            assertEquals("receiver", textOf(out, "ds-badge"), out);
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
