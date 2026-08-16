package blynx.thymeleaf.compositiondialect;

/**
 * Convenience base for a class-based component: takes the {@link CompositionComponentContext} in its
 * constructor and implements {@link CompositionComponent#context()} from it, exactly as
 * {@code CompositionComponent} itself did back when it was a class rather than an interface. Not required —
 * any class may {@code implements CompositionComponent} directly and provide {@code context()} itself —
 * but this is the shape most class-based components want.
 *
 * <p>A record needs neither this nor a hand-written {@code context()}: a record component named
 * {@code context} of type {@link CompositionComponentContext} satisfies the interface method on its own.
 */
public abstract class AbstractCompositionComponent implements CompositionComponent {

    private final CompositionComponentContext context;

    protected AbstractCompositionComponent(CompositionComponentContext context) {
        this.context = context;
    }

    @Override
    public CompositionComponentContext context() {
        return context;
    }
}
