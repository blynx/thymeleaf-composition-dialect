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

A marker with no fallback content (`<c:slot></c:slot>`) renders nothing when the caller passes nothing.

Whitespace-only default-slot content — for example, just the indentation between a component's tags — is
treated as if the caller passed nothing, and the fallback shows. A named slot can never end up
whitespace-only, since only an element can carry `c:slot`.

A marker only means something inside a component's own template. Written in a page, or in content being
passed to a component, `<c:slot />` is an error, naming the template and location — this is also what a
misspelled marker gets you.

## A slot nested inside a fallback

A slot's fallback content may itself contain another `c:slot` marker, to any depth. A component ships a
full, sensible default, and a caller can override one piece of it without reproducing the whole thing:

```html
<!-- data-table.html -->
<tbody>
    <c:slot c:name="rows">
        <tr><td><c:slot c:name="empty-message">No data available.</c:slot></td></tr>
    </c:slot>
</tbody>
```

```html
<c:data-table />
<!-- renders the default row, with its default message -->

<c:data-table><span c:slot="empty-message">Nothing matched your filters.</span></c:data-table>
<!-- overrides just the message, keeping the default row around it -->

<c:data-table><tr c:slot="rows"><td colspan="3">No results</td></tr></c:data-table>
<!-- overrides the whole row, so "empty-message" never gets a chance to render -->
```

A caller targets a nested slot exactly like a top-level one: `c:slot="empty-message"` does not care where in
the component's template that name's marker lives, and a typo of it gets the same listed-in-the-error
treatment as a top-level name.

Filling the outer slot (`rows` above) replaces its fallback wholesale, so a marker nested only inside that
fallback does not render either — content assigned to it separately lands nowhere.

## `th:text`/`th:utext` shorthand

`th:text`/`th:utext` on the component tag itself fills its default slot.

```html
<c:button th:text="#{catalog.add-to-cart}" />
```

If both `th:text` and `th:utext` are present at once, the standard
dialect's own precedence resolves it.
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

The same applies to a body given to a component with no default slot at all.

A marker under a `th:if` still counts as declared, so a component can decide per render whether to render a
slot it declares — that is what [`hasSlot()`](#hasslot) is for.

## The same slot in more than one branch

A component may write the same slot more than once. **Every** marker of that name receives the content,
which is what makes the usual conditional shape work — one branch per variant, only one of them rendering:

```html
<!-- heading.html -->
<h1 th:if="${this.level <= 1}"><c:slot /></h1>
<h2 th:if="${this.level == 2}"><c:slot /></h2>
<h3 th:if="${this.level == 3}"><c:slot /></h3>
```

This is how a component picks its own tag dynamically, since Thymeleaf has no dynamic element name.

If more than one branch's marker actually renders, the content is duplicated — once per rendered marker.
The same applies to a marker inside a `th:each`: it renders once per iteration, with the content alongside
it each time.

[A marker nested inside a fallback](#a-slot-nested-inside-a-fallback) works the same way: a marker still
counts as declared regardless of whether the branch or fallback containing it ends up rendering.

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

`hasSlot()` with no argument agrees with fallback rendering about whitespace: indentation alone between a
component's tags does not count as content, so it returns `false` there. `hasSlot("name")` is not affected
this way — only an element can carry `c:slot`, and an element can never be whitespace-only.
