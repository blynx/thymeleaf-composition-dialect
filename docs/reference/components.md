# Components

A component is a template. `<c:component>` marks it as one; everything else in the file is ordinary Thymeleaf markup.

## The simplest component

```html
<!-- templates/components/stack.html -->
<c:component/>
<div class="stack" c:rest><c:slot/></div>
```

```html
<c:stack class="p-4">
    <p>One</p>
    <p>Two</p>
</c:stack>
```

The tag is always the template's own file name: `stack.html` gives `<c:stack>`. `c:rest` spreads every attribute the caller passed onto wherever you put it — see [Attributes](attributes.md). `<c:slot/>` is where the caller's content goes — see [Slots](slots.md).

## Props

Name the attributes your component reads with `props`, a comma-separated, kebab-case list:

```html
<!-- templates/components/callout.html -->
<c:component props="variant"/>
<div th:classappend="${'callout--' + this.variant}" c:rest><c:slot/></div>
```

```html
<c:callout variant="warning">Careful.</c:callout>
```

`${this.variant}` reads the prop; anything not declared in `props` still reaches `c:rest`. Reading an undeclared name, e.g. `${this.somethingElse}`, gives nothing.

## Per-prop rules with `<c:prop>`

For anything beyond "this attribute exists," write one `<c:prop>` per prop instead of the flat list:

```html
<!-- templates/components/price.html -->
<c:component>
    <c:prop name="variant" default="primary"/>
    <c:prop name="quantity" type="int" required/>
    <c:prop name="amount" type="java.math.BigDecimal"/>
</c:component>
<span th:classappend="${'price--' + this.variant}" th:text="${this.amount}">?</span>
```

| Attribute | Meaning |
|---|---|
| `name` | The prop's name, kebab-case |
| `required` | Fails if the caller does not provide this attribute |
| `default` | A literal value used when the attribute is omitted |
| `type` | `string`, `int`, `long`, `double`, `boolean`, or a fully-qualified class name |

A primitive or `string` type parses the caller's plain-text attribute. Any other type is a contract: the caller must pass an expression that already evaluates to it — `<c:price th:amount="${order.total}"/>` works; `<c:price amount="12.00"/>` does not, since there is no plain-text parse into an arbitrary class.

`<c:component>`'s children must all be `<c:prop/>`, each self-closing. Anything else inside it — including an unclosed `<c:prop name="…">` — is an error. Use `props="…"` or `<c:prop>`, never both on the same component.

## Binding a class

Add `impl` to hand the component to a class or record instead:

```html
<!-- templates/components/button.html -->
<c:component impl="Button"/>
<button th:classappend="${'btn-' + this.variant}">
    <c:slot />
</button>
```

`impl` resolves two ways, tried in order: relative to the source's `defaultPackage` (a bare name like `Button`, or a relative path like `forms.Button`), then as a fully-qualified name. Use the short form for your own components; use the fully-qualified form in a published library.

Once a class is bound, the class's own fields declare the props — do not also write `props` or `<c:prop>` on the same `<c:component>`.

### Writing the class

A record's components are its props, one for one:

```java
package com.example.demo.components;

import blynx.thymeleaf.compositiondialect.CompositionComponent;
import blynx.thymeleaf.compositiondialect.CompositionComponentContext;

public record Button(String variant, CompositionComponentContext context) implements CompositionComponent {
    public Button {
        variant = variant != null ? variant : "primary";
    }
}
```

Or a plain class, extending `AbstractCompositionComponent`, with each prop a field annotated `@Prop`:

```java
package com.example.demo.components;

import blynx.thymeleaf.compositiondialect.AbstractCompositionComponent;
import blynx.thymeleaf.compositiondialect.CompositionComponentContext;
import blynx.thymeleaf.compositiondialect.Prop;

public class Button extends AbstractCompositionComponent {

    @Prop
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

Either way, the instance is `${this}` in the template: a record's components read as properties (`${this.variant}` → `variant()`), a class's public getters the same way (`${this.variant}` → `getVariant()`).

## Naming

The tag is always the template's file name, regardless of subdirectory: `components/forms/button.html` and `components/layout/button.html` both give `<c:button>` — pick a different file name if you need both.

## Reference

- [Attributes](attributes.md) — passing attributes, props, defaults, `c:rest`
- [Slots](slots.md) — default and named slots
- [Context](context.md) — locale, messages, variable scope
- [Component libraries](libraries.md) — publishing components as a jar, and importing one

### Errors

- **A misspelled or unregistered tag** fails when it renders, naming the tag.
- **A template with no `<c:component>`** is not a component — it can be a plain partial or layout living in the same tree — until something tries to call it as one, which fails naming the file.
- **Two templates producing the same tag** fail at startup, naming both files.
