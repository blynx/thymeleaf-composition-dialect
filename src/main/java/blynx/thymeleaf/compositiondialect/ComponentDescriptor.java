package blynx.thymeleaf.compositiondialect;

import java.util.Optional;
import java.util.Set;

/**
 * Immutable description of a single component the dialect knows about, produced by {@link
 * ComponentRegistry} from the startup template walk. The template — {@code templatePath}, resolved from the
 * file that declared it — is the identity; {@code componentClass} is present only when the template's
 * {@code <c:component>} declaration named one with {@code impl}, absent for a classless component. Also
 * carries the {@link ComponentSource} it was found under, the dialect {@code prefix} it is registered
 * under, its kebab-case {@code tagName} (the template's own basename), and the {@code props} it declares —
 * resolved once here rather than per render, so a component's declared attributes are introspectable
 * without rendering it.
 *
 * <p>{@code source} is what tells one module's components from another's once several are aggregated:
 * every component shares the one dialect prefix, so the prefix no longer distinguishes them. Collision
 * messages name it, and it is the grouping a component playground would list by.
 */
public record ComponentDescriptor(
        Optional<Class<? extends CompositionComponent>> componentClass,
        ComponentSource source,
        String prefix,
        String tagName,
        String templatePath,
        Set<String> props) {

    /** {@code prefix:tagName} — handy for collision messages and playground display. */
    public String qualifiedName() {
        return prefix + ":" + tagName;
    }
}
