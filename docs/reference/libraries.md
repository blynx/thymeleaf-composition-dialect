# Component libraries

A component library is an ordinary jar holding component classes and their templates. An application importing one gets its components as tags alongside its own, from the same dialect and under the same prefix.

## Publishing one

Put your components in one package and their templates under a path of your own:

```
src/main/java/com/example/designsystem/components/DsCard.java
src/main/resources/templates/designsystem/ds-card.html
```

Publish a `ComponentSource` bean from your own auto-configuration, and importing your library is just adding the dependency:

```java
@AutoConfiguration
public class DesignSystemAutoConfiguration {

    @Bean
    ComponentSource designSystemComponents() {
        return new ComponentSource("com.example.designsystem.components", "designsystem");
    }
}
```

A `ComponentSource` is what a library publishes, not a `CompositionDialect`. The importing application builds the one dialect, from its own components and every source it finds.

## Name your classes for your library

Every source shares the one dialect prefix, so the tag name is the only thing keeping your components apart from the application's. Carry your library's name in the class name, the way web components do:

```java
public record DsCard(String title, CompositionComponentContext context)
        implements CompositionComponent { }     // <c:ds-card>
```

Nothing enforces this. If you ship a plain `Card` and the importing application has one too, its startup fails with both class names — and the fix is a rename in whichever of the two the developer actually owns, which may well not be yours. Enforce it on your side instead:

```java
@Test
void everyComponentCarriesTheLibrarysName() {
    ComponentRegistry registry = ComponentRegistry.scan(
            new ComponentSource("com.example.designsystem.components"), "c");

    assertFalse(registry.components().isEmpty());
    registry.components().forEach(component ->
            assertTrue(component.tagName().startsWith("ds-"), component.tagName()));
}
```

Your tag names are public API. They are compiled into the templates of every application using you, and an importing application cannot rename or alias them — so pick the prefix once, before your first release.

Keep the templates path distinct from the tag prefix, and make it the longer of the two. The tag prefix wants to be short because it is typed on every tag; the templates path wants to be distinctive, because it is the only thing stopping your `templates/components/card.html` from shadowing another library's on the classpath. `ds-` and `designsystem/` rather than both.

## Referring to your own components

Write them exactly as anyone else would:

```html
<!-- templates/designsystem/ds-card.html -->
<article class="ds-card">
  <header><c:ds-badge c:label="${this.title}" /></header>
  <div class="ds-card__body"><c:slot /></div>
</article>
```

Because there is one prefix over every source, nothing about your registration is baked into the HTML you ship. That is also why an importing application cannot remap your tags: the reference above resolves by tag name alone, with no notion of which template it was written in, so a renamed `<c:ds-badge>` would find whatever else claimed that tag.

## Packaging

**Depend on the dialect, never bundle it.** Take `blynx.thymeleaf:thymeleaf-composition-dialect` as an ordinary dependency and let the application's copy win. Shading or relocating it breaks `${this}` inside slot content, silently and with no error anywhere.

**Do not bring your own Thymeleaf.** Declare it `compileOnly` and let the application provide it, as this dialect itself does.

## Template resolution

Your templates are resolved by the *application's* resolver chain, not one of your own. Under Spring Boot that means `classpath:/templates/`, so `templates/designsystem/ds-card.html` inside your jar is found with no setup at all.

It also means the application decides where that root is. Set `spring.thymeleaf.prefix` to `classpath:/views/` and the resolver goes looking for `views/designsystem/ds-card.html`, which your jar does not have.

If you need to be independent of that, publish your own resolver too, scoped to your own path so it never claims the application's templates:

```java
@Bean
ITemplateResolver designSystemTemplates() {
    ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
    resolver.setPrefix("designsystem-templates/");
    resolver.setSuffix(".html");
    resolver.setResolvablePatterns(Set.of("designsystem/**"));
    resolver.setCheckExistence(true);
    resolver.setOrder(1);
    return resolver;
}
```

Every `ITemplateResolver` bean is added to the engine, in order. Without `resolvablePatterns` yours would be asked to resolve every template in the application, including ones that are none of its business.
