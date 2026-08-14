package blynx.thymeleaf.compositiondialect.registryfixtures.pathprefix;

import blynx.thymeleaf.compositiondialect.CompositionComponent;
import blynx.thymeleaf.compositiondialect.CompositionComponentContext;

/** Abstract base contributing a {@code pathPrefix} shared by every concrete subclass. */
public abstract class SharedBase extends CompositionComponent {

    public static final String pathPrefix = "shared";

    public SharedBase(CompositionComponentContext context) {
        super(context);
    }
}
