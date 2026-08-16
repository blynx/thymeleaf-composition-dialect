package blynx.thymeleaf.compositiondialect.registryfixtures.abstractbase;

import blynx.thymeleaf.compositiondialect.AbstractCompositionComponent;
import blynx.thymeleaf.compositiondialect.CompositionComponentContext;

/** Abstract intermediate base — fixture proving {@code ComponentRegistry.scan} skips abstract classes. */
public abstract class BaseThing extends AbstractCompositionComponent {

    public BaseThing(CompositionComponentContext context) {
        super(context);
    }
}
