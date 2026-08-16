package blynx.thymeleaf.compositiondialect.testcomponents;

import blynx.thymeleaf.compositiondialect.CompositionComponent;
import blynx.thymeleaf.compositiondialect.CompositionComponentContext;

public record MagicHeadings(Integer level, CompositionComponentContext context) implements CompositionComponent {

    public MagicHeadings {
        // Read the parent's level BEFORE registering ourselves, so nested instances increment.
        if (level == null) {
            level = context.variable("parentMagicHeadings") instanceof MagicHeadings parent
                    ? parent.level() + 1
                    : 1;
        }
        context.setVariable("parentMagicHeadings", this);
    }
}
