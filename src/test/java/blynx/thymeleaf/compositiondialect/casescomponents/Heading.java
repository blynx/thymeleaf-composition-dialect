package blynx.thymeleaf.compositiondialect.casescomponents;

import blynx.thymeleaf.compositiondialect.CompositionComponent;
import blynx.thymeleaf.compositiondialect.CompositionComponentContext;

/**
 * Reports the level of the nearest enclosing {@link Outline} in the rendered document, unless it is given
 * an explicit {@code level} — an attribute the caller wrote beats a value the surroundings published.
 */
public class Heading extends CompositionComponent {

    private final int level;

    public Heading(CompositionComponentContext context) {
        super(context);
        Object explicit = context.attributes().get("level");
        if (explicit != null) {
            this.level = Integer.parseInt(explicit.toString());
        } else {
            this.level = context.variable("outline") instanceof Outline outline ? outline.getLevel() : 1;
        }
    }

    public int getLevel() {
        return level;
    }
}
