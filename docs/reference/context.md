# Context

The dialect passes a `CompositionComponentContext` to every component. Beyond attributes (see
[Attributes](attributes.md)), it gives access to locale, messages, and the surrounding template variable
scope.

## Locale

```java
public record Price(double amount, CompositionComponentContext context) implements CompositionComponent {
    public String formatted() {
        return NumberFormat.getCurrencyInstance(context.locale()).format(amount);
    }
}
```

## Messages

`context.message(String code, Object... params)` resolves against the configured Thymeleaf message source.
It returns the code itself if it finds no message:

```java
String label = context.message("price.label");
String error = context.message("validation.min", 5);
```

## Template variable scope

A component can read and write the surrounding template variable scope. This gives you implicit
parent-to-descendant communication, without threading a value through every level of attributes.

`context.variable(name)` reads from the template scope. It is equivalent to `${name}` in the template.
`context.setVariable(name, value)` writes a variable. Every descendant within the component's rendered
output can see it.

```java
public record Outline(Integer level, CompositionComponentContext context) implements CompositionComponent {
    public Outline {
        if (level == null) {
            level = context.variable("outline") instanceof Outline parent ? parent.level() + 1 : 1;
        }
        context.setVariable("outline", this);
    }
}

public record Heading(Integer level, CompositionComponentContext context) implements CompositionComponent {
    public Heading {
        if (level == null) {
            level = context.variable("outline") instanceof Outline outline ? outline.level() : 1;
        }
    }
}
```

```html
<c:outline>
    <c:heading>First level</c:heading>       <!-- level 1 -->
    <c:outline>
        <c:heading>Second level</c:heading>  <!-- level 2 -->
    </c:outline>
</c:outline>
<c:heading>Back to default</c:heading>       <!-- level 1, no enclosing outline -->
```

`level` is a boxed `Integer`, not a primitive `int`, on purpose. An attribute the caller omits must be
different from one set to an explicit `0`, and only a reference type can hold `null`.

Because `level` is a record component, it is a prop too, like any other. An explicit `level` attribute on
`Outline` itself overrides the computed nesting value — the same way an explicit `level` on `Heading`
overrides what it would otherwise inherit:

```html
<c:outline level="5">
    <c:heading>reports 5</c:heading>
</c:outline>
```
