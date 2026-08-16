package blynx.thymeleaf.compositiondialect.casescomponents;

import blynx.thymeleaf.compositiondialect.CompositionComponent;
import blynx.thymeleaf.compositiondialect.CompositionComponentContext;

/** Its own template spells the marker {@code <c-slot />}, to pin down identity-based marker recognition. */
public record DashSpelledSlot(CompositionComponentContext context) implements CompositionComponent {
}
