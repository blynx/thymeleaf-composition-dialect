# Attributes

## Passing attributes

Plain attributes arrive as strings. Attributes prefixed with `c:` are evaluated as Thymeleaf expressions before being passed:

```html
<c:button variant="danger" />              <!-- variant = "danger" (string) -->
<c:button c:variant="${currentVariant}" /> <!-- variant = value of currentVariant -->
```

Attributes are read in the component constructor via `context.attributes().get("key")`:

```java
public class Button extends CompositionComponent {
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

## Declaring props

A component declares the attributes that belong to it — as opposed to the caller's own HTML attributes — by listing them in a static `props` field, by their plain name — `"variant"`, whether the caller wrote `variant="..."` or `c:variant="..."`:

```java
public static final Set<String> props = Set.of("variant");
```

Declaring `props` is what excludes an attribute from `restAttributes`/`c:rest` — not reading it. A component can read an attribute to validate, log, or derive from it without declaring it, and it still flows through to `c:rest` like any attribute the component never touches:

```java
// "type" is read here only to validate it — not declared in props, so it still
// reaches c:rest, exactly like an attribute the component never touches at all.
Object rawType = context.attributes().get("type");
if (rawType != null && !Set.of("button", "submit", "reset").contains(rawType.toString())) {
    throw new IllegalArgumentException("unknown button type: " + rawType);
}
```

This also means what a component accepts is knowable from its declaration alone, without rendering it.

## Attribute defaults

**Default in the component class — declared as a prop and applied explicitly:**

Declare the attribute in `props` so it's excluded from `restAttributes`. If the template needs the value itself — building a CSS class, say — read it in the constructor with an explicit fallback, e.g.:

```java
public static final Set<String> props = Set.of("variant");

Object raw = context.attributes().get("variant");
this.variant = raw != null ? raw.toString() : "primary";
```

```html
<button th:classappend="${'btn-' + this.variant}" c:rest>
```

**Default in the template — overridable by the caller:**

For raw HTML attributes where you want a sensible default but the caller should be able to override, put the default as a static attribute in the template and do not declare it in `props`. `c:rest` overrides static attributes when the caller passes the same key:

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

`restAttributes` exposes every attribute the caller passed that isn't declared in `props`, as a map:

```java
public class Button extends CompositionComponent {
    public static final Set<String> props = Set.of("variant");

    private final String variant;

    public Button(CompositionComponentContext context) {
        super(context);
        Object raw = context.attributes().get("variant");
        this.variant = raw != null ? raw.toString() : "primary";
        // variant is declared in props — type, disabled, and anything else the caller passes are not
    }

    public String getVariant() {
        return variant;
    }
}
```

### `c:rest` — spread onto an element

Place `c:rest` on any element in a component template to spread every attribute not declared in `props` onto that element:

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

`variant` is declared in `props`. `type` and `disabled` are not, so they pass through to the element —
whether or not the constructor happens to read them.

### `restAttributes` — access programmatically

`restAttributes` is also available directly in the template or the component class:

```html
<!-- iterate the rest attributes manually -->
<div th:each="attr : ${this.restAttributes}" th:attr="${attr.key}=${attr.value}">
```

```java
// access in the class
Map<String, Object> dataAttrs = new HashMap<>();
getRestAttributes().forEach((key, value) -> {
    if (key.startsWith("data-")) {
        dataAttrs.put(key, value);
    }
});
```

`c:rest` is syntactic sugar over `restAttributes` — it spreads the map onto the element, merging with any static attributes already present (caller wins on conflict).

`th:text`/`th:utext` on the component tag are the one exception: they're never treated as a plain or `c:`-prefixed attribute, so they never appear in `restAttributes` either. See [Slots](slots.md#thtextthutext-shorthand) — they fill the default slot instead.
