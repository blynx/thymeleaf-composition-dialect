package blynx.thymeleaf.compositiondialect;

import java.util.Map;

/**
 * Base class every composition component extends. Subclasses take a
 * {@link CompositionComponentContext} in their single public constructor.
 *
 * <p>The {@code path} field is read reflectively via {@code getField("path")} to build the
 * component's template path; subclasses may shadow it with their own
 * {@code public static final String path} to override the sub-path.
 */
public class CompositionComponent {

    public static final String DEFAULT_SLOT = "";
    public static final String path = "";

    private final CompositionComponentContext componentContext;

    public CompositionComponent(CompositionComponentContext componentContext) {
        this.componentContext = componentContext;
    }

    public Map<String, Object> getRestAttributes() {
        return componentContext.attributes().rest();
    }

    public boolean hasSlot() {
        return hasSlot(DEFAULT_SLOT);
    }

    public boolean hasSlot(String slotName) {
        return componentContext.slotNames().contains(slotName);
    }
}
