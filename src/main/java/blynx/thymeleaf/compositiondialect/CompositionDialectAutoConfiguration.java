package blynx.thymeleaf.compositiondialect;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.thymeleaf.ThymeleafAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.thymeleaf.TemplateEngine;

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
            sources.add(new ComponentSource(properties.componentPackage(), properties.templatesPath()));
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

    /**
     * Checks every component's template exists, once, after the engine has been built.
     *
     * <p>A {@link SmartInitializingSingleton} rather than an {@code ApplicationRunner}: it runs as part of
     * bringing the context up, so a slice test that never runs the application still gets the check, and a
     * missing template fails startup rather than the first request that happens to need it. The engine is
     * taken as an {@link ObjectProvider} so that asking for it does not pull it into existence early, and
     * so that a context without one — this library's own auto-configuration tests, for instance — simply
     * has nothing to verify against.
     */
    @Bean
    public SmartInitializingSingleton compositionTemplateVerifier(
            ObjectProvider<TemplateEngine> templateEngines, ComponentRegistry componentRegistry) {
        return () -> {
            if (!properties.verifyTemplates()) {
                return;
            }
            TemplateEngine engine = templateEngines.getIfAvailable();
            if (engine != null) {
                componentRegistry.requireResolvableTemplates(engine.getConfiguration());
            }
        };
    }
}
