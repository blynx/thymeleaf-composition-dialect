# Setup

## Standalone

Put your component templates under one path, relative to your Thymeleaf templates root:

```html
<!-- templates/components/button.html -->
<c:component impl="Button"/>
<button th:classappend="${'btn-' + this.variant}">
    <c:slot />
</button>
```

Give that path to the dialect:

```java
templateEngine.addDialect(new CompositionDialect("components"));
```

See [Components](components.md) for how to write a component.

### `CompositionDialect` parameters

| Parameter | Required | Description |
|---|---|---|
| `templatesPath` | yes | Sub-path under the Thymeleaf templates root to scan for components |
| `defaultPackage` | no | Package a bare or relatively-dotted `impl` resolves against |
| `prefix` | no | Tag prefix (default: `c`) |

## Spring Boot

Add the library to your dependencies. With Spring Boot auto-configuration, you do not need to declare the dialect as a bean. Set the templates path in `application.properties` instead:

```properties
thymeleaf.composition.templates-path=components
thymeleaf.composition.default-package=com.example.demo.components
```

| Property | Required | Description |
|---|---|---|
| `thymeleaf.composition.templates-path` | no | Sub-path under the templates root to scan — omit it if you only import component libraries |
| `thymeleaf.composition.default-package` | no | Package a bare or relatively-dotted `impl` resolves against |
| `thymeleaf.composition.prefix` | no | Tag prefix (default: `c`) |
| `thymeleaf.composition.verify-templates` | no | Check at startup that every component's template can actually be resolved (default: `true`) |

### Startup template check

With `verify-templates` on, the application fails to start if a component's template cannot be resolved, listing every affected one at once:

```
Composition Dialect: no template found for component(s):
  <c:order-summary> expects components/order-summary.html
```

Turn the check off if you use a resolver that only finds its templates later than startup.

Outside Spring, call it yourself once the engine is built:

```java
dialect.getRegistry().requireResolvableTemplates(engine.getConfiguration());
```

For more control, declare your own bean. Auto-configuration backs off automatically when you do:

```java
@Configuration
class Config {
    @Bean
    CompositionDialect compositionDialect() {
        return new CompositionDialect("components", "com.example.demo.components");
    }
}
```

## Component libraries

Components can come from several places at once — your own, plus each component library you import. Each is a `ComponentSource`: a templates path to scan, plus optionally the package a bare `impl` resolves against and a tag namespace:

```java
templateEngine.addDialect(new CompositionDialect(
        new ComponentSource("components", "com.example.demo.components"),
        ComponentSource.namespaced("webawesome", "wa")));
```

Under Spring Boot you do not list them yourself: a library publishes its own `ComponentSource` bean (or a classpath descriptor requiring no code at all), and every one found is added to the dialect alongside your own components.

### One prefix over all of them

There is one dialect and one prefix, no matter how many sources there are — `c:slot`, `c:name`, `c:rest`, and `c:component` itself mean the same thing in every template, including a library's.

What keeps two sources' components apart is the tag name, always the template's own file name:

```html
<c:card>            <!-- yours -->
<c:wa-button>        <!-- webawesome/wa-button.html, from the imported design system -->
```

If two components claim the same tag, the dialect reports it at startup, naming both templates. A source that declares a namespace (see [Component libraries](libraries.md)) avoids the risk: its templates are checked at scan time, and two sources declaring the same namespace fail at construction.

See [Component libraries](libraries.md) for publishing one.
