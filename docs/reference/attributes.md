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

## Attribute defaults

**Default in the component class — consumed and applied explicitly:**

Read the attribute in the constructor with an explicit fallback. The attribute is consumed (will not appear in `restAttributes`) and applied explicitly in the template:

```java
Object raw = context.attributes().get("variant");
this.variant = raw != null ? raw.toString() : "primary";
```

```html
<button th:classappend="${'btn-' + this.variant}" c:rest>
```

**Default in the template — overridable by the caller:**

For raw HTML attributes where you want a sensible default but the caller should be able to override, put the default as a static attribute in the template and do not consume it in the class. `c:rest` overrides static attributes when the caller passes the same key:

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

The dialect tracks which attributes have been accessed via `context.attributes().get("key")`. Any attribute not accessed is considered _unconsumed_.

`restAttributes` exposes the unconsumed attributes as a map:

```java
public class Button extends CompositionComponent {
    private final String variant;

    public Button(CompositionComponentContext context) {
        super(context);
        Object raw = context.attributes().get("variant");
        this.variant = raw != null ? raw.toString() : "primary";
        // variant is consumed — type, disabled, and anything else the caller passes are not
    }

    public String getVariant() {
        return variant;
    }
}
```

### `c:rest` — spread onto an element

Place `c:rest` on any element in a component template to spread all unconsumed attributes onto that element:

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

`variant` was consumed in the constructor. `type` and `disabled` were not, so they pass through to the element.

### `restAttributes` — access programmatically

`restAttributes` is also available directly in the template or the component class:

```html
<!-- iterate unconsumed attributes manually -->
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
