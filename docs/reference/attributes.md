# Attributes

## Passing attributes

Attributes on a component tag work like on any other Thymeleaf element: a plain attribute is a literal
string, and a `th:`-prefixed one is evaluated as an expression:

```html
<c:button variant="danger" />               <!-- variant = "danger" (string) -->
<c:button th:variant="${currentVariant}" /> <!-- variant = value of currentVariant -->
```

`c:` is reserved for the dialect's own grammar — `c:slot`, `c:name`, `c:rest`.

`title` and `th:title` name the same prop (assuming `title` is declared) and writing both on one tag leaves the last one in source order winning.

## Attributes in classes

For record classes, an attribute the caller omits binds to `null` for a reference-typed component — `String`, 
a boxed number, an enum — or to `0`/`false` for a primitive `int`, `long`, `double` or `boolean`. Handle the `null` 
in a compact constructor; a primitive component already has a value to work with.

In a plain java class, read attributes in the constructor with `context.attributes().get("key")`.  
A plain java class gets no automatic coercion.

See [Writing the class](components.md#writing-the-class) for the shape of each.

## Declaring props

A component declares the attributes that belong to it, as opposed to the caller's own HTML attributes, so
they are excluded from `restAttributes`/`c:rest`. How, depends on the component:

- **A record's props are its record components**, one for one, other than the one holding
  `CompositionComponentContext`. `variant` above is a prop with no declaration needed at all. Override the
  attribute name a component binds from — when the kebab-cased derivation does not fit — with `@Prop`:
  `@Prop("data-variant") String variant`.
- **A class declares props by annotating fields with `@Prop`:** `@Prop private final String variant;`
  A field's attribute name defaults to its own name, kebab-cased. `@Prop("...")` overrides it, the same as
  on a record component.

Either way, declaring a prop is what excludes it, not reading it. A component can read an attribute to
validate, log, or derive from it without declaring it. That attribute still flows through to `c:rest`, like
any attribute the component never touches.

## Rest attributes

### `c:rest` — spread onto an element

Place `c:rest` on any element in a component template to spread every attribute that is not a declared prop
onto that element:

```html
<!-- button.html -->
<c:component props="variant"/>
<button th:classappend="${'btn-' + this.variant}" c:rest>
    <c:slot />
</button>
```

```html
<!-- call site -->
<c:button variant="danger" type="submit" disabled="true">Delete</c:button>
<!-- renders: <button class="btn-danger" type="submit" disabled="true">Delete</button> -->
```

`variant` is a declared prop. `type` and `disabled` are not, so they pass through to the element, whether or
not the component reads them.

Which attributes those are is decided by the component whose template `c:rest` is written in, so it only
means something there. In a page, or in content being passed to a component, there is nothing for it to
spread and it is an error — the same as a misplaced [`c:slot`](slots.md#fallback-content).

### `restAttributes` — access programmatically

`restAttributes` is also available directly in the template or the component:

```html
<!-- iterate the rest attributes manually -->
<div th:each="attr : ${this.restAttributes}" th:attr="${attr.key}=${attr.value}">
```

## Standard Thymeleaf attributes

### ... a component tag CANNOT carry

These thymeleaf built-in `th:` attributes cannot be used on a `c:` component tag and are rejected rather than silently
turned into a prop:

`th:attr`, `th:attrappend`, `th:attrprepend`, `th:with`, `th:object`,
`th:alt-title`, `th:lang-xmllang`, `th:fragment`, `th:remove`, `th:inline`, `th:assert`, `th:ref`,
`th:field`, `th:errors`, `th:errorclass`

They are legal in a component template though.

### ... a component CAN carry

These attributes cannot be used in `c:rest`.

`th:classappend`/`th:styleappend` are allowed. They will append into the respective declared attributes in the component template.

`th:if`, `th:unless`, `th:each`, `th:switch`, `th:case`, control flow is unaffected. It still decides
whether and how often the component renders. See [Components](components.md).  

`th:text`/`th:utext` overwrite the default slot, see also: [Slots](slots.md#thtextthutext-shorthand).
