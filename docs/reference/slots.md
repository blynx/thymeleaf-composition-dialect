# Slots

`<c:slot />` in a component template marks where the caller's content goes.

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

Content written between the marker's own tags renders when the caller passes nothing for that slot. This
applies to a named slot and to the default slot alike:

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

A marker with no fallback content (`<c:slot></c:slot>`) renders nothing when the caller passes nothing. It
does not leave leftover markup behind.

Whitespace-only default-slot content — for example, just the indentation between a component's tags — is
treated as if the caller passed nothing. The fallback still shows. This deviates from the native `<slot>`
element on purpose: there, a bare text node still counts as content, and it silently defeats a default-slot
fallback. Only the default slot works this way. A named slot can never end up whitespace-only, since only an
element can carry `c:slot`.

A slot's fallback content must not itself contain another `c:slot` marker. The dialect rejects this as an
error. Native `<slot>` permits it.

## `th:text`/`th:utext` shorthand

`th:text`/`th:utext` on the component tag itself fills its default slot. A component invocation with only
text content does not need a `<th:block>` wrapper:

```html
<!-- today, spelled out -->
<c:button>
    <th:block th:text="#{catalog.add-to-cart}">Add to cart</th:block>
</c:button>

<!-- equivalent shorthand -->
<c:button th:text="#{catalog.add-to-cart}" />
```

It behaves exactly as `th:text` would on any hand-written element. It silently replaces whatever default-slot
content was written between the tags. If both `th:text` and `th:utext` are present at once, the standard
dialect's own precedence resolves it — this dialect adds no validation of its own.
`th:insert`/`th:replace`/`th:include` are not supported this way.

## Named slots

Add `c:name` to `<c:slot />` to define named slots. At the call site, assign content to a named slot with the
`c:slot` attribute on a direct child element:

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

`c:slot` follows the same rule as the native shadow-DOM `slot` attribute: it binds to the element's direct
parent. Only direct children of a component tag are assigned to that component's slots. When components
nest, each component assigns its own children. A `c:slot` inside a nested component invocation is consumed
by the inner component, not the outer one:

```html
<c:card>
    <h2 c:slot="header">Outer card header</h2>
    <c:card>
        <h2 c:slot="header">Inner card header</h2>
        <p>Inner card body.</p>
    </c:card>
</c:card>
```

On elements deeper than a direct child, with no component in between, `c:slot` is inert. It has no effect,
and the dialect keeps it in the rendered output — the same as the native `slot` attribute on an element that
is not a shadow-host child.

## `hasSlot()`

`hasSlot()` checks whether the caller gave content for a slot. The named-slots example above uses it to
decide whether to render the wrapping `<header>` and `<footer>` elements at all.

- `hasSlot()` — checks the default slot
- `hasSlot("name")` — checks a named slot

`hasSlot()` with no argument agrees with fallback rendering about whitespace. Indentation alone between a
component's tags does not count as content, so it returns `false` there instead of `true` for a caller who
gave nothing meaningful. `hasSlot("name")` is not affected this way — only an element can carry `c:slot`, and
an element can never be whitespace-only.
