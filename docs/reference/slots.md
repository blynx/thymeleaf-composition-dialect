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

Add `c:name` to `<c:slot />` to define named slots. At the call site, assign content to a named slot with the `c:slot` attribute on a direct child element:

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

## Slot assignment scope

`c:slot` follows the same rule as the native shadow-DOM `slot` attribute: it binds to the
element's direct parent. Only direct children of a component tag are assigned to that
component's slots. When components nest, each component assigns its own children —
`c:slot` inside a nested component invocation is consumed by the inner component, not the
outer one:

```html
<c:card>
    <h2 c:slot="header">Outer card header</h2>
    <c:card>
        <h2 c:slot="header">Inner card header</h2>
        <p>Inner card body.</p>
    </c:card>
</c:card>
```

On elements deeper than a direct child (with no component in between), `c:slot` is inert:
it has no effect and is kept in the rendered output, just like the native `slot` attribute
on an element that is not a shadow-host child.

## `hasSlot()`

`hasSlot()` checks whether the caller provided content for a slot — useful for conditionally rendering wrapping markup:

```java
public class Card extends CompositionComponent {
    public Card(CompositionComponentContext context) {
        super(context);
    }
}
```

- `hasSlot()` — checks the default slot
- `hasSlot("name")` — checks a named slot
