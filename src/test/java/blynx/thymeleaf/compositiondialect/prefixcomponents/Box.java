package blynx.thymeleaf.compositiondialect.prefixcomponents;

import blynx.thymeleaf.compositiondialect.CompositionComponent;
import blynx.thymeleaf.compositiondialect.CompositionComponentContext;

/** A named slot and a default one, under the {@code x} prefix — the counterpart to {@code c:layout}. */
public record Box(String title, CompositionComponentContext context) implements CompositionComponent {

    public Box {
        title = title != null ? title : "untitled";
    }
}
