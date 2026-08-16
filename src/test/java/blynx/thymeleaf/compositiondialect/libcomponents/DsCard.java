package blynx.thymeleaf.compositiondialect.libcomponents;

import blynx.thymeleaf.compositiondialect.CompositionComponent;
import blynx.thymeleaf.compositiondialect.CompositionComponentContext;

/** A default slot, for nesting an application's components inside an imported library's. */
public record DsCard(String title, CompositionComponentContext context) implements CompositionComponent {

    public DsCard {
        title = title != null ? title : "untitled";
    }
}
