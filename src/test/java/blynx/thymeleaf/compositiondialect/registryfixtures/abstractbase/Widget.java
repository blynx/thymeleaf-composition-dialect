package blynx.thymeleaf.compositiondialect.registryfixtures.abstractbase;

import blynx.thymeleaf.compositiondialect.Composition;
import blynx.thymeleaf.compositiondialect.CompositionComponentContext;

/** Concrete subclass of {@link BaseThing} — proves transitive discovery still works, and that its own
 * {@code @Composition} applies regardless of the abstract parent (which declares none at all). */
@Composition(path = "sub-folder")
public class Widget extends BaseThing {

    public Widget(CompositionComponentContext context) {
        super(context);
    }
}
