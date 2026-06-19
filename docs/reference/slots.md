# Slots

`<c:slot />` in a component template marks where the caller's content is injected.

## Default slot

```html
<!-- button.html -->
<button><c:slot /></button>
```

```html
<!-- call site -->
<c:button>Click me</c:button>
<!-- renders: <button>Click me</button> -->
```

## Named slots

Add `c:name` to `<c:slot />` to define named slots. At the call site, assign content to a named slot with the `c:slot` attribute on any child element:

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

## `hasSlot()`

`hasSlot()` checks whether the caller provided content for a slot — useful for conditionally rendering wrapping markup:

```kotlin
class Card(context: CompositionComponentContext) : CompositionComponent(context)
```

- `hasSlot()` — checks the default slot
- `hasSlot("name")` — checks a named slot
