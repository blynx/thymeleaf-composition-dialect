package blynx.thymeleaf.compositiondialect.registryfixtures.composition;

import blynx.thymeleaf.compositiondialect.Composition;
import blynx.thymeleaf.compositiondialect.CompositionComponent;
import blynx.thymeleaf.compositiondialect.CompositionComponentContext;

/** Declares its own {@code path}; the package's {@code pathPrefix} should still compose with it. */
@Composition(path = "leaf-folder")
public record AnnotatedLeaf(CompositionComponentContext context) implements CompositionComponent {
}
