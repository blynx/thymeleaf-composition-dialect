package blynx.thymeleaf.compositiondialect.registryfixtures.composition;

import blynx.thymeleaf.compositiondialect.CompositionComponent;
import blynx.thymeleaf.compositiondialect.CompositionComponentContext;

/** No {@code @Composition} at all; the package's {@code pathPrefix} should still apply on its own. */
public record BareAnnotatedLeaf(CompositionComponentContext context) implements CompositionComponent {
}
