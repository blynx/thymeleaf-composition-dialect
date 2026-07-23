package blynx.thymeleaf.compositiondialect;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import org.reflections.Reflections;
import org.thymeleaf.dialect.AbstractProcessorDialect;
import org.thymeleaf.processor.IProcessor;
import org.thymeleaf.standard.StandardDialect;

/**
 * Thymeleaf dialect that registers one element processor per {@link CompositionComponent}
 * subclass found in {@code componentPackage}, plus the {@code c:rest} attribute processor.
 */
public class CompositionDialect extends AbstractProcessorDialect {

    public static final String DIALECT_NAME = "Composition Dialect";
    private static final String DIALECT_PREFIX = "c";

    private final String componentPackage;
    private final String componentsPath;

    public CompositionDialect(String componentPackage) {
        this(componentPackage, null);
    }

    public CompositionDialect(String componentPackage, String componentsPath) {
        this(componentPackage, componentsPath, DIALECT_NAME);
    }

    public CompositionDialect(String componentPackage, String componentsPath, String name) {
        this(componentPackage, componentsPath, name, DIALECT_PREFIX);
    }

    public CompositionDialect(String componentPackage, String componentsPath, String name, String prefix) {
        this(componentPackage, componentsPath, name, prefix, StandardDialect.PROCESSOR_PRECEDENCE);
    }

    public CompositionDialect(String componentPackage, String componentsPath, String name, String prefix,
                              int processorPrecedence) {
        super(name, prefix, processorPrecedence);
        this.componentPackage = componentPackage;
        this.componentsPath = componentsPath;
    }

    @Override
    public Set<IProcessor> getProcessors(String dialectPrefix) {
        Set<IProcessor> processors = new HashSet<>();
        for (Class<? extends CompositionComponent> componentClass :
                new Reflections(componentPackage).getSubTypesOf(CompositionComponent.class)) {
            String tagName = componentClass.getSimpleName()
                    .replaceAll("(?!^)(?=[A-Z][a-z])", "-")
                    .toLowerCase(Locale.ROOT);
            processors.add(new CompositionElementModelProcessor(dialectPrefix, tagName, componentClass, componentsPath));
        }
        processors.add(new CompositionRestAttributesTagProcessor(dialectPrefix));
        return processors;
    }
}
