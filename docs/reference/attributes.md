# Attributes

## Passing attributes

Plain attributes arrive as strings. Attributes prefixed with `c:` are evaluated as Thymeleaf expressions
before the dialect passes them on:

```html
<c:button variant="danger" />              <!-- variant = "danger" (string) -->
<c:button c:variant="${currentVariant}" /> <!-- variant = value of currentVariant -->
```

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

This also means you can tell what a component accepts from its declaration alone, without rendering it. A
class with no `@Prop` fields at all simply has no props.

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
