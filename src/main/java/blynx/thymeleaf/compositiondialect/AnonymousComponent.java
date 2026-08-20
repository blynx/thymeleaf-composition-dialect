package blynx.thymeleaf.compositiondialect;

import java.util.AbstractMap;
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
 * <p>Not yet constructed anywhere: introduced ahead of Stage 4 (classless components) so the {@code this}
 * access path is proven in both expression languages before anything on the render path depends on it. See
 * {@code _local-notes/TEMPLATE-FIRST-COMPONENTS.md}, "{@code ${this}} is always published".
 */
final class AnonymousComponent extends AbstractMap<String, Object> implements CompositionComponent {

    private final Map<String, Object> props;
    private final CompositionComponentContext context;

    AnonymousComponent(Map<String, Object> props, CompositionComponentContext context) {
        this.props = props;
        this.context = context;
    }

    @Override
    public CompositionComponentContext context() {
        return context;
    }

    @Override
    public Set<Entry<String, Object>> entrySet() {
        return props.entrySet();
    }
}
