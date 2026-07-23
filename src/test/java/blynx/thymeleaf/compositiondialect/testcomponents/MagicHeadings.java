package blynx.thymeleaf.compositiondialect.testcomponents;

import blynx.thymeleaf.compositiondialect.CompositionComponent;
import blynx.thymeleaf.compositiondialect.CompositionComponentContext;

public class MagicHeadings extends CompositionComponent {

    private final int level;

    public MagicHeadings(CompositionComponentContext context) {
        super(context);
        // Read the parent's level BEFORE registering ourselves, so nested instances increment.
        int parentLevel = context.variable("parentMagicHeadings") instanceof MagicHeadings parent
                ? parent.getLevel()
                : 0;
        this.level = parentLevel + 1;
        context.setVariable("parentMagicHeadings", this);
    }

    public int getLevel() {
        return level;
    }
}
