# Components

A component is a template. A class is an optional enhancement, bound to it explicitly.

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

The tag name is always the template's own file name — never derived from a class:

| Template | Tag |
|---|---|
| `button.html` | `<c:button />` |
| `nav-bar.html` | `<c:nav-bar />` |

Subdirectories under the templates path are cosmetic to the file path, not to the tag: `components/forms/button.html` and `components/layout/button.html` both produce `<c:button>`, which collides — the dialect reports it at startup, naming both templates.

## Binding a class with `impl`

`impl` names the class to bind, resolved two ways, tried in order:

1. **Relative to the source's `defaultPackage`** (see [Setup](setup.md)) — a bare simple name (`impl="Button"`) or a relative dotted path (`impl="forms.Button"`).
2. **As a fully-qualified name** (`impl="com.example.demo.components.Button"`).

Resolution happens at startup, not at first render, so a typo'd or moved class fails loudly with both attempts named:

```
Composition Dialect: components/button.html declares impl="Buton", which could not be resolved — tried
"com.example.demo.components.Buton" (relative to this source's default package) and "Buton" (as a
fully-qualified name).
```

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

There is no type declaration for a classless prop — it is whatever the caller's attribute evaluated to, exactly like an attribute with no matching class field today. If your props are domain types with a checked contract, or need coercion, you have outgrown classless: write the record.

## Context

See [Attributes](attributes.md) for how attributes are passed, declared as props, and read.
See [Context](context.md) for locale, messages, and variable scope.

## Unresolved tags

A tag under the dialect prefix that no template claims — a typo, a missing `ComponentSource`, or a tag meant for a different dialect prefix entirely — fails when it renders, naming the tag and, where it can tell, the likely cause: a misspelling inside a namespace the dialect knows about (naming what that namespace actually offers), a namespace nothing here provides ("is a dependency missing?"), or a plain lookup among the application's own tags. Resolution happens only for a tag that actually renders: one behind a false `th:if`, an untaken `th:each`, or any other branch that never runs is never checked, the same as a real component tag would be.

A template under the templates path that declares no `<c:component>` at all is not an error — it is just a template, and partials or layouts are free to live in the same tree. It only becomes a problem if something tries to use it as a component tag, in which case the error names the file and says to add the declaration.
