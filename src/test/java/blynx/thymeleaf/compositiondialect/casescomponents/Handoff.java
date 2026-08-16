package blynx.thymeleaf.compositiondialect.casescomponents;

import blynx.thymeleaf.compositiondialect.CompositionComponent;
import blynx.thymeleaf.compositiondialect.CompositionComponentContext;

/** Passes the content it is given straight on into another component's slot. */
public record Handoff(String title, CompositionComponentContext context) implements CompositionComponent {

    public Handoff {
        title = title != null ? title : "untitled";
    }
}
