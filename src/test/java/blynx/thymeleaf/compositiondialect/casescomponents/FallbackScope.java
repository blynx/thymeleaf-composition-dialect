package blynx.thymeleaf.compositiondialect.casescomponents;

import blynx.thymeleaf.compositiondialect.CompositionComponent;
import blynx.thymeleaf.compositiondialect.CompositionComponentContext;

/** Its slot's fallback reads {@code ${this}}, to pin down that a fallback is the component's own markup. */
public class FallbackScope extends CompositionComponent {

    public FallbackScope(CompositionComponentContext context) {
        super(context);
    }

    public String getTitle() {
        return "fallback-scope";
    }
}
