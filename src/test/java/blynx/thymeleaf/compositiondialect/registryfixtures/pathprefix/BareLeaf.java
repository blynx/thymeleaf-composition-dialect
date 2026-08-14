package blynx.thymeleaf.compositiondialect.registryfixtures.pathprefix;

import blynx.thymeleaf.compositiondialect.CompositionComponentContext;

/** Inherits {@link SharedBase}'s {@code pathPrefix} but declares no {@code path} of its own. */
public class BareLeaf extends SharedBase {

    public BareLeaf(CompositionComponentContext context) {
        super(context);
    }
}
