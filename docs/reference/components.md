# Components

A component is a template — which can be optionally enhanced by a class.

## The declaration

`<c:component>` is what makes a template a component — without it, a file under the templates path is just a plain Thymeleaf template (a partial, a layout), not a component at all. It is a self-closing directive, placed anywhere in the template, at most once:

```html
<!-- templates/components/button.html -->
<c:component impl="Button"/>
<button th:classappend="${'btn-' + this.variant}">
    <c:slot />
</button>
```

## Naming

The tag name is always the template's own file name.

| Template | Tag |
|---|---|
| `button.html` | `<c:button />` |
| `nav-bar.html` | `<c:nav-bar />` |

Subdirectories under the templates path are cosmetic to the file path, not to the tag: `components/forms/button.html` and `components/layout/button.html` both produce `<c:button>`, which collides — the dialect reports it at startup, naming both templates.

## Binding a class with `impl`

`impl` names the class to bind, resolved two ways, tried in order:

1. **Relative to the source's `defaultPackage`** (see [Setup](setup.md)) — a bare simple name (`impl="Button"`) or a relative dotted path (`impl="forms.Button"`).
2. **As a fully-qualified name** (`impl="com.example.demo.components.Button"`).

Resolution happens at startup and unresolvable definitions are reported.

Use the short form for your own application's components, where the package is obvious and a rename is a one-line fix. Use a fully-qualified name in a published library, so its templates describe themselves to a reader who has never seen the source's `defaultPackage`.

Nothing is inferred from the class name. A template with no `impl` is definitively classless — see [below](#classless-components) — there is no convention-matching fallback, so a missing binding never silently degrades.

## Component class

`CompositionComponent` is an interface with one method, `context()`. It returns this component's
`CompositionComponentContext` — its attributes, its slot names, and access to locale, messages, and the
template variable scope.

The simplest way to implement it is a record. Its components are its props, one for one, with no extra
declaration:

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

You can also write a component as a plain class. Extend `AbstractCompositionComponent`, and declare each prop
as a field with `@Prop`:

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

`AbstractCompositionComponent` stores the context for you and implements `context()` from it. Use a plain
class when a component must extend something else, needs custom coercion logic, or just reads better to you
that way.

The component instance is available in its template as `${this}`. A record's components are readable as
properties with no `get` prefix — `${this.variant}` resolves to `variant()`. A class's public getters work
the same way — `${this.variant}` resolves to `getVariant()`. See [Attributes](attributes.md) for how props
are declared and coerced.

A template's `props` and a bound class's props are the same list two ways of saying it, never two lists: declaring `props` on `<c:component>` *alongside* `impl` is rejected at startup, because the class's own shape (its record components, or its `@Prop` fields) is what declares its props — a template-side list next to `impl` could not mean anything else.

## Classless components

Omit `impl` and the template is still a component — just one with no backing class. `${this}` still works: the dialect builds a built-in component whose property access falls through to the props you declare with `props`, a comma-separated, kebab-case attribute on `<c:component>`:

```html
<!-- templates/components/stack.html -->
<c:component/>
<div class="stack" c:rest><c:slot/></div>
```

Zero declared props is legal — the declaration itself is what makes the file a component, not the props list. `<c:stack class="p-4">` forwarding its attributes onto its root `div` is a legitimate component and this is its whole definition.

```html
<!-- templates/components/callout.html -->
<c:component props="variant"/>
<div th:classappend="${'callout--' + this.variant}" c:rest><c:slot/></div>
```

`${this.variant}` reads the declared prop; anything else the caller passed reaches `c:rest`, exactly as it would with a class. Declared props are the component's whole API in both modes: an undeclared attribute is reachable through `c:rest` and nowhere else, and `${this.somethingUndeclared}` on a classless component reads nothing, the same "declare it to read it" rule the class side already has.

The flat `props="…"` list says a prop exists and nothing more — no type, no default, no required flag. For that, use the paired form.

## Per-prop declarations with `<c:prop>`

`props="…"` and `<c:prop>` say the same thing two ways — a component's props, never both at once on the same declaration. The paired form trades the flat list for one `<c:prop>` per prop, each carrying its own shape:

```html
<!-- templates/components/price.html -->
<c:component>
    <c:prop name="variant" default="primary"/>
    <c:prop name="quantity" type="int" required/>
    <c:prop name="amount" type="java.math.BigDecimal"/>
</c:component>
<span th:classappend="${'price--' + this.variant}" th:text="${this.amount}">?</span>
```

`<c:component>`'s children are declarations, never markup — the component's own markup is its sibling, as in the example above. Writing anything else inside it, or leaving a `<c:prop>` open instead of self-closing it, fails at startup naming the file.

| Attribute | Meaning |
|---|---|
| `name` | The prop's name, kebab-case, required |
| `required` | The caller must provide this attribute — checked before any default |
| `default` | A literal fallback used when the attribute is omitted — never an expression; the declaration describes a prop's shape, not its logic |
| `type` | A short name (`string`, `int`, `long`, `double`, `boolean`) or a fully-qualified class name |

`type` means two different things depending on which kind it names:

- **A primitive or `string`** is a *conversion* instruction, parsing the caller's plain-text attribute the same way `PropCoercion` already does for a record's own fields.
- **Anything else** is a *contract assertion*: whatever expression the caller passed must evaluate to that type. `<c:price c:amount="${order.total}"/>` passes an already-typed `BigDecimal` straight through; `<c:price amount="12.00"/>` fails loudly, since there is no plain-text conversion to an arbitrary domain type.

This is exactly what a classless component otherwise cannot have — an object-valued prop with a checked contract — and it is the signal that you have probably outgrown classless if it is most of what your props are: `BigDecimal` on an otherwise markup-only `<c:price>` is fine; three domain props and a nested property chain means write the record.

Resolution happens at scan time, like `impl` — a typo'd `type` fails at startup, naming the file and the prop.

## Context

See [Attributes](attributes.md) for how attributes are passed, declared as props, and read.
See [Context](context.md) for locale, messages, and variable scope.

## Unresolved tags

A tag under the dialect prefix that no template claims — a typo, a missing `ComponentSource`, or a tag meant for a different dialect prefix entirely — fails when it renders, naming the tag and, where it can tell, the likely cause: a misspelling inside a namespace the dialect knows about (naming what that namespace actually offers), a namespace nothing here provides ("is a dependency missing?"), or a plain lookup among the application's own tags. Resolution happens only for a tag that actually renders: one behind a false `th:if`, an untaken `th:each`, or any other branch that never runs is never checked, the same as a real component tag would be.

A template under the templates path that declares no `<c:component>` at all is not an error — it is just a template, and partials or layouts are free to live in the same tree. It only becomes a problem if something tries to use it as a component tag, in which case the error names the file and says to add the declaration.
