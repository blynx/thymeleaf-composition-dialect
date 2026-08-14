package blynx.thymeleaf.compositiondialect;

/**
 * One level of component nesting while rendering: the component whose template is being rendered, and the
 * frame it was reached from. Published as a local variable alongside {@code this} so that
 * {@link CompositionCallerProcessor} can put {@code this} back to what it was outside the component —
 * which is what slot content needs, having been written out there.
 *
 * <p>A linked frame rather than a list: one small object per component occurrence, and unwinding a level
 * is a field read.
 */
record ComponentFrame(CompositionComponent component, ComponentFrame caller) {

    /** Local variable holding the current frame. Internal; nothing outside the dialect reads it. */
    static final String VARIABLE = "__compositionFrame__";

    /** The frame in effect where this component's tag was written, or {@code null} at template level. */
    static ComponentFrame callerOf(Object frame) {
        return frame instanceof ComponentFrame componentFrame ? componentFrame.caller() : null;
    }
}
