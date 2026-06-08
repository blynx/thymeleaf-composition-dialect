package blynx.thymeleaf.compositiondialect

import org.thymeleaf.TemplateEngine
import org.thymeleaf.context.Context
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver
import org.thymeleaf.templateresolver.StringTemplateResolver
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompositionElementModelProcessorTest {

    private val engine: TemplateEngine by lazy {
        val componentResolver = ClassLoaderTemplateResolver().apply {
            order = 1
            prefix = "templates/"
            suffix = ".html"
            resolvablePatterns = setOf("components/*")
            characterEncoding = "UTF-8"
        }
        val pageResolver = StringTemplateResolver().apply {
            order = 2
            isCacheable = false
        }
        TemplateEngine().apply {
            addTemplateResolver(componentResolver)
            addTemplateResolver(pageResolver)
            addDialect(CompositionDialect(
                componentPackage = "blynx.thymeleaf.compositiondialect.testcomponents",
                componentsPath = "components"
            ))
        }
    }

    @Test
    fun `plain component renders its template`() {
        val result = engine.process("<c:plain />", Context())
        assertTrue("plain content" in result)
    }

    @Test
    fun `default slot content is injected into wrapper`() {
        val result = engine.process("<c:wrapper><p id=\"inner\">content</p></c:wrapper>", Context())
        assertTrue("id=\"wrapper\"" in result)
        assertTrue("id=\"inner\"" in result)
        assertTrue(result.indexOf("id=\"wrapper\"") < result.indexOf("id=\"inner\""))
    }

    @Test
    fun `named slot content is placed before default slot content`() {
        val result = engine.process(
            """<c:card><span c:slot="header" id="h">Head</span><span id="b">Body</span></c:card>""",
            Context()
        )
        assertTrue("id=\"h\"" in result)
        assertTrue("id=\"b\"" in result)
        assertTrue(result.indexOf("id=\"h\"") < result.indexOf("id=\"b\""))
    }

    @Test
    fun `c-slot attribute is stripped from rendered output`() {
        val result = engine.process(
            """<c:wrapper><p c:slot="">text</p></c:wrapper>""",
            Context()
        )
        assertFalse("c:slot" in result)
    }

    @Test
    fun `absent named slot hides its conditional section`() {
        val result = engine.process("<c:card><p id=\"b\">Body only</p></c:card>", Context())
        assertFalse("card-header" in result)
        assertFalse("card-footer" in result)
        assertTrue("card-body" in result)
    }

    @Test
    fun `present named slot shows its conditional section`() {
        val result = engine.process(
            """<c:card><span c:slot="header">H</span><span c:slot="footer">F</span></c:card>""",
            Context()
        )
        assertTrue("card-header" in result)
        assertTrue("card-footer" in result)
    }

    @Test
    fun `plain attribute is passed as raw string`() {
        val result = engine.process("""<c:label text="hello" />""", Context())
        assertTrue("hello" in result)
    }

    @Test
    fun `expression attribute is evaluated against the template context`() {
        val ctx = Context().apply { setVariable("msg", "evaluated") }
        val result = engine.process("<c:label c:value=\"\${msg}\" />", ctx)
        assertTrue("evaluated" in result)
    }

    @Test
    fun `heading outside magic-headings defaults to level 1`() {
        val result = engine.process("<c:heading>text</c:heading>", Context())
        assertTrue("id=\"heading-1\"" in result)
    }

    @Test
    fun `heading inside magic-headings uses inherited level`() {
        val result = engine.process("<c:magic-headings><c:heading>text</c:heading></c:magic-headings>", Context())
        assertTrue("id=\"heading-1\"" in result)
    }

    @Test
    fun `heading inside nested magic-headings increments level`() {
        val result = engine.process(
            "<c:magic-headings><c:magic-headings><c:heading>text</c:heading></c:magic-headings></c:magic-headings>",
            Context()
        )
        assertTrue("id=\"heading-2\"" in result)
    }

    @Test
    fun `explicit level attribute overrides inherited level`() {
        val result = engine.process(
            """<c:magic-headings><c:heading c:level="${'$'}{5}">text</c:heading></c:magic-headings>""",
            Context()
        )
        assertTrue("id=\"heading-5\"" in result)
    }
}
