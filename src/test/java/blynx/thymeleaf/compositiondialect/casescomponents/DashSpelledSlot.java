package blynx.thymeleaf.compositiondialect.casescomponents;

import blynx.thymeleaf.compositiondialect.CompositionComponent;
import blynx.thymeleaf.compositiondialect.CompositionComponentContext;

/** Its own template spells the marker {@code <c-slot />}, to pin down identity-based marker recognition. */
public class DashSpelledSlot extends CompositionComponent {

    public DashSpelledSlot(CompositionComponentContext context) {
        super(context);
    }
}
