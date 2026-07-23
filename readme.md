![AI generated illustration "Space and Thyme"](docs/space-and-thyme.jpg "Space And Thyme")

_"An ikebana composition_  
_of just space and thyme"_ 
([midjourney ai](https://www.midjourney.com/))

# Thymeleaf Composition Dialect

A flavour of ui component templating in Thymeleaf.

⚠️ Still drafting, some things may change ⚠️

```html
<c:card>
    <h2 c:slot="header">Thymeleaf Composition Dialect</h2>
    <p>Compose your layouts with components.</p>
    <c:link c:slot="footer" type="with-glitter">show me</c:link>
</c:card>
```

## Setup

Add the dialect to Thymeleaf with the package to scan for component classes:

```java
templateEngine.addDialect(new CompositionDialect("com.example.demo.components"));
```

For Spring Boot, set the package in `application.properties`:

```properties
thymeleaf.composition.component-package=com.example.demo.components
```

See [Setup](docs/reference/setup.md) for full configuration options.

## Documentation

- [Setup](docs/reference/setup.md) — standalone and Spring Boot configuration
- [Components](docs/reference/components.md) — component classes, templates, naming
- [Slots](docs/reference/slots.md) — default and named slots
- [Attributes](docs/reference/attributes.md) — passing attributes, defaults, rest attributes
- [Context](docs/reference/context.md) — locale, messages, variable scope

## Credits

Thanks [@tillsc](https://github.com/tillsc/) for adding the slots and instances :)
