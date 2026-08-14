package blynx.thymeleaf.compositiondialect;

import java.util.Map;

/**
 * Base class every composition component extends. Subclasses take a
 * {@link CompositionComponentContext} in their single public constructor.
 *
 * <p>{@code path} and {@code pathPrefix} are both read reflectively via {@code getField(...)} to build the
 * component's template path, joined as {@code pathPrefix/path/tagName}. They differ in how they interact
 * with inheritance: a subclass that redeclares {@code path} shadows the parent's value entirely (Java field
 * hiding), so it does not compose across levels. {@code pathPrefix} is meant to be declared once on a
 * shared abstract base instead — every concrete subclass that leaves it undeclared inherits it as-is and
 * only needs its own {@code path} for the rest.
 */
public class CompositionComponent {

    public static final String DEFAULT_SLOT = "";
    public static final String path = "";
    public static final String pathPrefix = "";

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
