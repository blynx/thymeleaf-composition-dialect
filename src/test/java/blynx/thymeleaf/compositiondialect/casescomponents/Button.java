package blynx.thymeleaf.compositiondialect.casescomponents;

import java.util.Set;

import blynx.thymeleaf.compositiondialect.CompositionComponent;
import blynx.thymeleaf.compositiondialect.CompositionComponentContext;

/** Declares "variant" as a prop, excluded from {@code c:rest}; reads "type" without declaring it, so
 * it's still spread by {@code c:rest} like any attribute the component never touches. */
public class Button extends CompositionComponent {

    public static final Set<String> props = Set.of("variant");

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
