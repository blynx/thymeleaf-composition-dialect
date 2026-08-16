package blynx.thymeleaf.compositiondialect;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.engine.AttributeName;
import org.thymeleaf.processor.element.IElementProcessor;
import org.thymeleaf.processor.element.MatchingAttributeName;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.templateresolver.FileTemplateResolver;
import org.thymeleaf.templateresolver.StringTemplateResolver;

class CompositionElementModelProcessorTest {

    private TemplateEngine engine;

    @BeforeEach
    void setUp() {
        ClassLoaderTemplateResolver componentResolver = new ClassLoaderTemplateResolver();
        componentResolver.setOrder(1);
        componentResolver.setPrefix("templates/");
        componentResolver.setSuffix(".html");
        componentResolver.setResolvablePatterns(Set.of("testcomponents/*"));
        componentResolver.setCharacterEncoding("UTF-8");

        StringTemplateResolver pageResolver = new StringTemplateResolver();
        pageResolver.setOrder(2);
        pageResolver.setCacheable(false);

        engine = new TemplateEngine();
        engine.addTemplateResolver(componentResolver);
        engine.addTemplateResolver(pageResolver);
        engine.addDialect(new CompositionDialect(
                "blynx.thymeleaf.compositiondialect.testcomponents",
                "testcomponents"));
    }

    @Test
    void plainComponentRendersItsTemplate() {
        String result = engine.process("<c:plain />", new Context());
        assertTrue(result.contains("plain content"));
    }

    @Test
    void defaultSlotContentIsInjectedIntoWrapper() {
        String result = engine.process("<c:wrapper><p id=\"inner\">content</p></c:wrapper>", new Context());
        assertTrue(result.contains("id=\"wrapper\""));
        assertTrue(result.contains("id=\"inner\""));
        assertTrue(result.indexOf("id=\"wrapper\"") < result.indexOf("id=\"inner\""));
    }

    @Test
    void namedSlotContentIsPlacedBeforeDefaultSlotContent() {
        String result = engine.process(
                "<c:card><span c:slot=\"header\" id=\"h\">Head</span><span id=\"b\">Body</span></c:card>",
                new Context());
        assertTrue(result.contains("id=\"h\""));
        assertTrue(result.contains("id=\"b\""));
        assertTrue(result.indexOf("id=\"h\"") < result.indexOf("id=\"b\""));
    }

    @Test
    void cSlotAttributeIsStrippedFromRenderedOutput() {
        String result = engine.process("<c:wrapper><p c:slot=\"\">text</p></c:wrapper>", new Context());
        assertFalse(result.contains("c:slot"));
    }

    @Test
    void absentNamedSlotHidesItsConditionalSection() {
        String result = engine.process("<c:card><p id=\"b\">Body only</p></c:card>", new Context());
        assertFalse(result.contains("card-header"));
        assertFalse(result.contains("card-footer"));
        assertTrue(result.contains("card-body"));
    }

    @Test
    void presentNamedSlotShowsItsConditionalSection() {
        String result = engine.process(
                "<c:card><span c:slot=\"header\">H</span><span c:slot=\"footer\">F</span></c:card>",
                new Context());
        assertTrue(result.contains("card-header"));
        assertTrue(result.contains("card-footer"));
    }

    @Test
    void plainAttributeIsPassedAsRawString() {
        String result = engine.process("<c:label text=\"hello\" />", new Context());
        assertTrue(result.contains("hello"));
    }

    @Test
    void expressionAttributeIsEvaluatedAgainstTemplateContext() {
        Context ctx = new Context();
        ctx.setVariable("msg", "evaluated");
        String result = engine.process("<c:label c:value=\"${msg}\" />", ctx);
        assertTrue(result.contains("evaluated"));
    }

    @Test
    void headingOutsideMagicHeadingsDefaultsToLevel1() {
        String result = engine.process("<c:heading>text</c:heading>", new Context());
        assertTrue(result.contains("id=\"heading-1\""));
    }

    @Test
    void headingInsideMagicHeadingsUsesInheritedLevel() {
        String result = engine.process(
                "<c:magic-headings><c:heading>text</c:heading></c:magic-headings>", new Context());
        assertTrue(result.contains("id=\"heading-1\""));
    }

    @Test
    void headingInsideNestedMagicHeadingsIncrementsLevel() {
        String result = engine.process(
                "<c:magic-headings><c:magic-headings><c:heading>text</c:heading></c:magic-headings></c:magic-headings>",
                new Context());
        assertTrue(result.contains("id=\"heading-2\""));
    }

