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

Create a package namespace for your component classes — it is supplied to the dialect so it can discover them automatically:

```kotlin
package com.example.demo.components
```

Add the dialect to Thymeleaf:

```kotlin
templateEngine.addDialect(CompositionDialect("com.example.demo.components"))
```

### Parameters of `CompositionDialect`

| Parameter | Required | Description |
|---|---|---|
| `componentPackage` | yes | Package to scan for component classes |
| `componentsPath` | no | Sub-path under the Thymeleaf templates root where component templates live |

### Spring Boot

```kotlin
@Configuration
class Config {
    @Bean
    fun compositionDialect() = CompositionDialect(
        componentPackage = "com.example.demo.components",
        componentsPath = "components"
    )
}
```

## Usage & Features

### Components

A component needs two things: a **class** and a **template**.

The class name determines the tag name — `PascalCase` is converted to `kebab-case`:

| Class | Tag |
|---|---|
| `Button` | `<c:button />` |
| `NavBar` | `<c:nav-bar />` |
| `StatBox` | `<c:stat-box />` |

#### Component class

Extend `CompositionComponent` and declare a constructor that takes `CompositionComponentContext`. Attributes passed at the call site are available via `context.attributes`:

```kotlin
package com.example.demo.components

import blynx.thymeleaf.compositiondialect.CompositionComponent
import blynx.thymeleaf.compositiondialect.CompositionComponentContext

class Button(context: CompositionComponentContext) : CompositionComponent(context) {
    val variant: String = context.attributes["variant"]?.toString() ?: "primary"
}
```

Plain attributes arrive as strings. Attributes prefixed with `c:` are evaluated as Thymeleaf expressions before being passed:

```html
<c:button variant="danger" />              <!-- variant = "danger" (string) -->
<c:button c:variant="${currentVariant}" /> <!-- variant = value of currentVariant -->
```

The component instance is available in its template as `${this}`:

```kotlin
// in template: ${this.variant}
```

#### Component template

Place the template at `{componentsPath}/{kebab-case-name}.html`. The component instance is available as `${this}`:

```html
<!-- templates/components/button.html -->
<button th:classappend="${'btn-' + this.variant}">
    <c:slot />
</button>
```

To further organise templates into subdirectories, declare `path` in the companion object:

```kotlin
class Button(context: CompositionComponentContext) : CompositionComponent(context) {
    companion object { const val path = "forms" }
    val variant: String = context.attributes["variant"]?.toString() ?: "primary"
}
```

This resolves the template to `components/forms/button.html`.

### Slots

`<c:slot />` in a component template marks where the caller's content is injected:

```html
<!-- button.html -->
<button><c:slot /></button>
```

```html
<!-- call site -->
<c:button>Click me</c:button>
<!-- renders: <button>Click me</button> -->
```

#### Named slots

Add a `c:name` attribute to `<c:slot />` to define named slots. At the call site, assign content to a named slot with the `c:slot` attribute on any child element:

```html
<!-- card.html -->
<div class="card">
    <header th:if="${this.hasSlot('header')}"><c:slot c:name="header" /></header>
    <main><c:slot /></main>
    <footer th:if="${this.hasSlot('footer')}"><c:slot c:name="footer" /></footer>
</div>
```

```html
<!-- call site -->
<c:card>
    <h2 c:slot="header">Title</h2>
    <p>Body content goes in the default slot.</p>
    <a c:slot="footer" href="#">Footer link</a>
</c:card>
```

`hasSlot()` lets you conditionally render wrapping markup around a slot:

```kotlin
class Card(context: CompositionComponentContext) : CompositionComponent(context)
```

`hasSlot()` with no argument checks the default slot; `hasSlot("name")` checks a named slot.

### Rest attributes (`c:rest`)

Place `c:rest` on any element in a component template to spread all unconsumed caller attributes onto that element:

```kotlin
class Button(context: CompositionComponentContext) : CompositionComponent(context) {
    val variant: String = context.attributes["variant"]?.toString() ?: "primary"
    // variant is consumed above — everything else passes through via c:rest
}
```

```html
<!-- button.html -->
<button th:classappend="${'btn-' + this.variant}" c:rest>
    <c:slot />
</button>
```

```html
<!-- call site -->
<c:button variant="danger" type="submit" disabled="true">Delete</c:button>
<!-- renders: <button class="btn-danger" type="submit" disabled="true">Delete</button> -->
```

`variant` was consumed in the constructor and maps to a CSS class. `type` and `disabled` were not consumed, so they pass through to the element.

### Attribute defaults

**Default in the component class — consumed and applied explicitly:**

Use `?: "default"` in the property declaration. The attribute is consumed (won't appear in `c:rest`) and applied explicitly in the template:

```kotlin
val variant: String = context.attributes["variant"]?.toString() ?: "primary"
```

```html
<button th:classappend="${'btn-' + this.variant}" c:rest>
```

If the caller doesn't pass `variant`, the component uses `"primary"`. The caller never sees it in the HTML attributes directly — the component maps it to whatever it needs (a CSS class in this case).

**Default in the template — overridable via rest:**

For raw HTML attributes where you want a sensible default but the caller should be able to override, put the default as a static attribute in the template and do _not_ consume it in the class. `c:rest` overrides static attributes when the caller passes the same key:

```html
<button type="button" c:rest>
    <c:slot />
</button>
```

```html
<c:button>Submit</c:button>              <!-- renders: <button type="button">Submit</button> -->
<c:button type="submit">Submit</c:button> <!-- renders: <button type="submit">Submit</button> -->
```

### Template variable scope

Components can read and write the surrounding template variable scope — useful for implicit parent-to-descendant communication without threading values through every level of attributes.

```kotlin
class MagicHeadings(context: CompositionComponentContext) : CompositionComponent(context) {
    val level: Int = ((context.variable("parentMagicHeadings") as? MagicHeadings)?.level ?: 0) + 1
    init { context.setVariable("parentMagicHeadings", this) }
}

class Heading(context: CompositionComponentContext) : CompositionComponent(context) {
    val level: Int = context.attributes["level"]?.toString()?.toIntOrNull()
        ?: (context.variable("parentMagicHeadings") as? MagicHeadings)?.level
        ?: 1
}
```

```html
<c:magic-headings>
    <c:heading>First level</c:heading>       <!-- level 1 -->
    <c:magic-headings>
        <c:heading>Second level</c:heading>  <!-- level 2 -->
    </c:magic-headings>
</c:magic-headings>
<c:heading>Back to default</c:heading>       <!-- level 1 (no context) -->
```

`context.variable(name)` reads from the template scope (equivalent to `${name}` in the template). `context.setVariable(name, value)` writes a variable that is visible to all descendants within the component's rendered output.

### Locale and messages

The template locale and i18n message resolution are available on the context:

```kotlin
class Price(context: CompositionComponentContext) : CompositionComponent(context) {
    val amount: Double = context.attributes["amount"] as Double
    val formatted: String = NumberFormat.getCurrencyInstance(context.locale).format(amount)
    val label: String = context.message("price.label")
}
```

`context.message(code, vararg params)` resolves against the configured Thymeleaf message source. Returns the code itself if no message is found.

## Credits

Thanks [@tillsc](https://github.com/tillsc/) for adding the slots and instances :)
