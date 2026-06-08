package blynx.thymeleaf.compositiondialect

import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.thymeleaf.ThymeleafAutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean

@AutoConfiguration(after = [ThymeleafAutoConfiguration::class])
@EnableConfigurationProperties(CompositionDialectProperties::class)
class CompositionDialectAutoConfiguration(private val properties: CompositionDialectProperties) {

    @Bean
    @ConditionalOnMissingBean
    fun compositionDialect(): CompositionDialect = CompositionDialect(
        componentPackage = properties.componentPackage,
        componentsPath = properties.componentsPath,
        prefix = properties.prefix,
    )
}
