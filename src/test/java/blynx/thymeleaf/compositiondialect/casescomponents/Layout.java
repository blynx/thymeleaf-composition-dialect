package blynx.thymeleaf.compositiondialect.casescomponents;

import blynx.thymeleaf.compositiondialect.CompositionComponent;
import blynx.thymeleaf.compositiondialect.CompositionComponentContext;

/** A page shell with named slots. Carries a {@code title} so it is visible whose title a case reads. */
public class Layout extends CompositionComponent {

    private final String title;

    public Layout(CompositionComponentContext context) {
        super(context);
        Object raw = context.attributes().get("title");
        this.title = raw != null ? raw.toString() : "untitled";
    }

    public String getTitle() {
        return title;
    }
}
