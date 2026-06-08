package blynx.thymeleaf.compositiondialect

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("thymeleaf.composition")
data class CompositionDialectProperties(
    val componentPackage: String,
    val componentsPath: String? = null,
    val prefix: String = "c",
)
