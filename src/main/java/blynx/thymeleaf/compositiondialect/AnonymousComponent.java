package blynx.thymeleaf.compositiondialect;

import java.util.AbstractMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The component {@code this} resolves to for a template with no bound class. Its declared props are the
 * map's own entries, so {@code ${this.title}} resolves as a map property read rather than needing a
 * bean-style getter per prop — OGNL reads {@code Map} properties natively, and SpEL does the same once a
 * {@code MapAccessor} is registered, which {@code thymeleaf-spring6}'s {@code SpringStandardDialect} does.
 *
 * <p>Extending {@link AbstractMap} rather than merely implementing {@link Map} is what makes {@code this}
 * simultaneously an {@code instanceof CompositionComponent} (so {@code c:rest} and {@code hasSlot()} keep
 * working, both inherited unmodified) and an {@code instanceof Map} (so property access resolves).
 *
 * <p>Being a {@code Map} has one sharp edge: OGNL and SpEL both resolve <em>bare</em> property syntax
 * (e.g. {@code this.hasSlot}, no parens) against a {@code Map} by calling {@code get(name)} directly,
 * before ever considering a same-named method — unlike on a normal (non-{@code Map}) object, where a
 * no-arg method is tried as a property-read fallback. Left alone, that makes {@code ${this.hasSlot}} and
 * {@code ${this.restAttributes}} silently resolve to {@code null} instead of calling the real interface
 * default methods, even though the parenthesised form ({@code ${this.hasSlot()}}) works fine. The
 * constructor closes that gap by seeding the backing map with those two computed values under their own
 * names, so bare property syntax reaches the same answer the method call would — a declared prop of the
 * same name (unlikely, but possible) still wins, since it is merged in afterwards.
 */
final class AnonymousComponent extends AbstractMap<String, Object> implements CompositionComponent {

    private final Map<String, Object> entries;
    private final CompositionComponentContext context;

    AnonymousComponent(Map<String, Object> props, CompositionComponentContext context) {
        this.context = context;
        Map<String, Object> merged = new LinkedHashMap<>();
        merged.put("hasSlot", context.slotNames().contains(CompositionComponent.DEFAULT_SLOT));
        merged.put("restAttributes", context.attributes().rest());
        merged.putAll(props);
        this.entries = merged;
    }

    @Override
    public CompositionComponentContext context() {
        return context;
    }

    @Override
    public Set<Entry<String, Object>> entrySet() {
        return entries.entrySet();
    }
}
