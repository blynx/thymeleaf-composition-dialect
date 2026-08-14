package blynx.thymeleaf.compositiondialect;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
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
}
