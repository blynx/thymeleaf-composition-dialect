package blynx.thymeleaf.compositiondialect.testcomponents;

import blynx.thymeleaf.compositiondialect.CompositionComponent;
import blynx.thymeleaf.compositiondialect.CompositionComponentContext;

public class Heading extends CompositionComponent {

    private final int level;

    public Heading(CompositionComponentContext context) {
        super(context);
        this.level = resolveLevel(context);
    }

    private static int resolveLevel(CompositionComponentContext context) {
        Object rawLevel = context.attributes().get("level");
        if (rawLevel != null) {
            try {
                return Integer.parseInt(rawLevel.toString());
            } catch (NumberFormatException ignored) {
                // not a number: fall back to the inherited / default level
            }
        }
        if (context.variable("parentMagicHeadings") instanceof MagicHeadings parent) {
            return parent.getLevel();
        }
        return 1;
    }

    public int getLevel() {
        return level;
    }
}
