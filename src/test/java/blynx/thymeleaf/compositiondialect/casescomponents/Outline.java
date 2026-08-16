package blynx.thymeleaf.compositiondialect.casescomponents;

import blynx.thymeleaf.compositiondialect.CompositionComponent;
import blynx.thymeleaf.compositiondialect.CompositionComponentContext;

/**
 * Publishes itself for its descendants to find, so nesting increments a level. The deliberately
 * <em>dynamic</em> counterpart to {@code ${this}}: what a heading inside reports depends on where it is
 * rendered, not on who wrote it.
 *
 * <p>{@code level} is a record component like any other prop, so an explicit {@code level} attribute on
 * the outline itself overrides the computed nesting value too — deliberately, mirroring how an explicit
 * {@code level} on {@link Heading} overrides what it would otherwise inherit. A nested outline with no
 * explicit level of its own still computes off whatever value the enclosing one is using, explicit or not.
 */
public record Outline(Integer level, CompositionComponentContext context) implements CompositionComponent {

    public Outline {
        if (level == null) {
            level = context.variable("outline") instanceof Outline parent ? parent.level() + 1 : 1;
        }
        context.setVariable("outline", this);
    }
}
