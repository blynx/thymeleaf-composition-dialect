# Components

A component needs two things: a record or class, and a template.

## Naming

The class name sets the tag name. `PascalCase` becomes `kebab-case`:

| Class | Tag |
|---|---|
| `Button` | `<c:button />` |
| `NavBar` | `<c:nav-bar />` |
| `SuperFancyElement` | `<c:super-fancy-element />` |

## Component class

`CompositionComponent` is an interface with one method, `context()`. It returns this component's
`CompositionComponentContext` — its attributes, its slot names, and access to locale, messages, and the
template variable scope.

The simplest way to implement it is a record. Its components are its props, one for one, with no extra
declaration:

```java
package com.example.demo.components;

import blynx.thymeleaf.compositiondialect.CompositionComponent;
import blynx.thymeleaf.compositiondialect.CompositionComponentContext;

public record Button(String variant, CompositionComponentContext context) implements CompositionComponent {
    public Button {
        variant = variant != null ? variant : "primary";
    }
}
```

You can also write a component as a plain class. Extend `AbstractCompositionComponent`, and declare each prop
as a field with `@Prop`:

```java
package com.example.demo.components;

import blynx.thymeleaf.compositiondialect.AbstractCompositionComponent;
import blynx.thymeleaf.compositiondialect.CompositionComponentContext;
import blynx.thymeleaf.compositiondialect.Prop;

public class Button extends AbstractCompositionComponent {

    @Prop
    private final String variant;

    public Button(CompositionComponentContext context) {
        super(context);
        Object raw = context.attributes().get("variant");
        this.variant = raw != null ? raw.toString() : "primary";
    }

    public String getVariant() {
        return variant;
    }
}
```

`AbstractCompositionComponent` stores the context for you and implements `context()` from it. Use a plain
class when a component must extend something else, needs custom coercion logic, or just reads better to you
that way.

The component instance is available in its template as `${this}`. A record's components are readable as
properties with no `get` prefix — `${this.variant}` resolves to `variant()`. A class's public getters work
the same way — `${this.variant}` resolves to `getVariant()`. See [Attributes](attributes.md) for how props
are declared and coerced.

## Component template

Place the template at `{templatesPath}/{kebab-case-name}.html`:

```html
<!-- templates/components/button.html -->
<button th:classappend="${'btn-' + this.variant}">
    <c:slot />
</button>
```

## Subdirectories

To organize templates into subdirectories, annotate the component with `@Composition`:

```java
@Composition(path = "forms")
public record Button(String variant, CompositionComponentContext context) implements CompositionComponent {
    ...
}
```

This resolves the template to `components/forms/button.html`.

## Sharing a path prefix across a package

`@Composition(path = ...)` applies to one component only. A record can never share a base class with another
record — records are final — so there is no way to inherit a `path` across a group of components. Instead,
declare `@Composition(pathPrefix = ...)` once on the package, in `package-info.java`:

```java
// com/example/demo/components/forms/package-info.java
@Composition(pathPrefix = "forms")
package com.example.demo.components.forms;
```

```java
package com.example.demo.components.forms;

@Composition(path = "text-inputs")
public record TextField(CompositionComponentContext context) implements CompositionComponent { }
```

This resolves `TextField`'s template to `components/forms/text-inputs/text-field.html` — the package's
`pathPrefix`, then the component's own `path`, then the tag name. A component with no `path` of its own still
gets the package's `pathPrefix` alone, for example `components/forms/text-field.html`.

A component that declares its own `@Composition(pathPrefix = ...)` overrides the package's value. It does
not combine with it. The more specific placement always wins.

## Context

See [Attributes](attributes.md) for how attributes are passed, declared as props, and read.
See [Context](context.md) for locale, messages, and variable scope.

## Unresolved tags

A tag under the dialect prefix that no component claims — a typo, or a component whose class isn't
discovered by any of the dialect's `ComponentSource`s — fails when it renders, naming the tag. Resolution
happens only for a tag that actually renders: one behind a false `th:if`, an untaken `th:each`, or any other
branch that never runs is never checked, the same as a real component tag would be.
