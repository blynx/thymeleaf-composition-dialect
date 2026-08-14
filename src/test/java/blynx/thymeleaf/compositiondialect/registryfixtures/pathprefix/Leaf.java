package blynx.thymeleaf.compositiondialect.registryfixtures.pathprefix;

import blynx.thymeleaf.compositiondialect.CompositionComponentContext;

/** Inherits {@link SharedBase}'s {@code pathPrefix} and adds its own {@code path}; both should compose. */
public class Leaf extends SharedBase {

    public static final String path = "leaf-folder";

    public Leaf(CompositionComponentContext context) {
        super(context);
    }
}
