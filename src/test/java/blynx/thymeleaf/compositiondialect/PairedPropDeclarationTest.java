package blynx.thymeleaf.compositiondialect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.templateresolver.StringTemplateResolver;

/**
 * The paired {@code <c:component><c:prop .../></c:component>} form: the same props a flat {@code
 * props="…"} declares, but each with its own shape — {@code required}, a literal {@code default}, and a
 * {@code type} wired into {@link PropCoercion}. See {@code _local-notes/TEMPLATE-FIRST-COMPONENTS.md},
 * "Paired form, for per-prop declarations".
 */
class PairedPropDeclarationTest {

    private static final String PAIRED = "registryfixtures/pairedprops";

    @Test
    void eachPropKeepsItsOwnShape() {
        ComponentDescriptor descriptor = ComponentRegistry.scan(new ComponentSource(PAIRED), "c")
                .findByTagName("c", "price").orElseThrow();

        assertEquals(Set.of("variant", "quantity", "amount"), descriptor.props());
        List<PropSpec> specs = descriptor.classlessPropSpecs();
        assertEquals(new PropSpec("variant", false, "primary", null),
                specs.stream().filter(s -> s.kebabName().equals("variant")).findFirst().orElseThrow());
        assertEquals(new PropSpec("quantity", true, null, int.class),
                specs.stream().filter(s -> s.kebabName().equals("quantity")).findFirst().orElseThrow());
        assertEquals(new PropSpec("amount", false, null, java.math.BigDecimal.class),
                specs.stream().filter(s -> s.kebabName().equals("amount")).findFirst().orElseThrow());
    }

    @Test
    void aPropNotSelfClosedFailsLoudly() {
        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> ComponentRegistry.scan(new ComponentSource("registryfixtures/propunclosed"), "c"));

        assertTrue(thrown.getMessage().contains("<c:prop>"), thrown.getMessage());
        assertTrue(thrown.getMessage().contains("must be self-closing"), thrown.getMessage());
    }

    @Test
    void markupInsideComponentFailsLoudly() {
        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> ComponentRegistry.scan(new ComponentSource("registryfixtures/propmarkup"), "c"));

        assertTrue(thrown.getMessage().contains("<div>"), thrown.getMessage());
        assertTrue(thrown.getMessage().contains("never markup"), thrown.getMessage());
    }

    @Test
    void bothPropsAttributeAndPropChildrenFailsLoudly() {
        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> ComponentRegistry.scan(new ComponentSource("registryfixtures/propboth"), "c"));

        assertTrue(thrown.getMessage().contains("both"), thrown.getMessage());
        assertTrue(thrown.getMessage().contains("use one or the other"), thrown.getMessage());
    }

    @Test
    void propIsReservedLikeSlotAndComponent() {
        ComponentRegistry registry = ComponentRegistry.scan(new ComponentSource("registryfixtures/reservedprop"), "c");

        IllegalStateException thrown = assertThrows(IllegalStateException.class, registry::requireNoReservedTagNames);
        assertTrue(thrown.getMessage().contains("<c:prop>"), thrown.getMessage());
    }

    private TemplateEngine engine;

    @BeforeEach
    void setUp() {
        ClassLoaderTemplateResolver components = new ClassLoaderTemplateResolver();
        components.setPrefix("templates/");
        components.setSuffix(".html");
        components.setResolvablePatterns(Set.of(PAIRED + "/*"));
        components.setCharacterEncoding("UTF-8");

        StringTemplateResolver pages = new StringTemplateResolver();
        pages.setCacheable(false);

        engine = new TemplateEngine();
        engine.addTemplateResolver(components);
        engine.addTemplateResolver(pages);
        engine.addDialect(new CompositionDialect(PAIRED));
    }

    @Test
    void aDefaultFillsInWhenTheCallerOmitsTheAttribute() {
        String result = engine.process("<c:price quantity=\"3\"/>", new Context());

        assertTrue(result.contains(">primary<"), result);
    }

    @Test
    void theCallersValueWinsOverTheDefault() {
        String result = engine.process("<c:price quantity=\"3\" variant=\"secondary\"/>", new Context());

        assertTrue(result.contains(">secondary<"), result);
    }

    @Test
    void aTypedPropIsCoercedFromAPlainAttribute() {
        String result = engine.process("<c:price quantity=\"3\"/>", new Context());

        assertTrue(result.contains("id=\"quantity\">3<"), result);
    }

    @Test
    void aRequiredPropMissingWithNoDefaultFailsAtRender() {
        Exception thrown = assertThrows(Exception.class, () -> engine.process("<c:price/>", new Context()));

        String message = rootMessage(thrown);
        assertTrue(message.contains("quantity"), message);
        assertTrue(message.contains("requires"), message);
    }

    @Test
    void anOptionalDomainTypedPropIsNullWhenOmitted() {
        String result = engine.process("<c:price quantity=\"3\"/>", new Context());

        assertTrue(result.contains("<span id=\"amount\"></span>"), result);
    }

    @Test
    void aDomainTypedPropPassesThroughWhenAlreadyTheRightType() {
        Context context = new Context();
        context.setVariable("total", new java.math.BigDecimal("12.50"));

        String result = engine.process("<c:price quantity=\"3\" c:amount=\"${total}\"/>", context);

        assertTrue(result.contains("id=\"amount\">12.50<"), result);
    }

    @Test
    void aDomainTypedPropFailsLoudlyWhenGivenAPlainStringAttribute() {
        Exception thrown = assertThrows(Exception.class,
                () -> engine.process("<c:price quantity=\"3\" amount=\"12.00\"/>", new Context()));

        assertTrue(anyMessageContains(thrown, "amount"), fullChain(thrown));
        assertTrue(anyMessageContains(thrown, "BigDecimal"), fullChain(thrown));
    }

    /** {@code PropCoercion} wraps a plain conversion failure in a nicer message; the plain one stays as
     * its cause, so the useful detail may be at any level rather than only at the deepest one. */
    private static boolean anyMessageContains(Throwable thrown, String text) {
        for (Throwable current = thrown; current != null; current = current.getCause()) {
            if (String.valueOf(current.getMessage()).contains(text)) {
                return true;
            }
        }
        return false;
    }

    private static String fullChain(Throwable thrown) {
        StringBuilder chain = new StringBuilder();
        for (Throwable current = thrown; current != null; current = current.getCause()) {
            chain.append(current).append('\n');
        }
        return chain.toString();
    }

    private static String rootMessage(Throwable thrown) {
        Throwable root = thrown;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return String.valueOf(root.getMessage());
    }
}
