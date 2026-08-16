package blynx.thymeleaf.compositiondialect.casescomponents;

import blynx.thymeleaf.compositiondialect.CompositionComponent;
import blynx.thymeleaf.compositiondialect.CompositionComponentContext;

/** Pulls a plain Thymeleaf fragment into its own template, for the insertion cases. */
public record Inserter(String title, CompositionComponentContext context) implements CompositionComponent {

    public Inserter {
        title = title != null ? title : "untitled";
    }
}
