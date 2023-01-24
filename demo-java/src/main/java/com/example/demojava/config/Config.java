package com.example.demojava.config;

import blynx.thymeleaf.compositiondialect.CompositionDialect;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Config {

    @Bean
    public CompositionDialect CompositionDialect() {
        return new CompositionDialect("com.example.demojava.components", "components");
    }

}
