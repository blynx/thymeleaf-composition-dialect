package blynx.thymeleaf.compositiondialect.registryfixtures.collision.bravo;

import blynx.thymeleaf.compositiondialect.CompositionComponent;
import blynx.thymeleaf.compositiondialect.CompositionComponentContext;

/** Same simple name and tag as {@code registryfixtures.collision.alpha.Twin} — collision fixture. */
public record Twin(CompositionComponentContext context) implements CompositionComponent {
}
