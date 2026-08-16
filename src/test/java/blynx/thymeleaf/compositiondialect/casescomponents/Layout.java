package blynx.thymeleaf.compositiondialect.casescomponents;

import blynx.thymeleaf.compositiondialect.CompositionComponent;
import blynx.thymeleaf.compositiondialect.CompositionComponentContext;

/** A page shell with named slots. Carries a {@code title} so it is visible whose title a case reads. */
public record Layout(String title, CompositionComponentContext context) implements CompositionComponent {

    public Layout {
        title = title != null ? title : "untitled";
    }
}
