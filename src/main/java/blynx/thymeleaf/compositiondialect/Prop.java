package blynx.thymeleaf.compositiondialect;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a field as one of a component's props — the attributes it consumes as its own, as opposed to
 * the caller's own HTML attributes. Declaring a field this way is what excludes it from
 * {@link CompositionComponent#restAttributes()}/{@code c:rest} — not reading it.
 *
 * <p>Only meaningful on a class-based component: a record's components are its props already, one-to-one,
 * with no annotation needed — see {@link ComponentRegistry}. Placed on a record component instead, this
 * only overrides the attribute name that component binds from (see {@link #value()}); it plays no part in
 * deciding which record components are props, since all of them (other than the one typed
 * {@link CompositionComponentContext}) already are.
 *
 * <p>The attribute name defaults to the field's own name, kebab-cased (so {@code autoHideSeconds} reads the
 * {@code auto-hide-seconds} attribute); {@link #value()} overrides that for a name the derivation doesn't fit.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Prop {

    /** The attribute name this prop binds from, or {@code ""} to derive it from the field's own name. */
    String value() default "";
}
