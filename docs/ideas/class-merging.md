# Class Merging

## The Idea

Components should be able to define base CSS classes while allowing callers to extend or override them — the same pattern popularised by `clsx` and `tailwind-merge` in the React ecosystem.

```html
<!-- caller adds classes on top of the component's own defaults -->
<c:button class="mt-4 w-full">Submit</c:button>
```

The component merges its defaults with the caller-supplied classes and exposes the result:

```kotlin
class Button(ctx: CompositionComponentContext) : CompositionComponent(ctx) {
    val classes = mergeClasses(
        "px-4 py-2 rounded bg-blue-600 text-white",
        ctx.attributes["class"]
    )
}
```

The merged value is then available to the template or any other delivery mechanism as `${this.classes}`.

## Merging Strategies

**Simple concatenation** — append caller classes to component defaults. Works for BEM/semantic CSS.

**Tailwind-aware merging** — when two classes conflict (`bg-blue-600` vs `bg-red-500`), the last one wins and the earlier one is dropped. This is what [tailwind-merge](https://github.com/nickvdyck/tailwind-merge-java) does. Critical for Tailwind projects — otherwise the DOM carries conflicting utilities and specificity decides the winner unpredictably.

**Caller wins / component wins modes** — sometimes the component should lock certain classes (structural, accessibility-critical) and only allow additive caller input.

## API Shape

A utility method available on `CompositionComponent` (or a standalone utility class):

```kotlin
// simple concatenation
fun mergeClasses(vararg parts: Any?): String

// tailwind-aware conflict resolution
fun twMerge(vararg parts: Any?): String
```

Handles nulls, empty strings, collections — same ergonomics as `clsx`.

## Prior Art

- **clsx** / **classnames** (JS) — conditional class joining
- **tailwind-merge** (JS), **tailwind-merge-java** (JVM port) — conflict-aware Tailwind class merging
- **CVA** (Class Variance Authority, JS) — variant-based class composition; interesting model for components with multiple visual variants (`size`, `intent`, etc.)

## Open Questions

- Ship a basic `mergeClasses` utility in the dialect itself, or leave it to the consuming project?
- Tailwind-merge is a significant dependency for a non-Tailwind project — should it be optional / a separate companion module?
- CVA-style variant API worth exploring: `Button(intent = "primary", size = "lg")` maps to a predefined class set.

## See Also

- [Component Lifecycle Hooks](component-lifecycle.md) — one way to deliver merged classes to the root element without template boilerplate
