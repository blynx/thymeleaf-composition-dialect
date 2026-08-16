package blynx.thymeleaf.compositiondialect.casescomponents;

import blynx.thymeleaf.compositiondialect.CompositionComponent;
import blynx.thymeleaf.compositiondialect.CompositionComponentContext;

/** A single default slot, for nesting cases. Carries a {@code title} of its own. */
public record Panel(String title, CompositionComponentContext context) implements CompositionComponent {

    public Panel {
        title = title != null ? title : "untitled";
    }
}
