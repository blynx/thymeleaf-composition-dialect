package blynx.thymeleaf.compositiondialect.casescomponents;

import blynx.thymeleaf.compositiondialect.CompositionComponent;
import blynx.thymeleaf.compositiondialect.CompositionComponentContext;

/** Reads nothing, so its template can spread every attribute back out with {@code c:rest}. */
public class Relay extends CompositionComponent {

    private final String title;

    public Relay(CompositionComponentContext context) {
        super(context);
        Object raw = context.attributes().get("title");
        this.title = raw != null ? raw.toString() : "untitled";
    }

    public String getTitle() {
        return title;
    }
}
