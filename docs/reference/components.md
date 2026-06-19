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

```kotlin
package com.example.demo.components

import blynx.thymeleaf.compositiondialect.CompositionComponent
import blynx.thymeleaf.compositiondialect.CompositionComponentContext

class Button(context: CompositionComponentContext) : CompositionComponent(context) {
    val variant: String = context.attributes["variant"]?.toString() ?: "primary"
}
```

The component instance is available in its template as `${this}`.

## Component template

Place the template at `{componentsPath}/{kebab-case-name}.html`:

```html
<!-- templates/components/button.html -->
<button th:classappend="${'btn-' + this.variant}">
    <c:slot />
</button>
```

## Subdirectories

To organise templates into subdirectories, declare `path` in the companion object:

```kotlin
class Button(context: CompositionComponentContext) : CompositionComponent(context) {
    companion object { const val path = "forms" }
    val variant: String = context.attributes["variant"]?.toString() ?: "primary"
}
```

This resolves the template to `components/forms/button.html`.

## Context

See [Attributes](attributes.md) for how attributes are passed and consumed.
See [Context](context.md) for locale, messages, and variable scope.
