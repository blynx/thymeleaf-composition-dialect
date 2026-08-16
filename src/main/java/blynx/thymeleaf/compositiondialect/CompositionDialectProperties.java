package blynx.thymeleaf.compositiondialect;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration bound from {@code thymeleaf.composition.*}, describing the application's own components.
 * {@code componentPackage} may be absent when the application has none of its own and only imports
 * component libraries, which contribute their own {@link ComponentSource} beans; {@code templatesPath} is
 * optional (null when absent); {@code prefix} defaults to {@code "c"} and covers every source.
 *
 * <p>{@code verifyTemplates} decides whether every component's template is checked for existence once the
 * engine is built, which turns a missing or misnamed template file into one startup failure listing all of
 * them instead of a render failure the first time that one component is used. On by default; the escape
 * hatch is for a resolver that only finds its templates later than startup.
 */
@ConfigurationProperties("thymeleaf.composition")
public record CompositionDialectProperties(
        String componentPackage,
        String templatesPath,
        @DefaultValue("c") String prefix,
        @DefaultValue("true") boolean verifyTemplates) {
}
