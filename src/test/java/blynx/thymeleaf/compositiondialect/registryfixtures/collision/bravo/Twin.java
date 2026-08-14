package blynx.thymeleaf.compositiondialect.registryfixtures.collision.bravo;

import blynx.thymeleaf.compositiondialect.CompositionComponent;
import blynx.thymeleaf.compositiondialect.CompositionComponentContext;

/** Same simple name and tag as {@code registryfixtures.collision.alpha.Twin} — collision fixture. */
public class Twin extends CompositionComponent {

    public static final String path = "bravo";

    public Twin(CompositionComponentContext context) {
        super(context);
    }
}
