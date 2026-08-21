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
 * default methods, even though the parenthesised form ({@code ${this.hasSlot()}}) works fine. {@link #get}
 * and {@link #containsKey} close that gap by answering those two names from the interface methods
 * themselves, so bare property syntax reaches the same answer the method call would — a declared prop of
 * the same name (unlikely, but possible) still wins, being looked up first.
 *
 * <p>{@code get}, {@code containsKey} and {@code entrySet} are all overridden rather than left to
 * {@link AbstractMap}, which derives every one of them from {@code entrySet} — so each single prop read
 * would walk the whole entry set, and every occurrence would compute {@code restAttributes} whether its
 * template mentions it or not. The merged entry set is what {@code entrySet} still hands out, built on
 * demand for whoever iterates {@code this} as a map, which no prop read does.
 */
final class AnonymousComponent extends AbstractMap<String, Object> implements CompositionComponent {

    private static final String HAS_SLOT = "hasSlot";
    private static final String REST_ATTRIBUTES = "restAttributes";

    private final Map<String, Object> props;
    private final CompositionComponentContext context;

    /** The declared props plus the two computed names, built the first time {@link #entrySet} is asked. */
    private Map<String, Object> merged;

    AnonymousComponent(Map<String, Object> props, CompositionComponentContext context) {
        this.props = props;
        this.context = context;
    }

    @Override
    public CompositionComponentContext context() {
        return context;
    }

    @Override
    public Object get(Object key) {
        Object value = props.get(key);
        if (value != null) {
            return value;
        }
        // Only now containsKey, and only on the miss: a declared prop given no value resolves to null and
        // still owns its name — including when that name is one of the two computed ones below — but every
        // prop that does have a value has already been answered in one lookup.
        if (props.containsKey(key)) {
            return null;
        }
        if (HAS_SLOT.equals(key)) {
            return hasSlot();
        }
        if (REST_ATTRIBUTES.equals(key)) {
            return restAttributes();
        }
        return null;
    }

    @Override
    public boolean containsKey(Object key) {
        return props.containsKey(key) || HAS_SLOT.equals(key) || REST_ATTRIBUTES.equals(key);
    }

    @Override
    public Set<Entry<String, Object>> entrySet() {
        Map<String, Object> entries = merged;
        if (entries == null) {
            entries = new LinkedHashMap<>();
            entries.put(HAS_SLOT, hasSlot());
            entries.put(REST_ATTRIBUTES, restAttributes());
            entries.putAll(props);
            merged = entries;
        }
        return entries.entrySet();
    }
}
