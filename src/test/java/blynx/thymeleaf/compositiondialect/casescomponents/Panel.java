package blynx.thymeleaf.compositiondialect.casescomponents;

import blynx.thymeleaf.compositiondialect.CompositionComponent;
import blynx.thymeleaf.compositiondialect.CompositionComponentContext;

/** A single default slot, for nesting cases. Carries a {@code title} of its own. */
public class Panel extends CompositionComponent {

    private final String title;

    public Panel(CompositionComponentContext context) {
        super(context);
        Object raw = context.attributes().get("title");
        this.title = raw != null ? raw.toString() : "untitled";
    }

    public String getTitle() {
        return title;
    }
}
