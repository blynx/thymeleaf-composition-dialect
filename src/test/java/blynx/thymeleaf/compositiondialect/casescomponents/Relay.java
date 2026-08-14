package blynx.thymeleaf.compositiondialect.casescomponents;

import blynx.thymeleaf.compositiondialect.CompositionComponent;
import blynx.thymeleaf.compositiondialect.CompositionComponentContext;

/** Reads nothing, so its template can spread every attribute back out with {@code c:rest}. */
public class Relay extends CompositionComponent {

    public Relay(CompositionComponentContext context) {
        super(context);
    }
}
