package blynx.thymeleaf.compositiondialect;

import java.util.Set;

/**
 * Immutable description of a single component the dialect knows about, produced by
 * {@link ComponentRegistry} from the startup scan. Carries the identity ({@code componentClass}) and
 * everything derived from it — the dialect {@code prefix} it is registered under, its kebab-case
 * {@code tagName}, the {@code templatePath} its fragment is loaded from, and the {@code props} it
 * declares — resolved once here rather than per render, so a component's declared attributes are
 * introspectable without rendering it.
 */
public record ComponentDescriptor(
        Class<? extends CompositionComponent> componentClass,
        String prefix,
        String tagName,
        String templatePath,
        Set<String> props) {

    /** {@code prefix:tagName} — handy for collision messages and playground display. */
    public String qualifiedName() {
        return prefix + ":" + tagName;
    }
}
