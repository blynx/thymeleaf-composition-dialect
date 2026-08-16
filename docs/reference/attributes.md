# Attributes

## Passing attributes

Composition dialect brings a new type of attribute with the dialect prefix `c:`.
It is evaluated as Thymeleaf expression just like `th:`-attributes, but it names a prop
and only a prop — it never ends up in `restAttributes`/`c:rest`, so a `c:`-prefixed attribute the component
does not declare as a prop can only be a mistake, and fails immediately rather than silently vanishing:

```html
<c:button c:variant="${currentVariant}" />  <!-- fine: "variant" is a declared prop -->
<c:button c:tabindex="${idx}" />            <!-- fails: "tabindex" is not a declared prop -->
<c:button th:tabindex="${idx}" />           <!-- passes through to c:rest instead -->
```

Plain attributes arrive as strings. Attributes prefixed with `th:` are evaluated as Thymeleaf expressions
before the dialect passes them on — that is already what `th:` means on any element, evaluate this
expression and call it by that name. On a component tag the result is a prop rather than an HTML attribute,
so `th:title`, `th:class` and `th:disabled` all arrive under their plain names and end up in
`restAttributes`/`c:rest` unless the component declares them as props:

```html
<c:button variant="danger" />               <!-- variant = "danger" (string) -->
<c:button th:variant="${currentVariant}" /> <!-- variant = value of currentVariant -->
```

Reach for `th:` (or a plain attribute) whenever the value is meant to pass through as an HTML attribute,
whether or not the component happens to declare a prop of that name; reach for `c:` only for a value that is
specifically meant to reach a declared prop.

Note that the prefix is *dropped*: `title`, `c:title` and `th:title` all name the same prop (assuming
`title` is declared), and writing more than one spelling of it on a tag leaves the last one in source order
winning. Only `c:` and `th:` are evaluated; any other prefix is passed through as its raw, unevaluated
string.

### Standard attributes a component tag cannot carry

A component tag is replaced by the component's own markup before most of the standard dialect would run, so
the attributes whose meaning depends on modifying *that tag* have nothing left to act on and no prop they
could sensibly become. These are rejected rather than quietly scraped as strings:

`th:attr`, `th:attrappend`, `th:attrprepend`, `th:with`, `th:object`, `th:classappend`, `th:styleappend`,
`th:alt-title`, `th:lang-xmllang`, `th:fragment`, `th:remove`, `th:inline`, `th:assert`, `th:ref`,
`th:field`, `th:errors`, `th:errorclass`

Put them on an element inside the component's own template instead, or declare the value as a prop and pass
it with `c:`, letting the template apply it.

Control flow is unaffected — `th:if`, `th:unless`, `th:each`, `th:switch` and `th:case` run *before* the
component takes over its tag, so they decide whether and how often the component renders. See
[components](components.md).

In a record, each component other than `context` binds automatically from the attribute of the same name,
kebab-cased, and is coerced to its declared type — `String`, `boolean`/`Boolean`, `int`/`Integer`,
`long`/`Long`, `double`/`Double`, or an enum constant:

```java
public record Button(String variant, CompositionComponentContext context) implements CompositionComponent {
    public Button {
        variant = variant != null ? variant : "primary";
    }
}
```

An attribute the caller omits binds to `null` for a reference-typed component, or to the type's zero value
for a primitive one (`0`, `false`, and so on). Handle `null` in a compact constructor, as above. A primitive
component defaults to zero or false on its own. A boxed or enum component needs an explicit default, since
`null` is a real, distinct value there.

A value that is already the declared type passes through with no coercion at all — for example, a
`c:`-prefixed expression that evaluates directly to an `Enum` constant, or a prop typed `Object`, which
accepts anything.

If a plain attribute cannot be read as the declared type — `auto-hide-seconds="soon"` for an `int` prop, say
— construction fails. The error names the component, the attribute, the value given, and the type expected.

In a class, read attributes in the constructor with `context.attributes().get("key")`:

