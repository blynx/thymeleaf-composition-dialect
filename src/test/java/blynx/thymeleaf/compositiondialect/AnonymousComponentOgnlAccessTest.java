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

    /**
     * The gap this closes: {@code Map} property access resolves {@code this.hasSlot} (bare, no parens) by
     * calling {@code get("hasSlot")} directly, never falling back to the real zero-arg method the way OGNL
     * does for a normal (non-{@code Map}) object. Without the constructor seeding that key, this would
     * silently read as {@code null} regardless of whether the default slot actually has content.
     */
    @Test
    void bareHasSlotMatchesTheParenthesisedForm() {
        Context withDefaultSlot = withThis(new AnonymousComponent(Map.of(), slots(CompositionComponent.DEFAULT_SLOT)));
        Context withoutDefaultSlot = withThis(new AnonymousComponent(Map.of(), slots("other")));

        String hasIt = engine.process("<span th:text=\"${this.hasSlot}\">?</span>", withDefaultSlot);
        String doesNot = engine.process("<span th:text=\"${this.hasSlot}\">?</span>", withoutDefaultSlot);

        assertTrue(hasIt.contains(">true<"), hasIt);
        assertTrue(doesNot.contains(">false<"), doesNot);
    }

    /**
     * Same gap, for {@code restAttributes}: bare {@code this.restAttributes} must resolve to the real map,
     * not {@code null}, so indexing into it (as the documented usage does) doesn't throw.
     */
    @Test
    void bareRestAttributesIsIndexable() {
        CompositionComponentContext withRest = new CompositionComponentContext(
                new ComponentAttributes(Map.of("title", "mine", "data-kept", "a"), Set.of("title"), Map.of()),
                Set.of(),
                Locale.ROOT,
                (code, params) -> code,
                name -> null,
                (name, value) -> { });
        Context context = withThis(new AnonymousComponent(Map.of("title", "mine"), withRest));

        String result = engine.process(
                "<span th:text=\"${this.restAttributes['data-kept']}\">?</span>", context);

        assertTrue(result.contains(">a<"), result);
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
                new ComponentAttributes(Map.of(), Set.of(), Map.of()),
                Set.of(slotNames),
                Locale.ROOT,
                (code, params) -> code,
                name -> null,
                (name, value) -> { });
    }
}
