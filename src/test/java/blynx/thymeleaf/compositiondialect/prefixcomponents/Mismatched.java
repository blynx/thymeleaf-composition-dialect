package blynx.thymeleaf.compositiondialect.prefixcomponents;

import blynx.thymeleaf.compositiondialect.CompositionComponent;
import blynx.thymeleaf.compositiondialect.CompositionComponentContext;

/** Its template spells the marker {@code c:slot} while it is registered at {@code x}. Fails when rendered. */
public record Mismatched(CompositionComponentContext context) implements CompositionComponent {
}
