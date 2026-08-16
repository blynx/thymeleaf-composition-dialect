package blynx.thymeleaf.compositiondialect.registryfixtures.missingtemplate;

import blynx.thymeleaf.compositiondialect.CompositionComponent;
import blynx.thymeleaf.compositiondialect.CompositionComponentContext;

/**
 * Has no {@code ghost.html} beside the other test templates, deliberately — it stands in for the class
 * whose template was never written, or kept the old name through a rename. Registers like any other
 * component; only looking for its template finds anything wrong.
 */
public record Ghost(CompositionComponentContext context) implements CompositionComponent {
}
