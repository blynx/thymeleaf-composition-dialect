package blynx.thymeleaf.compositiondialect;

import java.util.Map;
import java.util.Set;

/**
 * Base class every composition component extends. Subclasses take a
 * {@link CompositionComponentContext} in their single public constructor.
 *
 * <p>{@code path}, {@code pathPrefix} and {@code props} are all read reflectively via
 * {@code getField(...)}. {@code path}/{@code pathPrefix} build the component's template path, joined as
 * {@code pathPrefix/path/tagName}; they differ in how they interact with inheritance — a subclass that
 * redeclares {@code path} shadows the parent's value entirely (Java field hiding), so it does not compose
 * across levels, while {@code pathPrefix} is meant to be declared once on a shared abstract base instead,
 * composing down to every subclass that leaves it undeclared.
 *
 * <p>{@code props} declares which attributes this component consumes as its own, by their plain name
 * (e.g. {@code "variant"}, whether the caller wrote {@code variant="..."} or {@code c:variant="..."}).
 * Reading an attribute via {@link CompositionComponentContext#attributes()} never by itself excludes it
 * from {@link #getRestAttributes()}/{@code c:rest} — only declaring it in {@code props} does, so a
 * component can read an attribute (to validate, log, or derive from it) while still letting it fall
 * through to {@code c:rest}.
 */
public class CompositionComponent {

    public static final String DEFAULT_SLOT = "";
    public static final String path = "";
    public static final String pathPrefix = "";
    public static final Set<String> props = Set.of();

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
