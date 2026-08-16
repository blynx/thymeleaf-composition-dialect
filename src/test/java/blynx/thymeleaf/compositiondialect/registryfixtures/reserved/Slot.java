package blynx.thymeleaf.compositiondialect.registryfixtures.reserved;

import blynx.thymeleaf.compositiondialect.CompositionComponent;
import blynx.thymeleaf.compositiondialect.CompositionComponentContext;

/** Claims {@code c:slot}, which the dialect's own grammar already uses — rejected at startup. */
public record Slot(CompositionComponentContext context) implements CompositionComponent {
}
