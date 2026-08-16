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

A marker only means something inside a component's own template. Written in a page, or in content being
passed to a component, `<c:slot />` is an error rather than markup left behind in the output — which is
also what a misspelling gets you, since a marker is recognized while the component's template is loaded and
nothing else answers to that tag.

## `th:text`/`th:utext` shorthand

`th:text`/`th:utext` on the component tag itself fills its default slot.

```html
<c:button th:text="#{catalog.add-to-cart}" />
```

If both `th:text` and `th:utext` are present at once, the standard
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

### Content with nowhere to go is an error

Asking for a slot the component does not declare fails, naming what was asked for and what the component
actually has:

```html
<c:card><h2 c:slot="headr">Title</h2></c:card>
<!-- fails: <c:card> was given content it declares no slot for: slot "headr".
     It declares a default slot, slot "footer", slot "header" -->
```

The same applies to a body given to a component with no default slot at all. Without this the content is
simply rendered nowhere, which looks exactly like having passed nothing — the mistake and the correct
"pass nothing" case produce identical output.

A marker under a `th:if` still counts as declared. Markers are found when the template is parsed, before
any conditional processing, so a component can decide per render whether to render a slot it declares —
that is what [`hasSlot()`](#hasslot) is for.

## The same slot in more than one branch

A component may write the same slot more than once. **Every** marker of that name receives the content,
which is what makes the usual conditional shape work — one branch per variant, only one of them rendering:

```html
<!-- heading.html -->
<h1 th:if="${this.level <= 1}"><c:slot /></h1>
<h2 th:if="${this.level == 2}"><c:slot /></h2>
<h3 th:if="${this.level == 3}"><c:slot /></h3>
```

Thymeleaf has no dynamic element name, so this is how a component picks its own tag. The markers are found
when the component's template is parsed, before any `th:if` around them has run, so at that moment all three
exist and nothing can yet know which one will survive. Filling only the first — as the native `<slot>`
element does, matching against a live tree where a slot either exists or does not — would mean betting on a
marker that may well be removed, and the content would disappear with it.

The cost is that two markers which really do both render both get the content, and it is rendered twice.
That is a duplicate you can see in the output; the other way round it is a drop you cannot. A marker inside
a `th:each` follows from the same rule: it renders once per iteration, and the content comes with it.

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