```java
public class Button extends AbstractCompositionComponent {
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

A class gets no automatic coercion. Read the raw value and convert it yourself.

## Declaring props

A component declares the attributes that belong to it, as opposed to the caller's own HTML attributes, so
they are excluded from `restAttributes`/`c:rest`. How, depends on the component:

- **A record's props are its record components**, one for one, other than the one holding
  `CompositionComponentContext`. `variant` above is a prop with no declaration needed at all. Override the
  attribute name a component binds from — when the kebab-cased derivation does not fit — with `@Prop`:
  `@Prop("data-variant") String variant`.
- **A class declares props by annotating fields with `@Prop`:**

  ```java
  @Prop private final String variant;
  ```

  A field's attribute name defaults to its own name, kebab-cased. `@Prop("...")` overrides it, the same as
  on a record component.

Either way, declaring a prop is what excludes it, not reading it. A component can read an attribute to
validate, log, or derive from it without declaring it. That attribute still flows through to `c:rest`, like
any attribute the component never touches:

```java
// "type" is read here only to validate it — not declared as a prop, so it still
// reaches c:rest, exactly like an attribute the component never touches at all.
Object rawType = context.attributes().get("type");
if (rawType != null && !Set.of("button", "submit", "reset").contains(rawType.toString())) {
    throw new IllegalArgumentException("unknown button type: " + rawType);
}
```

This read-without-declaring pattern only works for a plain or `th:`-prefixed `type` at the call site. A
`c:`-prefixed one has no such escape: `c:` always names a prop, so `c:type` on a component that does not
declare `type` fails before the constructor above ever runs, regardless of whether the constructor would
have read it. Use `th:type="${...}"` for a dynamic value that is meant to be read this way.

This also means you can tell what a component accepts from its declaration alone, without rendering it —
every `c:`-prefixed name that can legally appear on its tag is exactly its declared props. A class with no
`@Prop` fields at all simply has no props.

## Attribute defaults

**Default in the component, declared as a prop and applied explicitly:**

Declare the attribute as a prop, so `restAttributes` excludes it. If the template needs the value itself —
building a CSS class, say — apply an explicit fallback. Use the constructor for a class, or a compact
constructor for a record.

```java
@Prop private final String variant;
// ...
Object raw = context.attributes().get("variant");
this.variant = raw != null ? raw.toString() : "primary";
```

```html
<button th:classappend="${'btn-' + this.variant}" c:rest>
```

**Default in the template, overridable by the caller:**

For raw HTML attributes where you want a sensible default, but the caller should be able to override it, put
the default as a static attribute in the template. Do not declare it as a prop. `c:rest` overrides a static
attribute when the caller passes the same key:

```html
<button type="button" c:rest>
    <c:slot />
</button>
```

```html
<c:button>Submit</c:button>               <!-- renders: <button type="button">Submit</button> -->
<c:button type="submit">Submit</c:button> <!-- renders: <button type="submit">Submit</button> -->
```

## Rest attributes

`restAttributes` exposes every attribute the caller passed that is not a declared prop, as a map:

```java
public class Button extends AbstractCompositionComponent {
    @Prop private final String variant;

    public Button(CompositionComponentContext context) {
        super(context);
        Object raw = context.attributes().get("variant");
        this.variant = raw != null ? raw.toString() : "primary";
        // variant is a declared prop — type, disabled, and anything else the caller passes are not
    }

    public String getVariant() {
        return variant;
    }
}
```

### `c:rest` — spread onto an element

Place `c:rest` on any element in a component template to spread every attribute that is not a declared prop
onto that element:

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

```java
// access in the class
Map<String, Object> dataAttrs = new HashMap<>();
restAttributes().forEach((key, value) -> {
    if (key.startsWith("data-")) {
        dataAttrs.put(key, value);
    }
});
```

`c:rest` is syntactic sugar over `restAttributes`. It spreads the map onto the element and merges it with any
static attributes already present. The caller wins on conflict.

`th:text`/`th:utext` on the component tag are the one exception. The dialect never treats them as a plain or
`c:`-prefixed attribute, so they never appear in `restAttributes` either. See
[Slots](slots.md#thtextthutext-shorthand) — they fill the default slot instead.
