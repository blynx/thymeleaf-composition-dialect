package blynx.thymeleaf.compositiondialect.registryfixtures.collision.alpha;

import blynx.thymeleaf.compositiondialect.CompositionComponent;
import blynx.thymeleaf.compositiondialect.CompositionComponentContext;

/** Same simple name and tag as {@code registryfixtures.collision.bravo.Twin} — collision fixture. */
public class Twin extends CompositionComponent {

    public static final String path = "alpha";

    public Twin(CompositionComponentContext context) {
        super(context);
    }
}
