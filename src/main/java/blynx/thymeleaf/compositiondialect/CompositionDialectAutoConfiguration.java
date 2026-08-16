package blynx.thymeleaf.compositiondialect;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.thymeleaf.ThymeleafAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Builds the one dialect from the application's own components plus every imported component library's.
 *
 * <p>A library contributes by publishing a {@link ComponentSource} bean from its own auto-configuration,
 * not a {@link CompositionDialect} bean — those are collected here and scanned together. A library
 * publishing its own dialect instead would trip {@code @ConditionalOnMissingBean} below and silently
 * suppress the application's own components, and two dialects sharing a prefix would register the
 * dialect's grammar processors twice over.
 *
 * <pre>{@code
 * // in the component library's own @AutoConfiguration
 * @Bean
 * ComponentSource designSystemComponents() {
 *     return new ComponentSource("com.example.designsystem.components", "designsystem");
 * }
 * }</pre>
 */
@AutoConfiguration(after = ThymeleafAutoConfiguration.class)
@EnableConfigurationProperties(CompositionDialectProperties.class)
public class CompositionDialectAutoConfiguration {

    private final CompositionDialectProperties properties;

    public CompositionDialectAutoConfiguration(CompositionDialectProperties properties) {
        this.properties = properties;
    }

    @Bean
    @ConditionalOnMissingBean
    public CompositionDialect compositionDialect(ObjectProvider<ComponentSource> librarySources) {
        List<ComponentSource> sources = new ArrayList<>();
        // Absent when the application has no components of its own and only imports libraries, which is a
        // perfectly ordinary way to use a design system.
        if (properties.componentPackage() != null && !properties.componentPackage().isBlank()) {
            sources.add(new ComponentSource(properties.componentPackage(), properties.componentsPath()));
        }
        librarySources.orderedStream().forEach(sources::add);
        if (sources.isEmpty()) {
            throw new IllegalStateException(CompositionDialect.DIALECT_NAME
                    + ": no components to scan — set thymeleaf.composition.component-package to your own "
                    + "component package, or import a component library that publishes a "
                    + ComponentSource.class.getSimpleName() + " bean.");
        }
        return new CompositionDialect(sources, CompositionDialect.DIALECT_NAME, properties.prefix());
    }

    @Bean
    @ConditionalOnMissingBean
    public ComponentRegistry componentRegistry(List<CompositionDialect> dialects) {
        return ComponentRegistry.aggregate(dialects.stream().map(CompositionDialect::getRegistry).toList());
    }
}
