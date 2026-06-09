# Component Lifecycle Hooks

## The Idea

Components currently influence rendering only through the template — properties and methods on `this` are read by Thymeleaf expressions. A lifecycle hook would let the component class also influence the rendered output programmatically, without requiring template boilerplate.

The primary candidate is a hook that contributes attributes to the component's root element:

```kotlin
open class CompositionComponent(...) {
    open fun rootAttributes(): Map<String, String> = emptyMap()
}
```

The processor calls this after instantiation and merges the returned attributes onto the first element of the rendered template.

## Lifecycle Points for SSR

Unlike custom elements (which have mount/unmount cycles), server-side rendering has a linear lifecycle. The meaningful hook points are:

| Hook | When | Use cases |
|------|------|-----------|
| Constructor (exists) | Component instantiated, attributes available | Read attrs, compute derived state |
| `rootAttributes()` | Before root element is emitted | Add/merge HTML attributes programmatically |
| `beforeRender()` | Before template is processed | Set additional template variables |

`afterRender()` has no practical use in SSR — the output is already a string by that point.

**Constructor vs `rootAttributes()`:** the constructor is the right place to *compute* values (it has access to `CompositionComponentContext`). `rootAttributes()` is just how the processor *reads* them back. They work together, not as alternatives.

## Use Cases

**Class merging** — component contributes base CSS classes, merged with caller-supplied ones. Primary motivating case. See [Class Merging](class-merging.md).

**Stimulus controller wiring** — a `StimulusComponent` base class overrides `rootAttributes()` to emit `data-controller` derived from the component's class name. No template boilerplate, no per-component wiring. See [Component-Scoped Assets](component-scoped-assets.md) for the JS side.

**Alpine.js** — emit `x-data` on the root element to activate an Alpine component.

**ARIA defaults** — structural components (modals, dialogs, navigation landmarks) emit the correct `role` attribute by default.

**Test automation** — a `data-testid` derived from the component name, emitted automatically in non-production builds.

## Design Notes

- `rootAttributes()` should merge with, not replace, attributes already present on the root element in the template. Caller-supplied attributes on `<c:button class="...">` and component defaults should all coexist.
- The hook is opt-in via `open fun` — components that don't need it pay no cost.
- The full Thymeleaf `ITemplateContext` is not available inside the component class by design. `rootAttributes()` works with what was already computed in the constructor from `CompositionComponentContext`.

## Open Questions

- Should `rootAttributes()` receive the existing root element attributes as input, so the component can make merge decisions? Or is that the job of utilities like `mergeClasses()`?
- Is `beforeRender()` worth adding, or is the constructor sufficient for setting template variables via `ctx.setVariable()`?
- Should attribute precedence be: template wins, `rootAttributes()` fills gaps — or the other way around?
