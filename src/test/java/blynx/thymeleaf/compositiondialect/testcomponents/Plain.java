package blynx.thymeleaf.compositiondialect.testcomponents;

import blynx.thymeleaf.compositiondialect.CompositionComponent;
import blynx.thymeleaf.compositiondialect.CompositionComponentContext;

public record Plain(CompositionComponentContext context) implements CompositionComponent {
}
