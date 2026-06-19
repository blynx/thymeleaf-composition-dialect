# Context

`CompositionComponentContext` is passed to every component constructor. Beyond attributes (see [Attributes](attributes.md)), it provides access to locale, messages, and the surrounding template variable scope.

## Locale

```kotlin
class Price(context: CompositionComponentContext) : CompositionComponent(context) {
    val amount: Double = context.attributes["amount"] as Double
    val formatted: String = NumberFormat.getCurrencyInstance(context.locale).format(amount)
}
```

## Messages

`context.message(code, vararg params)` resolves against the configured Thymeleaf message source. Returns the code itself if no message is found:

```kotlin
val label: String = context.message("price.label")
val error: String = context.message("validation.min", 5)
```

## Template variable scope

Components can read and write the surrounding template variable scope — useful for implicit parent-to-descendant communication without threading values through every level of attributes.

`context.variable(name)` reads from the template scope (equivalent to `${name}` in the template).  
`context.setVariable(name, value)` writes a variable visible to all descendants within the component's rendered output.

```kotlin
class MagicHeadings(context: CompositionComponentContext) : CompositionComponent(context) {
    val level: Int = ((context.variable("parentMagicHeadings") as? MagicHeadings)?.level ?: 0) + 1
    init { context.setVariable("parentMagicHeadings", this) }
}

class Heading(context: CompositionComponentContext) : CompositionComponent(context) {
    val level: Int = context.attributes["level"]?.toString()?.toIntOrNull()
        ?: (context.variable("parentMagicHeadings") as? MagicHeadings)?.level
        ?: 1
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
