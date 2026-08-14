package blynx.thymeleaf.compositiondialect.casescomponents;

import blynx.thymeleaf.compositiondialect.CompositionComponent;
import blynx.thymeleaf.compositiondialect.CompositionComponentContext;

/** Passes the content it is given straight on into another component's slot. */
public class Handoff extends CompositionComponent {

    private final String title;

    public Handoff(CompositionComponentContext context) {
        super(context);
        Object raw = context.attributes().get("title");
        this.title = raw != null ? raw.toString() : "untitled";
    }

    public String getTitle() {
        return title;
    }
}
