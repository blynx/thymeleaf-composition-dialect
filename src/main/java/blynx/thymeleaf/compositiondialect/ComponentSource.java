package blynx.thymeleaf.compositiondialect;

/**
 * One package of components to scan, together with the templates sub-path its templates live under.
 *
 * <p>A dialect is built from any number of these — typically the application's own components plus one
 * per imported component library. The templates path belongs to the source rather than to the dialect
 * because an imported library's templates sit wherever that library put them in its own jar, which has
 * nothing to do with where the application keeps its own.
 *
 * <pre>{@code
 * new CompositionDialect(List.of(
 *         new ComponentSource("com.example.app.components", "components"),
 *         new ComponentSource("com.example.designsystem.components", "designsystem")));
 * }</pre>
 *
 * <p>Every component of every source shares the one dialect prefix, so a library keeps its components
 * apart by naming its classes for itself — {@code DsButton} rather than {@code Button}, giving
 * {@code <c:ds-button>}. Nothing here enforces that; {@link ComponentRegistry#requireNoCollisions()}
 * reports the clash if a library neglects it and the application happens to own the same tag.
 */
public record ComponentSource(String componentPackage, String componentsPath) {

    /** A source whose templates sit directly under the Thymeleaf templates root. */
    public ComponentSource(String componentPackage) {
        this(componentPackage, null);
    }

    public ComponentSource {
        if (componentPackage == null || componentPackage.isBlank()) {
            throw new IllegalArgumentException(
                    CompositionDialect.DIALECT_NAME + ": a component source needs a package to scan");
        }
    }
}
