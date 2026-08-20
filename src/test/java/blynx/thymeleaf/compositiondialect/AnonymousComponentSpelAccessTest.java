package blynx.thymeleaf.compositiondialect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.context.expression.MapAccessor;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

/**
 * The SpEL half of the {@code AnonymousComponentOgnlAccessTest} proof.
 *
 * <p>{@code thymeleaf-spring6} is what wires SpEL into the Thymeleaf engine for a Spring-backed
 * application, and it registers a {@link MapAccessor} on its evaluation context — that registration is
 * exactly what makes {@code ${this.title}} resolve on a {@code Map}-backed object the same way under Spring
 * as it already does under OGNL. {@code thymeleaf-spring6} is not a dependency of this project (plain
 * {@code thymeleaf} itself is {@code compileOnly}, so its Spring counterpart is not pulled in either), so
 * this test reaches for the same mechanism directly through {@code spring-expression} — a transitive test
 * dependency via {@code spring-boot-autoconfigure} — rather than through Thymeleaf's own Spring integration,
 * which does not exist here to test against.
 *
 * <p>What it proves is the one thing that was actually in doubt: that a {@code MapAccessor}-equipped SpEL
 * context resolves both a {@code Map}-backed property read <em>and</em> a method call on the very same
 * object, which is precisely what {@link AnonymousComponent} needs to be true of {@code this} under Spring.
 */
class AnonymousComponentSpelAccessTest {

    private final ExpressionParser parser = new SpelExpressionParser();

    @Test
    void propertyAccessReadsADeclaredPropThroughAMapAccessor() {
        AnonymousComponent component = new AnonymousComponent(Map.of("title", "Hello"), noSlots());

        assertEquals("Hello", evaluate("title", component));
    }

    @Test
    void methodCallsStayReachableOnTheSameMapBackedObject() {
        AnonymousComponent component = new AnonymousComponent(Map.of(), slots("x"));

        assertTrue((Boolean) evaluate("hasSlot('x')", component));
        assertFalse((Boolean) evaluate("hasSlot('y')", component));
    }

    private Object evaluate(String expression, AnonymousComponent root) {
        StandardEvaluationContext evaluationContext = new StandardEvaluationContext(root);
        evaluationContext.addPropertyAccessor(new MapAccessor());
        Expression compiled = parser.parseExpression(expression);
        return compiled.getValue(evaluationContext);
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
