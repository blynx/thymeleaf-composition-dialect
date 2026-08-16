package blynx.thymeleaf.compositiondialect.libcomponents;

import blynx.thymeleaf.compositiondialect.CompositionComponent;
import blynx.thymeleaf.compositiondialect.CompositionComponentContext;

/** A leaf of the imported library, used by {@link DsCard} from inside the library's own template. */
public record DsBadge(String label, CompositionComponentContext context) implements CompositionComponent {

    public DsBadge {
        label = label != null ? label : "none";
    }
}
