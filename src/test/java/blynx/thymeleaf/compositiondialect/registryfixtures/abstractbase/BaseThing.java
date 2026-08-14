package blynx.thymeleaf.compositiondialect.registryfixtures.abstractbase;

import blynx.thymeleaf.compositiondialect.CompositionComponent;
import blynx.thymeleaf.compositiondialect.CompositionComponentContext;

/** Abstract intermediate base — fixture proving {@code ComponentRegistry.scan} skips abstract classes. */
public abstract class BaseThing extends CompositionComponent {

    public static final String path = "base-folder";

    public BaseThing(CompositionComponentContext context) {
        super(context);
    }
}
