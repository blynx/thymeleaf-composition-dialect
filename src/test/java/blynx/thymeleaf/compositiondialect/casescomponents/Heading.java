package blynx.thymeleaf.compositiondialect.casescomponents;

import blynx.thymeleaf.compositiondialect.CompositionComponent;
import blynx.thymeleaf.compositiondialect.CompositionComponentContext;

/**
 * Reports the level of the nearest enclosing {@link Outline} in the rendered document, unless it is given
 * an explicit {@code level} — an attribute the caller wrote beats a value the surroundings published. A
 * boxed {@code Integer}, not {@code int}: absent must be distinguishable from an explicit {@code 0}.
 */
public record Heading(Integer level, CompositionComponentContext context) implements CompositionComponent {

    public Heading {
        if (level == null) {
            level = context.variable("outline") instanceof Outline outline ? outline.level() : 1;
        }
    }
}
