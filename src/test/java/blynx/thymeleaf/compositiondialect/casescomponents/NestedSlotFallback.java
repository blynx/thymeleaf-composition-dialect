package blynx.thymeleaf.compositiondialect.casescomponents;

import blynx.thymeleaf.compositiondialect.CompositionComponent;
import blynx.thymeleaf.compositiondialect.CompositionComponentContext;

/**
 * Its own template nests {@code c:slot} markers inside another slot's fallback content, three levels deep:
 * {@code rows} declares a fallback containing {@code empty-message} (with a sibling {@code empty-icon}),
 * whose own fallback in turn contains {@code empty-detail}.
 */
public record NestedSlotFallback(CompositionComponentContext context) implements CompositionComponent {
}
