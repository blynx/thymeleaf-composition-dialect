package blynx.thymeleaf.compositiondialect;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration bound from {@code thymeleaf.composition.*}, describing the application's own components.
 * {@code templatesPath} may be absent when the application has none of its own and only imports component
 * libraries, which contribute their own {@link ComponentSource} beans — it is the trigger for whether the
 * application contributes a source at all, since discovery walks templates rather than a package.
 * {@code defaultPackage} is optional, used only to resolve a bare or relatively-dotted {@code impl} in a
 * {@code <c:component>} declaration; {@code prefix} defaults to {@code "c"} and covers every source.
 *
 * <p>{@code verifyTemplates} decides whether every component's template is checked against the
 * application's actual resolver chain once the engine is built, which turns a resolver misconfiguration
 * into one startup failure listing all of them instead of a render failure the first time that one
 * component is used. On by default; the escape hatch is for a resolver that only finds its templates later
 * than startup.
 */
@ConfigurationProperties("thymeleaf.composition")
public record CompositionDialectProperties(
        String templatesPath,
        String defaultPackage,
        @DefaultValue("c") String prefix,
        @DefaultValue("true") boolean verifyTemplates) {
}
