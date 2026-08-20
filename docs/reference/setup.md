# Setup

## Standalone

Put your component templates under one path, relative to your Thymeleaf templates root. Give that path to the dialect. The dialect walks it and finds every template that declares itself a component with `<c:component>` — see [Components](components.md):

```html
<!-- templates/components/button.html -->
<c:component impl="Button"/>
<button th:classappend="${'btn-' + this.variant}">
    <c:slot />
</button>
```

```java
templateEngine.addDialect(new CompositionDialect("components"));
```

### `CompositionDialect` parameters

| Parameter | Required | Description |
|---|---|---|
| `templatesPath` | yes | Sub-path under the Thymeleaf templates root to walk for components |
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
| `thymeleaf.composition.templates-path` | no | Sub-path under the templates root to walk — omit it if you only import component libraries |
| `thymeleaf.composition.default-package` | no | Package a bare or relatively-dotted `impl` resolves against |
| `thymeleaf.composition.prefix` | no | Tag prefix (default: `c`) |
| `thymeleaf.composition.verify-templates` | no | Check at startup that every discovered component's template is reachable through the actual resolver chain (default: `true`) |

### Every component's template is checked at startup

Discovery already walked the classpath directly to find your components, so `verify-templates` is not "does the file exist" — it is "does the *application's own* Thymeleaf resolver chain agree with what the classpath walk found". Those can disagree: `spring.thymeleaf.prefix` may point somewhere the classpath walk cannot see, or a resolver's resolvable-pattern restriction may leave a discovered template unreachable. With the check on, the application fails to start and lists every affected component at once:

```
Composition Dialect: no template found for component(s):
  <c:order-summary> expects components/order-summary.html
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
        return new CompositionDialect("components", "com.example.demo.components");
    }
}
```

## Component Libraries

Components can come from several places at once — your own, plus each component library you import. Each is a `ComponentSource`: a templates path to walk, and (optionally) the package a bare `impl` resolves against and the tag namespace the source's templates are named for.

```java
templateEngine.addDialect(new CompositionDialect(
        new ComponentSource("components", "com.example.demo.components"),
        ComponentSource.namespaced("webawesome", "wa")));
```

The templates path belongs to the source rather than to the dialect, because an imported library's templates sit wherever that library put them in its own jar.

Under Spring Boot you do not list them yourself. A library publishes its own `ComponentSource` bean, or ships a classpath descriptor with no Java code at all, and every one found is collected into the dialect alongside your own components — so importing a library is just adding the dependency.

### One prefix over all of them

There is one dialect and one prefix, no matter how many sources there are. `c:slot`, `c:name`, `c:rest` — and `c:component` itself — therefore mean the same thing in every template, including the ones inside an imported library's jar. That is also why a source's templates must spell `<c:component>` under whichever prefix the dialect they end up in actually uses: it is dialect grammar, exactly as prefix-bound as `<c:slot>`.

What keeps two sources' components apart is the tag name, which is always the template's own file name. A library claims its own by naming its files for itself, or better, by declaring a namespace on its source (see [Component libraries](libraries.md)):

```html
<c:card>            <!-- yours -->
<c:wa-button>        <!-- webawesome/wa-button.html, from the imported design system -->
```

If two components end up claiming the same tag, the dialect says so at startup and names both templates and both sources. It cannot pick one for you: whichever tag is unreachable would simply never render. A namespaced source removes the risk entirely — its own templates are checked at scan time, and two sources declaring the same namespace fail at construction, before anything is even walked.

See [Component libraries](libraries.md) for publishing one.
