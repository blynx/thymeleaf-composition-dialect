package blynx.thymeleaf.compositiondialect.casescomponents;

import blynx.thymeleaf.compositiondialect.CompositionComponent;
import blynx.thymeleaf.compositiondialect.CompositionComponentContext;

/**
 * Publishes itself for its descendants to find, so nesting increments a level. The deliberately
 * <em>dynamic</em> counterpart to {@code ${this}}: what a heading inside reports depends on where it is
 * rendered, not on who wrote it.
 */
public class Outline extends CompositionComponent {

    private final int level;

    public Outline(CompositionComponentContext context) {
        super(context);
        int parentLevel = context.variable("outline") instanceof Outline parent ? parent.getLevel() : 0;
        this.level = parentLevel + 1;
        context.setVariable("outline", this);
    }

    public int getLevel() {
        return level;
    }
}
