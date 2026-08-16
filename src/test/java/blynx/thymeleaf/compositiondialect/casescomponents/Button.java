package blynx.thymeleaf.compositiondialect.casescomponents;

import blynx.thymeleaf.compositiondialect.AbstractCompositionComponent;
import blynx.thymeleaf.compositiondialect.CompositionComponentContext;
import blynx.thymeleaf.compositiondialect.Prop;

/** Declares "variant" as a prop via {@link Prop}, excluded from {@code c:rest}; reads "type" without
 * declaring it, so it's still spread by {@code c:rest} like any attribute the component never touches. */
public class Button extends AbstractCompositionComponent {

    @Prop
    private final String variant;

    public Button(CompositionComponentContext context) {
        super(context);
        Object raw = context.attributes().get("variant");
        this.variant = raw != null ? raw.toString() : "primary";
        context.attributes().get("type");
    }

    public String getVariant() {
        return variant;
    }
}