    @Test
    void explicitLevelAttributeOverridesInheritedLevel() {
        String result = engine.process(
                "<c:magic-headings><c:heading c:level=\"${5}\">text</c:heading></c:magic-headings>",
                new Context());
        assertTrue(result.contains("id=\"heading-5\""));
    }

    @Test
    void slotAttributeOnStandaloneChildAssignsThatSlot() {
        String result = engine.process(
                "<c:card><img c:slot=\"header\" id=\"i\"><p id=\"b\">Body</p></c:card>",
                new Context());
        assertTrue(result.contains("card-header"));
        assertTrue(result.indexOf("id=\"i\"") < result.indexOf("card-body"));
        assertTrue(result.indexOf("card-body") < result.indexOf("id=\"b\""));
        assertFalse(result.contains("c:slot"));
    }

    @Test
    void nonContiguousContentForSameSlotConcatenatesInDocumentOrder() {
        String result = engine.process(
                "<c:card><span c:slot=\"header\" id=\"h1\">A</span><span id=\"b1\">B</span>"
                        + "<span c:slot=\"header\" id=\"h2\">C</span></c:card>",
                new Context());
        assertTrue(result.indexOf("id=\"h1\"") < result.indexOf("id=\"h2\""));
        assertTrue(result.indexOf("id=\"h2\"") < result.indexOf("card-body"));
        assertTrue(result.indexOf("card-body") < result.indexOf("id=\"b1\""));
    }

    @Test
    void nestedComponentHandlesItsOwnSlotAssignments() {
        String result = engine.process(
                "<c:card><span c:slot=\"header\" id=\"oh\">outer</span>"
                        + "<c:card><span c:slot=\"header\" id=\"ih\">inner</span><span id=\"ib\">body</span></c:card>"
                        + "</c:card>",
                new Context());
        int outerBody = result.indexOf("card-body");
        assertTrue(result.indexOf("id=\"oh\"") < outerBody);
        int innerHeader = result.indexOf("card-header", outerBody);
        assertTrue(innerHeader > 0);
        assertTrue(result.indexOf("id=\"ih\"") > innerHeader);
        assertTrue(result.indexOf("id=\"ih\"") < result.indexOf("id=\"ib\""));
        assertFalse(result.contains("c:slot"));
    }

    @Test
    void slotAttributeDeeperThanDirectChildrenIsInertAndKept() {
        String result = engine.process(
                "<c:wrapper><div><span c:slot=\"header\" id=\"s\">deep</span></div></c:wrapper>",
                new Context());
        assertTrue(result.indexOf("id=\"wrapper\"") < result.indexOf("id=\"s\""));
        assertTrue(result.contains("c:slot=\"header\""));
    }

    @Test
    void nonCacheableComponentTemplateIsReloadedOnChange(@TempDir Path templateDir) throws IOException {
        TemplateEngine fileEngine = fileBasedEngine(templateDir, false);
        writeWrapperTemplate(templateDir, "v1");

        String first = fileEngine.process("<c:wrapper>x</c:wrapper>", new Context());
        assertTrue(first.contains("id=\"v1\""));

        writeWrapperTemplate(templateDir, "v2");
        String second = fileEngine.process("<c:wrapper>x</c:wrapper>", new Context());
        assertTrue(second.contains("id=\"v2\""));
    }

    @Test
    void cacheableComponentTemplateStaysCached(@TempDir Path templateDir) throws IOException {
        TemplateEngine fileEngine = fileBasedEngine(templateDir, true);
        writeWrapperTemplate(templateDir, "v1");

        String first = fileEngine.process("<c:wrapper>x</c:wrapper>", new Context());
        assertTrue(first.contains("id=\"v1\""));

        writeWrapperTemplate(templateDir, "v2");
        String second = fileEngine.process("<c:wrapper>x</c:wrapper>", new Context());
        assertTrue(second.contains("id=\"v1\""));
    }

