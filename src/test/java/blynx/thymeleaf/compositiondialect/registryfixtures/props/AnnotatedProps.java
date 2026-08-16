package blynx.thymeleaf.compositiondialect.registryfixtures.props;

import blynx.thymeleaf.compositiondialect.AbstractCompositionComponent;
import blynx.thymeleaf.compositiondialect.CompositionComponentContext;
import blynx.thymeleaf.compositiondialect.Prop;

/** A class declaring props via {@link Prop}: one plain (kebab-derived), one name-overridden. */
public class AnnotatedProps extends AbstractCompositionComponent {

    @Prop
    private final String variant;

    @Prop("data-size")
    private final String size;

    public AnnotatedProps(CompositionComponentContext context) {
        super(context);
        this.variant = String.valueOf(context.attributes().get("variant"));
        this.size = String.valueOf(context.attributes().get("data-size"));
    }
}
