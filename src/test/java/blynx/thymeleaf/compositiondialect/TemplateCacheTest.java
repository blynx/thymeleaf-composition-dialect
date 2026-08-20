package blynx.thymeleaf.compositiondialect;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templateresolver.FileTemplateResolver;
import org.thymeleaf.templateresolver.StringTemplateResolver;

class TemplateCacheTest {

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
                "testcomponents",
                "blynx.thymeleaf.compositiondialect.testcomponents"));
        return fileEngine;
    }

    private void writeWrapperTemplate(Path templateDir, String marker) throws IOException {
        Files.createDirectories(templateDir.resolve("testcomponents"));
        Files.writeString(templateDir.resolve("testcomponents/wrapper.html"),
                "<c:component impl=\"Wrapper\"/>\n<div id=\"" + marker + "\"><c:slot /></div>");
    }
}
