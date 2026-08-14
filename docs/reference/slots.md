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

## Fallback content

Content written between the marker's own tags renders when the caller passes nothing for that slot — for a named slot or the default slot alike:

```html
<!-- card.html -->
<div class="card">
    <header><c:slot c:name="header">Untitled</c:slot></header>
    <main><c:slot>Nothing here yet.</c:slot></main>
</div>
```

```html
<c:card />
<!-- renders: <div class="card"><header>Untitled</header><main>Nothing here yet.</main></div> -->

<c:card><h2 c:slot="header">Title</h2></c:card>
<!-- renders: <div class="card"><header>Title</header><main>Nothing here yet.</main></div> -->
```

A marker with no fallback content (`<c:slot></c:slot>`) renders nothing when the caller passes nothing, rather than leftover markup.

Whitespace-only default-slot content — e.g. just the indentation between a component's tags — is treated as if nothing were passed, so the fallback still shows. This deliberately deviates from the native `<slot>` element, where a bare text node still counts as content and silently defeats a default-slot fallback. Only the default slot is affected this way: a named slot can never end up whitespace-only, since only an element can carry `c:slot`.

A slot's fallback content may not itself contain another `c:slot` marker — that is rejected as an error, unlike native `<slot>`, which permits it.

## `th:text`/`th:utext` shorthand

`th:text`/`th:utext` on the component tag itself fills its default slot, so a component invocation with
only text content doesn't need a `<th:block>` wrapper:

```html
<!-- today, spelled out -->
<c:button>
    <th:block th:text="#{catalog.add-to-cart}">Add to cart</th:block>
</c:button>

<!-- equivalent shorthand -->
<c:button th:text="#{catalog.add-to-cart}" />
```

It behaves exactly as `th:text` would on any hand-written element: it silently replaces whatever
default-slot content was written between the tags, and if both `th:text` and `th:utext` are present at
once, the standard dialect's own precedence resolves it — this dialect does not add its own validation.
`th:insert`/`th:replace`/`th:include` are not supported this way.

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

`hasSlot()` (no-arg) agrees with fallback rendering about whitespace: indentation alone between a component's tags does not count as content, so it returns `false` there rather than `true` for a caller who passed nothing meaningful. `hasSlot("name")` is never affected, since a named slot can never end up whitespace-only.
