package blynx.thymeleaf.compositiondialect.testcomponents;

import blynx.thymeleaf.compositiondialect.CompositionComponent;
import blynx.thymeleaf.compositiondialect.CompositionComponentContext;

/** A boxed {@code Integer}, not {@code int}: absent must be distinguishable from an explicit {@code 0}. */
public record Heading(Integer level, CompositionComponentContext context) implements CompositionComponent {

    public Heading {
        if (level == null) {
            level = context.variable("parentMagicHeadings") instanceof MagicHeadings parent
                    ? parent.level()
                    : 1;
        }
    }
}
