package blynx.thymeleaf.compositiondialect.casescomponents;

import blynx.thymeleaf.compositiondialect.CompositionComponent;
import blynx.thymeleaf.compositiondialect.CompositionComponentContext;

/**
 * Names the same slot twice with nothing conditional between them, and declares no default one — so it
 * answers two questions at once: what two markers sharing a name do with one piece of content, and what
 * happens to a body given to a component with nowhere to put it.
 */
public record RepeatedSlot(CompositionComponentContext context) implements CompositionComponent {
}
