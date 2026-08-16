package blynx.thymeleaf.compositiondialect.registryfixtures.props;

import blynx.thymeleaf.compositiondialect.CompositionComponent;
import blynx.thymeleaf.compositiondialect.CompositionComponentContext;

/** A record's components are its props, one for one, with no declaration needed at all — {@code variant}
 * and {@code autoHideSeconds} (kebab-derived to {@code auto-hide-seconds}) are props; {@code context},
 * recognized by type, is not. */
public record RecordProps(String variant, int autoHideSeconds, CompositionComponentContext context)
        implements CompositionComponent {
}
