package blynx.thymeleaf.compositiondialect.casescomponents;

import blynx.thymeleaf.compositiondialect.CompositionComponent;
import blynx.thymeleaf.compositiondialect.CompositionComponentContext;

/** A named and a default slot, both with fallback content, plus an empty named slot with none. */
public class Card extends CompositionComponent {

    public Card(CompositionComponentContext context) {
        super(context);
    }
}
