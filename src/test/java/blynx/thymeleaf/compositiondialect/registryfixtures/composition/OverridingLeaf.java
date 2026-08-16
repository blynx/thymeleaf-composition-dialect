package blynx.thymeleaf.compositiondialect.registryfixtures.composition;

import blynx.thymeleaf.compositiondialect.Composition;
import blynx.thymeleaf.compositiondialect.CompositionComponent;
import blynx.thymeleaf.compositiondialect.CompositionComponentContext;

/** Declares its own {@code pathPrefix}; that should win over the package's, not compose with it. */
@Composition(path = "own-folder", pathPrefix = "override")
public record OverridingLeaf(CompositionComponentContext context) implements CompositionComponent {
}
