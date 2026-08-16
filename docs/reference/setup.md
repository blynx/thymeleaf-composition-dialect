# Setup

## Standalone

Put your components in one package. Give this package to the dialect. The dialect scans it and finds your components:

```java
package com.example.demo.components;
```

Add the dialect to Thymeleaf:

```java
templateEngine.addDialect(new CompositionDialect("com.example.demo.components"));
```

For more on locating templates, see [Components](components.md#component-template).

### `CompositionDialect` parameters

| Parameter | Required | Description |
|---|---|---|
| `componentPackage` | yes | Package to scan for components |
| `templatesPath` | no | Sub-path under the Thymeleaf templates root where component templates live (default: the templates root itself) |
| `prefix` | no | Tag prefix (default: `c`) |

## Spring Boot

Add the library to your dependencies. With Spring Boot auto-configuration, you do not need to declare the dialect as a bean. Set the package in `application.properties` instead:

```properties
thymeleaf.composition.component-package=com.example.demo.components
thymeleaf.composition.templates-path=components
```

| Property | Required | Description |
|---|---|---|
| `thymeleaf.composition.component-package` | no | Package to scan for your own components — omit it if you only import component libraries |
| `thymeleaf.composition.templates-path` | no | Sub-path under the templates root (default: the templates root itself) |
| `thymeleaf.composition.prefix` | no | Tag prefix (default: `c`) |
| `thymeleaf.composition.verify-templates` | no | Check at startup that every component has a template (default: `true`) |

### Every component's template is checked at startup

With `verify-templates` on, the application fails to start if any registered component has no template
file, listing every one of them at once:

```
Composition Dialect: no template found for component(s):
  <c:order-summary> (com.example.components.OrderSummary) expects components/order-summary.html
```

Turn the check off if you use a resolver that cannot find its templates until later than startup.

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
        return new CompositionDialect("com.example.demo.components", "components");
    }
}
```

## Component Libraries

Components can come from several places at once — your own, plus each component library you import. Each is a `ComponentSource`: a package to scan, and the templates sub-path that package's templates live under.

```java
templateEngine.addDialect(new CompositionDialect(
        new ComponentSource("com.example.demo.components", "components"),
        new ComponentSource("com.example.designsystem.components", "designsystem")));
```

The templates path belongs to the source rather than to the dialect, because an imported library's templates sit wherever that library put them in its own jar.

Under Spring Boot you do not list them yourself. A library publishes its own `ComponentSource` bean, and every one of them is collected into the dialect alongside your own components — so importing a library is just adding the dependency.

### One prefix over all of them

There is one dialect and one prefix, no matter how many sources there are. `c:slot`, `c:name` and `c:rest` therefore mean the same thing in every template, including the ones inside an imported library's jar.

What keeps two sources' components apart is the tag name, and a library claims its own by naming its classes for itself:

```html
<c:card>            <!-- yours -->
<c:ds-card>         <!-- DsCard, from the imported design system -->
```

If two components end up claiming the same tag, the dialect says so at startup and names both classes and both sources. It cannot pick one for you: whichever tag is unreachable would simply never render.

See [Component libraries](libraries.md) for publishing one.
