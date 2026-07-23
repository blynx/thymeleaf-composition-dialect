package blynx.thymeleaf.compositiondialect.testcomponents;

import blynx.thymeleaf.compositiondialect.CompositionComponent;
import blynx.thymeleaf.compositiondialect.CompositionComponentContext;

public class Label extends CompositionComponent {

    private final String text;
    private final Object value;

    public Label(CompositionComponentContext context) {
        super(context);
        Object rawText = context.attributes().get("text");
        this.text = rawText != null ? rawText.toString() : null;
        this.value = context.attributes().get("value");
    }

    public String getText() {
        return text;
    }

    public Object getValue() {
        return value;
    }
}
