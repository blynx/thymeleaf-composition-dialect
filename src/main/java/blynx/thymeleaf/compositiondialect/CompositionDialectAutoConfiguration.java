package blynx.thymeleaf.compositiondialect;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.thymeleaf.ThymeleafAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = ThymeleafAutoConfiguration.class)
@EnableConfigurationProperties(CompositionDialectProperties.class)
public class CompositionDialectAutoConfiguration {

    private final CompositionDialectProperties properties;

    public CompositionDialectAutoConfiguration(CompositionDialectProperties properties) {
        this.properties = properties;
    }

    @Bean
    @ConditionalOnMissingBean
    public CompositionDialect compositionDialect() {
        return new CompositionDialect(
                properties.componentPackage(),
                properties.componentsPath(),
                CompositionDialect.DIALECT_NAME,
                properties.prefix());
    }
}
