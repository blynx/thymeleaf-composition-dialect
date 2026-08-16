package blynx.thymeleaf.compositiondialect;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares where a component's template lives. The template path a component's fragment is loaded from is
 * {@code pathPrefix/path/tagName}, joined by {@link ComponentRegistry}; either part may be left unset (the
 * default), in which case it contributes no extra path segment at all.
 *
 * <p>Usable on the component class itself and on a {@code package-info.java}, so a shared
 * {@code pathPrefix} can be declared once for every component in a package instead of via a shared
 * abstract base — which a record, being final, could never extend anyway. The class-level value wins when
 * set; otherwise the package-level value applies.
 *
 * <pre>{@code
 * // package-info.java — applies to every component in this package
 * @Composition(pathPrefix = "design-system")
 * package com.example.components.alerts;
 * }</pre>
 *
 * <pre>{@code
 * @Composition(path = "alerts")
 * public record Alert(Level level, CompositionComponentContext context) implements CompositionComponent { }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.PACKAGE})
public @interface Composition {

    /** The component's own sub-path, or {@code ""} (the default) for none. */
    String path() default "";

    /**
     * The shared path prefix, or {@code ""} (the default) for none. A value set here on the type wins over
     * one declared on the package.
     */
    String pathPrefix() default "";
}
