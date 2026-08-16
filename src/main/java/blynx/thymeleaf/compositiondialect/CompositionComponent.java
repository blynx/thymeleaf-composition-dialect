package blynx.thymeleaf.compositiondialect;

import java.util.Map;

/**
 * Every composition component implements this — a record whose components are its context and its props,
 * or a plain class implementing it directly (see {@link AbstractCompositionComponent} for the latter's
 * usual boilerplate). Either way, the component exposes its {@link CompositionComponentContext} via
 * {@link #context()}; everything else here is a default method built on top of it.
 *
 * <p>A component's template path is declared with {@link Composition}; a class's props are declared with
 * {@link Prop} on its fields; a record's props are its record components, one for one, needing no
 * declaration at all.
 */
public interface CompositionComponent {

    String DEFAULT_SLOT = "";

    /** This component occurrence's context — attributes, slot names, locale, messages, variables. */
    CompositionComponentContext context();

    default Map<String, Object> restAttributes() {
        return context().attributes().rest();
    }

    default boolean hasSlot() {
        return hasSlot(DEFAULT_SLOT);
    }

    default boolean hasSlot(String slotName) {
        return context().slotNames().contains(slotName);
    }
}
