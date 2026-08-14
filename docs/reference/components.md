# Components

A component needs two things: a **class** and a **template**.

## Naming

The class name determines the tag name — `PascalCase` is converted to `kebab-case`:

| Class | Tag |
|---|---|
| `Button` | `<c:button />` |
| `NavBar` | `<c:nav-bar />` |
| `StatBox` | `<c:stat-box />` |

## Component class

Extend `CompositionComponent` and declare a constructor that takes `CompositionComponentContext`:

```java
package com.example.demo.components;

import blynx.thymeleaf.compositiondialect.CompositionComponent;
import blynx.thymeleaf.compositiondialect.CompositionComponentContext;

public class Button extends CompositionComponent {
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

The component instance is available in its template as `${this}`, and its public getters are reachable as properties — `${this.variant}` resolves to `getVariant()`.

## Component template

Place the template at `{componentsPath}/{kebab-case-name}.html`:

```html
<!-- templates/components/button.html -->
<button th:classappend="${'btn-' + this.variant}">
    <c:slot />
</button>
```

## Subdirectories

To organise templates into subdirectories, declare a `public static final String path` field:

```java
public class Button extends CompositionComponent {
    public static final String path = "forms";

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

This resolves the template to `components/forms/button.html`.

## Sharing a path across an abstract base

`path` shadows rather than composes: a subclass that declares its own `path` replaces the parent's
entirely, since it's ordinary Java field hiding. If you have a shared abstract base and want every
concrete subclass to inherit a common sub-path without repeating it, declare `pathPrefix` on the base
instead — it's read independently of `path` and composes with whatever a subclass sets for itself:

```java
public abstract class FormField extends CompositionComponent {
    public static final String pathPrefix = "forms";

    public FormField(CompositionComponentContext context) {
        super(context);
    }
}

public class TextField extends FormField {
    public static final String path = "text-inputs";

    public TextField(CompositionComponentContext context) {
        super(context);
    }
}
```

This resolves `TextField`'s template to `components/forms/text-inputs/text-field.html` — `pathPrefix`
first, then the subclass's own `path`, then the tag name. A subclass that declares no `path` of its own
still gets `pathPrefix` alone, e.g. `components/forms/text-field.html`.

## Context

See [Attributes](attributes.md) for how attributes are passed and consumed.
See [Context](context.md) for locale, messages, and variable scope.
