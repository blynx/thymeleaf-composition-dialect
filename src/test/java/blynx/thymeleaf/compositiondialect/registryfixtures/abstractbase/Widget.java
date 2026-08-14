package blynx.thymeleaf.compositiondialect.registryfixtures.abstractbase;

import blynx.thymeleaf.compositiondialect.CompositionComponentContext;

/** Concrete subclass of {@link BaseThing} — proves transitive discovery still works, and that {@code path}
 * shadows rather than composes: its own path, not the abstract parent's. */
public class Widget extends BaseThing {

    public static final String path = "sub-folder";

    public Widget(CompositionComponentContext context) {
        super(context);
    }
}