    private TemplateEngine fileBasedEngine(Path templateDir, boolean cacheable) {
        FileTemplateResolver componentResolver = new FileTemplateResolver();
        componentResolver.setOrder(1);
        componentResolver.setPrefix(templateDir + "/");
        componentResolver.setSuffix(".html");
        componentResolver.setResolvablePatterns(Set.of("testcomponents/*"));
        componentResolver.setCharacterEncoding("UTF-8");
        componentResolver.setCacheable(cacheable);

        StringTemplateResolver pageResolver = new StringTemplateResolver();
        pageResolver.setOrder(2);
        pageResolver.setCacheable(false);

        TemplateEngine fileEngine = new TemplateEngine();
        fileEngine.addTemplateResolver(componentResolver);
        fileEngine.addTemplateResolver(pageResolver);
        fileEngine.addDialect(new CompositionDialect(
                "blynx.thymeleaf.compositiondialect.testcomponents",
                "testcomponents"));
        return fileEngine;
    }

    private void writeWrapperTemplate(Path templateDir, String marker) throws IOException {
        Files.createDirectories(templateDir.resolve("testcomponents"));
        Files.writeString(templateDir.resolve("testcomponents/wrapper.html"),
                "<div id=\"" + marker + "\"><c:slot /></div>");
    }

    /**
     * The two assumptions {@link CompositionElementModelProcessor#UNSUPPORTED_STANDARD_ATTRIBUTES} rests on,
     * checked against the standard dialect itself rather than believed: which {@code th:} attributes exist,
     * and which of them run before this processor replaces the tag. A Thymeleaf upgrade that renames one or
     * moves its precedence across ours would otherwise change what a component tag silently accepts.
     */
    @Nested
    @DisplayName("contract with the standard dialect")
    class StandardAttributeContract {

        /** Registered by the Spring dialect, so absent from the plain engine these tests build. */
        private static final Set<String> SPRING_ONLY = Set.of("field", "errors", "errorclass");

        /**
         * Consumed at a lower precedence than ours, so they never reach {@code extractAttrs} — which is why
         * they are absent from the unsupported set despite being just as impossible to carry as a prop.
         */
        private static final Set<String> CONTROL_FLOW = Set.of("if", "unless", "each", "switch", "case");

        @Test
        void everyUnsupportedAttributeIsOneTheStandardDialectActuallyHas() {
            Set<String> registered = standardAttributePrecedences().keySet();

            Set<String> expected = new HashSet<>(CompositionElementModelProcessor.UNSUPPORTED_STANDARD_ATTRIBUTES);
            expected.removeAll(SPRING_ONLY);
            expected.removeAll(registered);
            assertTrue(expected.isEmpty(), "no such th: attribute (renamed or removed upstream?): " + expected);
        }

        @Test
        void everyUnsupportedAttributeWouldHaveRunAfterUs() {
            standardAttributePrecedences().forEach((name, precedence) -> {
                if (CompositionElementModelProcessor.UNSUPPORTED_STANDARD_ATTRIBUTES.contains(name)) {
                    assertTrue(precedence > CompositionElementModelProcessor.PRECEDENCE,
                            "th:" + name + " runs at " + precedence + ", before this processor at "
                                    + CompositionElementModelProcessor.PRECEDENCE + " — it is consumed before "
                                    + "we see the tag, so rejecting it is unreachable");
                }
            });
        }

        @Test
        void controlFlowIsConsumedBeforeUs() {
            Map<String, Integer> precedences = standardAttributePrecedences();

            for (String name : CONTROL_FLOW) {
                Integer precedence = precedences.get(name);
                assertNotNull(precedence, "th:" + name + " is gone from the standard dialect");
                assertTrue(precedence < CompositionElementModelProcessor.PRECEDENCE,
                        "th:" + name + " runs at " + precedence + ", after this processor at "
                                + CompositionElementModelProcessor.PRECEDENCE + " — control flow on a "
                                + "component tag would stop working and it would arrive as a prop instead");
            }
        }

        /** Every {@code th:} attribute with a processor of its own, and the precedence it runs at. */
        private Map<String, Integer> standardAttributePrecedences() {
            String standardPrefix = engine.getConfiguration().getStandardDialectPrefix();
            Map<String, Integer> precedences = new HashMap<>();
            for (IElementProcessor processor : engine.getConfiguration().getElementProcessors(TemplateMode.HTML)) {
                MatchingAttributeName matching = processor.getMatchingAttributeName();
                // Null for element processors; a null name inside it means "all attributes with a prefix",
                // which is the default setter that gives every other th: attribute its meaning.
                if (matching == null || matching.getMatchingAttributeName() == null) {
                    continue;
                }
                AttributeName attributeName = matching.getMatchingAttributeName();
                if (standardPrefix.equals(attributeName.getPrefix())) {
                    precedences.put(attributeName.getAttributeName(), processor.getPrecedence());
                }
            }
            return precedences;
        }
    }
}
