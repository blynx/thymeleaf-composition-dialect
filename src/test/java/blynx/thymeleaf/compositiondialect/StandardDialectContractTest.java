package blynx.thymeleaf.compositiondialect;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.engine.AttributeName;
import org.thymeleaf.processor.element.IElementProcessor;
import org.thymeleaf.processor.element.MatchingAttributeName;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.templateresolver.StringTemplateResolver;

/**
 * The two assumptions {@link CompositionElementModelProcessor#UNSUPPORTED_STANDARD_ATTRIBUTES} rests on,
 * checked against the standard dialect itself rather than believed: which {@code th:} attributes exist,
 * and which of them run before this processor replaces the tag. A Thymeleaf upgrade that renames one or
 * moves its precedence across ours would otherwise change what a component tag silently accepts.
 */
@DisplayName("contract with the standard dialect")
class StandardDialectContractTest {

    /** Registered by the Spring dialect, so absent from the plain engine these tests build. */
    private static final Set<String> SPRING_ONLY = Set.of("field", "errors", "errorclass");

    /**
     * Consumed at a lower precedence than ours, so they never reach {@code extractAttrs} — which is why
     * they are absent from the unsupported set despite being just as impossible to carry as a prop.
     */
    private static final Set<String> CONTROL_FLOW = Set.of("if", "unless", "each", "switch", "case");

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
                "testcomponents",
                "blynx.thymeleaf.compositiondialect.testcomponents"));
    }

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
