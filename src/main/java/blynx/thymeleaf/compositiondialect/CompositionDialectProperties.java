package blynx.thymeleaf.compositiondialect;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration bound from {@code thymeleaf.composition.*}, describing the application's own components.
 * {@code componentPackage} may be absent when the application has none of its own and only imports
 * component libraries, which contribute their own {@link ComponentSource} beans; {@code componentsPath} is
 * optional (null when absent); {@code prefix} defaults to {@code "c"} and covers every source.
 */
@ConfigurationProperties("thymeleaf.composition")
public record CompositionDialectProperties(
        String componentPackage,
        String componentsPath,
        @DefaultValue("c") String prefix) {
}
