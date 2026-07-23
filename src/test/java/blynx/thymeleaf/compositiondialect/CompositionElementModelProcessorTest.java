package blynx.thymeleaf.compositiondialect;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.templateresolver.StringTemplateResolver;

class CompositionElementModelProcessorTest {

    private TemplateEngine engine;

    @BeforeEach
    void setUp() {
        ClassLoaderTemplateResolver componentResolver = new ClassLoaderTemplateResolver();
        componentResolver.setOrder(1);
        componentResolver.setPrefix("templates/");
        componentResolver.setSuffix(".html");
        componentResolver.setResolvablePatterns(Set.of("components/*"));
        componentResolver.setCharacterEncoding("UTF-8");

        StringTemplateResolver pageResolver = new StringTemplateResolver();
        pageResolver.setOrder(2);
        pageResolver.setCacheable(false);

        engine = new TemplateEngine();
        engine.addTemplateResolver(componentResolver);
        engine.addTemplateResolver(pageResolver);
        engine.addDialect(new CompositionDialect(
                "blynx.thymeleaf.compositiondialect.testcomponents",
                "components"));
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
}
