package blynx.thymeleaf.compositiondialect.casescomponents;

import blynx.thymeleaf.compositiondialect.CompositionComponent;
import blynx.thymeleaf.compositiondialect.CompositionComponentContext;

/** Pulls a plain Thymeleaf fragment into its own template, for the insertion cases. */
public class Inserter extends CompositionComponent {

    private final String title;

    public Inserter(CompositionComponentContext context) {
        super(context);
        Object raw = context.attributes().get("title");
        this.title = raw != null ? raw.toString() : "untitled";
    }

    public String getTitle() {
        return title;
    }
}
