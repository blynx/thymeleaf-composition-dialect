package blynx.thymeleaf.compositiondialect;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * The context object handed to every component's constructor. It exposes the resolved
 * attributes, slot names and locale, and bridges message resolution and template-variable
 * read/write back into the surrounding Thymeleaf template.
 */
public final class CompositionComponentContext {

    private final ComponentAttributes attributes;
    private final Set<String> slotNames;
    private final Locale locale;
    private final BiFunction<String, Object[], String> messageResolver;
    private final Function<String, Object> variableReader;
    private final BiConsumer<String, Object> variableWriter;

    public CompositionComponentContext(
            ComponentAttributes attributes,
            Set<String> slotNames,
            Locale locale,
            BiFunction<String, Object[], String> messageResolver,
            Function<String, Object> variableReader,
            BiConsumer<String, Object> variableWriter) {
        this.attributes = attributes;
        this.slotNames = slotNames;
        this.locale = locale;
        this.messageResolver = messageResolver;
        this.variableReader = variableReader;
        this.variableWriter = variableWriter;
    }

    public ComponentAttributes attributes() {
        return attributes;
    }

    public Set<String> slotNames() {
        return slotNames;
    }

    public Locale locale() {
        return locale;
    }

    public String message(String code, Object... params) {
        return Objects.requireNonNullElse(messageResolver.apply(code, params), code);
    }

    public Object variable(String name) {
        return variableReader.apply(name);
    }

    public void setVariable(String name, Object value) {
        variableWriter.accept(name, value);
    }
}
