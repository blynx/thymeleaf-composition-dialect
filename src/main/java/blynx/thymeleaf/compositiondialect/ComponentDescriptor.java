package blynx.thymeleaf.compositiondialect;

/**
 * Immutable description of a single component the dialect knows about, produced by
 * {@link ComponentRegistry} from the startup scan. Carries the identity ({@code componentClass}) and
 * everything derived from it — the dialect {@code prefix} it is registered under, its kebab-case
 * {@code tagName}, and the {@code templatePath} its fragment is loaded from.
 */
public record ComponentDescriptor(
        Class<? extends CompositionComponent> componentClass,
        String prefix,
        String tagName,
        String templatePath) {

    /** {@code prefix:tagName} — handy for collision messages and playground display. */
    public String qualifiedName() {
        return prefix + ":" + tagName;
    }
}
