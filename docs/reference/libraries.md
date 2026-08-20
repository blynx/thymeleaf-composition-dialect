# Component libraries

A component library is an ordinary jar holding component templates, and optionally the classes that back them. An application importing one gets its components as tags alongside its own, from the same dialect and under the same prefix.

## Publishing one

Put your templates under a path of your own. Classes, if you have any, can live anywhere — discovery walks templates, not packages, so there is no requirement that your classes share one scannable package:

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

A `ComponentSource` is what a library publishes, not a `CompositionDialect`. The importing application builds the one dialect, from its own components and every source it finds.

### No Java at all

A library with no classes — a pure design-system wrapper, all markup — does not need the auto-configuration either. Ship a classpath descriptor instead, and the application discovers it automatically:

```properties
# META-INF/thymeleaf-composition/source.properties
templates-path=webawesome
namespace=wa
```

`default-package` is a third, optional key, for a library that does have classes but would rather skip the `@Bean` and `@AutoConfiguration` machinery entirely. This is the smallest thing that works: no interface to implement, no class to write, no Spring required — and it is why a pure-HTML pattern library is possible at all.

## Declare your namespace once

Every source shares the one dialect prefix, so the tag name is the only thing keeping your components apart from the application's. Declare a `tagNamespace` on your source, and it is enforced, not merely applied: every template under your `templatesPath` must itself be named `<namespace>-*`, checked when it is discovered.

```java
return ComponentSource.namespaced("webawesome", "wa", "com.example.webawesome");
```

```
webawesome/wa-button.html   → <c:wa-button>
webawesome/card.html        → build error: not named "wa-*"
```

This is stricter than a convention you remember to keep — a template you forget to prefix fails at startup, naming the file and the namespace it violated, rather than shipping and silently risking a collision with whatever else claims `<c:card>`. And it is checked once, at the source, rather than once per file: no test to write, no reviewer to catch it.

Two libraries both declaring `wa` fail too, at construction, from the source list alone — before anything is even walked, so the error can name both jars without having to discover a clashing tag first.

Your tag names are public API. They are compiled into the templates of every application using you, and an importing application cannot rename or alias them — so pick the namespace once, before your first release. Keep it short: it is typed on every tag your library ships. The `templatesPath` can be longer and more distinctive, since it only has to avoid colliding with another library's on the classpath — `wa` and `webawesome/`, rather than both short.

## Referring to your own components

Write them exactly as anyone else would:

```html
<!-- templates/webawesome/wa-card.html -->
<c:component impl="Card"/>
<article class="wa-card">
  <header><c:wa-badge c:label="${this.title}" /></header>
  <div class="wa-card__body"><c:slot /></div>
</article>
```

Because there is one prefix over every source, nothing about your registration is baked into the HTML you ship. That is also why an importing application cannot remap your tags: the reference above resolves by tag name alone, with no notion of which template it was written in, so a renamed `<c:wa-badge>` would find whatever else claimed that tag.

## Packaging

**Depend on the dialect, never bundle it.** Take `blynx.thymeleaf:thymeleaf-composition-dialect` as an ordinary dependency and let the application's copy win. Shading or relocating it breaks `${this}` inside slot content, silently and with no error anywhere.

**Do not bring your own Thymeleaf.** Declare it `compileOnly` and let the application provide it, as this dialect itself does.

## Template resolution

Your templates are resolved by the *application's* resolver chain, not one of your own. Under Spring Boot that means `classpath:/templates/`, so `templates/webawesome/wa-button.html` inside your jar is found with no setup at all.

It also means the application decides where that root is. Set `spring.thymeleaf.prefix` to `classpath:/views/` and the resolver goes looking for `views/webawesome/wa-button.html`, which your jar does not have. `verify-templates` (see [Setup](setup.md)) catches exactly this at startup: discovery walks the classpath directly and finds your template regardless, so a resolver-chain mismatch shows up as "found while walking the classpath, but the application's own resolver chain cannot resolve it" rather than as a per-component failure the first time someone renders it.

If you need to be independent of that, publish your own resolver too, scoped to your own path so it never claims the application's templates:

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

Every `ITemplateResolver` bean is added to the engine, in order. Without `resolvablePatterns` yours would be asked to resolve every template in the application, including ones that are none of its business.
