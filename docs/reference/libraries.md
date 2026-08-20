# Component libraries

A component library is an ordinary jar holding component templates, and optionally the classes that back them. An application importing one gets its components as tags alongside its own, from the same dialect and under the same prefix.

## Publishing one

Put your templates under a path of your own. Classes, if you have any, can live in any package:

```
src/main/resources/templates/webawesome/wa-button.html
src/main/java/com/example/webawesome/Button.java
```

```html
<!-- templates/webawesome/wa-button.html -->
<c:component impl="Button"/>
<button class="wa-button" c:rest><c:slot/></button>
```

Publish a `ComponentSource` bean from your own auto-configuration, and importing your library is just adding the dependency:

```java
@AutoConfiguration
public class WebAwesomeAutoConfiguration {

    @Bean
    ComponentSource webAwesomeComponents() {
        return ComponentSource.namespaced("webawesome", "wa", "com.example.webawesome");
    }
}
```

`ComponentSource` is what a library publishes — the importing application collects every one it finds into the one dialect it builds.

### No Java at all

A library with no classes — a design-system wrapper, all markup — does not need the auto-configuration either. Ship a classpath descriptor instead, and the application discovers it automatically:

```properties
# META-INF/thymeleaf-composition/source.properties
templates-path=webawesome
namespace=wa
```

`default-package` is a third, optional key, for a library that does have classes but would rather skip the `@Bean`/`@AutoConfiguration` setup.

## Declare your namespace

Every source shares the one dialect prefix, so the tag name is what keeps your components apart from the application's. Declare a `tagNamespace` on your source:

```java
return ComponentSource.namespaced("webawesome", "wa", "com.example.webawesome");
```

Every template under that source's `templatesPath` must then be named `<namespace>-*`:

```
webawesome/wa-button.html   → <c:wa-button>
webawesome/card.html        → build error: not named "wa-*"
```

A template that does not follow this fails at startup, naming the file and the namespace. Two sources declaring the same namespace fail at construction too, naming both.

Your tag names are public API, compiled into every application that uses you — pick the namespace once, before your first release, and keep it short, since it is typed on every tag. The `templatesPath` can be longer and more descriptive.

## Referring to your own components

Write them exactly as anyone else would:

```html
<!-- templates/webawesome/wa-card.html -->
<c:component impl="Card"/>
<article class="wa-card">
  <header><c:wa-badge th:label="${this.title}" /></header>
  <div class="wa-card__body"><c:slot /></div>
</article>
```

An importing application cannot rename or remap your tags — a renamed `<c:wa-badge>` would resolve to whatever else claims that tag.

## Packaging

**Depend on the dialect, never bundle it.** Take `blynx.thymeleaf:thymeleaf-composition-dialect` as an ordinary dependency and let the application's copy win. Shading or relocating it breaks `${this}` inside slot content, silently and with no error anywhere.

**Do not bring your own Thymeleaf.** Declare it `compileOnly` and let the application provide it.

## Template resolution

Your templates are resolved by the *application's* resolver chain, not one of your own. Under Spring Boot that means `classpath:/templates/`, so `templates/webawesome/wa-button.html` inside your jar is found with no setup at all.

The application decides where that root is, though. Set `spring.thymeleaf.prefix` to `classpath:/views/` and the resolver goes looking for `views/webawesome/wa-button.html`, which your jar does not have — `verify-templates` (see [Setup](setup.md)) catches this at startup rather than at first render.

If you need to be independent of the application's resolver configuration, publish your own, scoped to your own path so it never claims the application's templates:

```java
@Bean
ITemplateResolver webAwesomeTemplates() {
    ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
    resolver.setPrefix("webawesome-templates/");
    resolver.setSuffix(".html");
    resolver.setResolvablePatterns(Set.of("webawesome/**"));
    resolver.setCheckExistence(true);
    resolver.setOrder(1);
    return resolver;
}
```

Every `ITemplateResolver` bean is added to the engine, in order. Without `resolvablePatterns` yours would be asked to resolve every template in the application.
