package blynx.thymeleaf.compositiondialect.casescomponents;

import blynx.thymeleaf.compositiondialect.CompositionComponent;
import blynx.thymeleaf.compositiondialect.CompositionComponentContext;

/** Its own template nests a {@code c:slot} inside another slot's fallback content — always an error. */
public class NestedSlotFallback extends CompositionComponent {

    public NestedSlotFallback(CompositionComponentContext context) {
        super(context);
    }
}
