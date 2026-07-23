# Context

`CompositionComponentContext` is passed to every component constructor. Beyond attributes (see [Attributes](attributes.md)), it provides access to locale, messages, and the surrounding template variable scope.

## Locale

```java
public class Price extends CompositionComponent {
    private final double amount;
    private final String formatted;

    public Price(CompositionComponentContext context) {
        super(context);
        this.amount = (Double) context.attributes().get("amount");
        this.formatted = NumberFormat.getCurrencyInstance(context.locale()).format(amount);
    }

    public double getAmount() {
        return amount;
    }

    public String getFormatted() {
        return formatted;
    }
}
```

## Messages

`context.message(String code, Object... params)` resolves against the configured Thymeleaf message source. Returns the code itself if no message is found:

```java
String label = context.message("price.label");
String error = context.message("validation.min", 5);
```

## Template variable scope

Components can read and write the surrounding template variable scope — useful for implicit parent-to-descendant communication without threading values through every level of attributes.

`context.variable(name)` reads from the template scope (equivalent to `${name}` in the template).  
`context.setVariable(name, value)` writes a variable visible to all descendants within the component's rendered output.

```java
public class MagicHeadings extends CompositionComponent {
    private final int level;

    public MagicHeadings(CompositionComponentContext context) {
        super(context);
        // read the parent's level BEFORE registering ourselves, so nested instances increment
        int parentLevel = context.variable("parentMagicHeadings") instanceof MagicHeadings parent
                ? parent.getLevel() : 0;
        this.level = parentLevel + 1;
        context.setVariable("parentMagicHeadings", this);
    }

    public int getLevel() {
        return level;
    }
}

public class Heading extends CompositionComponent {
    private final int level;

    public Heading(CompositionComponentContext context) {
        super(context);
        this.level = resolveLevel(context);
    }

    private static int resolveLevel(CompositionComponentContext context) {
        Object raw = context.attributes().get("level");
        if (raw != null) {
            try {
                return Integer.parseInt(raw.toString());
            } catch (NumberFormatException ignored) {
                // not a number: fall back to the inherited / default level
            }
        }
        if (context.variable("parentMagicHeadings") instanceof MagicHeadings parent) {
            return parent.getLevel();
        }
        return 1;
    }

    public int getLevel() {
        return level;
    }
}
```

```html
<c:magic-headings>
    <c:heading>First level</c:heading>       <!-- level 1 -->
    <c:magic-headings>
        <c:heading>Second level</c:heading>  <!-- level 2 -->
    </c:magic-headings>
</c:magic-headings>
<c:heading>Back to default</c:heading>       <!-- level 1 (no context) -->
```
