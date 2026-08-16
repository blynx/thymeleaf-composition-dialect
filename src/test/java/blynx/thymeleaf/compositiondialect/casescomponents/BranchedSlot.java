package blynx.thymeleaf.compositiondialect.casescomponents;

import blynx.thymeleaf.compositiondialect.CompositionComponent;
import blynx.thymeleaf.compositiondialect.CompositionComponentContext;

/**
 * Writes the same slot once per branch, one branch per level — the shape a component whose tag name is not
 * fixed has to take, since Thymeleaf has no dynamic element name. Which marker renders is decided per
 * render by {@code th:if}, long after all of them were found.
 */
public record BranchedSlot(Integer level, CompositionComponentContext context) implements CompositionComponent {

    public BranchedSlot {
        if (level == null) {
            level = 1;
        }
    }
}
