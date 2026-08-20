package blynx.thymeleaf.compositiondialect;

/**
 * One templates root to discover components under, together with the optional package a bare (non-dotted
 * or relatively-dotted) {@code impl} resolves against.
 *
 * <p>A dialect is built from any number of these — typically the application's own components plus one per
 * imported component library. Discovery walks {@code templatesPath} for {@code *.html} files carrying a
 * {@code <c:component>} declaration; the tag name is the file's own basename, never derived from a class.
 * {@code defaultPackage} is purely a resolution shorthand for {@code impl} — see {@code <c:component
 * impl="…"/>} in the reference docs — and a source with no classes at all (a pure-HTML pattern library)
 * simply omits it.
 *
 * <pre>{@code
 * new CompositionDialect(List.of(
 *         new ComponentSource("components", "com.example.app.components"),
 *         new ComponentSource("designsystem", "com.example.designsystem.components")));
 * }</pre>
 *
 * <p>Every component of every source shares the one dialect prefix, so two sources whose templates both
 * produce the same tag name (same basename) collide; {@link ComponentRegistry#requireNoCollisions()}
 * reports it.
 */
public record ComponentSource(String templatesPath, String defaultPackage) {

    /** A source with no default package — every {@code impl} in it must be a fully-qualified name. */
    public ComponentSource(String templatesPath) {
        this(templatesPath, null);
    }

    public ComponentSource {
        if (templatesPath == null || templatesPath.isBlank()) {
            throw new IllegalArgumentException(
                    CompositionDialect.DIALECT_NAME + ": a component source needs a templates path to scan");
        }
    }
}
