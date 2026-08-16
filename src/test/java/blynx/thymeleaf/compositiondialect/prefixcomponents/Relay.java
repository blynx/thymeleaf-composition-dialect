package blynx.thymeleaf.compositiondialect.prefixcomponents;

import blynx.thymeleaf.compositiondialect.CompositionComponent;
import blynx.thymeleaf.compositiondialect.CompositionComponentContext;

/**
 * Reads nothing, and spreads its attributes back out with {@code c:rest} — the <em>other</em> dialect's
 * spelling, deliberately, to show which component a grammar attribute acts on.
 */
public record Relay(CompositionComponentContext context) implements CompositionComponent {
}
