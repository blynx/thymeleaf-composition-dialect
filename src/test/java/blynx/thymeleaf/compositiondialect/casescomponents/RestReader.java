package blynx.thymeleaf.compositiondialect.casescomponents;

import blynx.thymeleaf.compositiondialect.CompositionComponent;
import blynx.thymeleaf.compositiondialect.CompositionComponentContext;

/**
 * Reads {@code restAttributes} as a map in its own template, rather than spreading it with {@code c:rest}
 * the way {@link Relay} does. {@code title} is a declared prop, so it must not appear among the entries.
 */
public record RestReader(String title, CompositionComponentContext context) implements CompositionComponent {
}
