package blynx.thymeleaf.compositiondialect.casescomponents;

import java.util.Locale;
import java.util.Objects;

import blynx.thymeleaf.compositiondialect.CompositionComponent;
import blynx.thymeleaf.compositiondialect.CompositionComponentContext;
import blynx.thymeleaf.compositiondialect.Prop;

/**
 * A record component: {@code level}, {@code closable} and {@code autoHideSeconds} are its props, one-to-one
 * with its record components, needing no {@code props} declaration at all. {@code closable} proves
 * {@link Prop} overriding the derived attribute name ({@code dismissible} would otherwise be assumed);
 * {@code autoHideSeconds} proves the derivation itself, binding from {@code auto-hide-seconds} with no
 * annotation. A missing {@code level} attribute is coerced to {@code null} and defaulted in the compact
 * constructor; a missing {@code autoHideSeconds} is coerced straight to the primitive's zero value.
 */
public record Alert(
        Level level,
        @Prop("closable") boolean dismissible,
        int autoHideSeconds,
        CompositionComponentContext context) implements CompositionComponent {

    public enum Level { INFO, WARNING, DANGER }

    public Alert {
        level = Objects.requireNonNullElse(level, Level.INFO);
    }

    public String cssClass() {
        return "alert alert-" + level.name().toLowerCase(Locale.ROOT);
    }

    public boolean autoHides() {
        return autoHideSeconds > 0;
    }
}
