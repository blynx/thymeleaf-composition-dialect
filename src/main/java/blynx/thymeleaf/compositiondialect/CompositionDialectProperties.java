package blynx.thymeleaf.compositiondialect;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration bound from {@code thymeleaf.composition.*}. {@code componentPackage} is required;
 * {@code componentsPath} is optional (null when absent); {@code prefix} defaults to {@code "c"}.
 */
@ConfigurationProperties("thymeleaf.composition")
public record CompositionDialectProperties(
        String componentPackage,
        String componentsPath,
        @DefaultValue("c") String prefix) {
}
