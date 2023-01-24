package com.example.demo.config

import blynx.thymeleaf.compositiondialect.CompositionDialect
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class Config {

    @Bean
    fun compositionDialect(): CompositionDialect {
        return CompositionDialect(
            componentPackage = "com.example.demo.components",
            componentsPath = "components"
        )
    }
}
