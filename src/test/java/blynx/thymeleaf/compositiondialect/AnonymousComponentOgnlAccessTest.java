package blynx.thymeleaf.compositiondialect;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templateresolver.StringTemplateResolver;

/**
 * Stage 0/2 spike, pinned as a permanent test: {@code ${this.x}} and {@code ${this.hasSlot('x')}} both
 * resolve off an {@link AnonymousComponent} under OGNL — the expression language a plain (non-Spring)
 * {@link TemplateEngine} uses. No {@link CompositionDialect} is involved at all, deliberately: the
 * mechanism under test is OGNL's native {@code Map} property access on the object bound to {@code this},
 * not anything this dialect's own processors add.
 *
 * <p>See {@code AnonymousComponentSpelAccessTest} for the SpEL half of the same spike — a plain
 * {@code TemplateEngine} has no SpEL support to test against, since that only exists in
 * {@code thymeleaf-spring6}, which this project depends on at compile time for neither engine nor tests.
 */
class AnonymousComponentOgnlAccessTest {

    private final TemplateEngine engine = new TemplateEngine();

    AnonymousComponentOgnlAccessTest() {
        engine.setTemplateResolver(new StringTemplateResolver());
    }

    @Test
    void propertyAccessReadsADeclaredProp() {
        Context context = withThis(new AnonymousComponent(Map.of("title", "Hello"), noSlots()));

        String result = engine.process("<span th:text=\"${this.title}\">?</span>", context);

        assertTrue(result.contains(">Hello<"), result);
    }

    @Test
    void methodCallsStayReachableOnTheSameMapBackedObject() {
        Context context = withThis(new AnonymousComponent(Map.of(), slots("x")));

        String result = engine.process(
                "<span th:text=\"${this.hasSlot('x')}\">?</span><span th:text=\"${this.hasSlot('y')}\">?</span>",
                context);

        assertTrue(result.contains(">true<"), result);
        assertTrue(result.contains(">false<"), result);
    }

    private static Context withThis(AnonymousComponent component) {
        Context context = new Context();
        context.setVariable("this", component);
        return context;
    }

    private static CompositionComponentContext noSlots() {
        return slots();
    }

    private static CompositionComponentContext slots(String... slotNames) {
        return new CompositionComponentContext(
                new ComponentAttributes(Map.of(), Set.of()),
                Set.of(slotNames),
                Locale.ROOT,
                (code, params) -> code,
                name -> null,
                (name, value) -> { });
    }
}
