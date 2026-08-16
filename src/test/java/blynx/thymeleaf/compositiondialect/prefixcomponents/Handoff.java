package blynx.thymeleaf.compositiondialect.prefixcomponents;

import blynx.thymeleaf.compositiondialect.CompositionComponent;
import blynx.thymeleaf.compositiondialect.CompositionComponentContext;

/**
 * Writes markup of its own into a {@code c:} component's slot, so that what {@code ${this}} resolves to
 * there is the question the render answers: the content was written in <em>this</em> template, and must
 * keep reading this component even though the slot it lands in belongs to another dialect.
 */
public record Handoff(String label, CompositionComponentContext context) implements CompositionComponent {
}
