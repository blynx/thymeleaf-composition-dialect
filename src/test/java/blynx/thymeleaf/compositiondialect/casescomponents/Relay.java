package blynx.thymeleaf.compositiondialect.casescomponents;

import blynx.thymeleaf.compositiondialect.CompositionComponent;
import blynx.thymeleaf.compositiondialect.CompositionComponentContext;

/** Reads nothing, so its template can spread every attribute back out with {@code c:rest}. */
public record Relay(CompositionComponentContext context) implements CompositionComponent {
}
