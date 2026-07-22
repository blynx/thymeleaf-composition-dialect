# Component Registry

A small, queryable list of the components the dialect knows about — extracted from the discovery
that already happens at startup, so other features can ask "what components exist?"

## The Problem

Discovery already runs: `CompositionDialect.getProcessors()` calls
`Reflections(componentPackage).getSubTypesOf(CompositionComponent)`, derives a kebab-case tag name
per class, and builds one processor each. But that list is **consumed and thrown away** inside
`getProcessors()` — nothing else can enumerate the components, their tag names, prefixes, or template
paths. Several planned features need exactly that, and each would otherwise re-implement the same
scan and naming logic.

## The Idea

Factor discovery into a `ComponentRegistry` — a list of descriptors built once from the same scan:

```kotlin
data class ComponentDescriptor(
    val componentClass: Class<out CompositionComponent>,
    val prefix: String,        // the dialect prefix this component is registered under
    val tagName: String,       // kebab-case, e.g. "nav-bar"
    val templatePath: String,  // as computed by buildComponentPath
    // val module: String?     // for the per-module direction — see Module Organization
)
```

`getProcessors()` becomes a *consumer* of the registry rather than the place discovery lives. The
naming rule (PascalCase → kebab-case) and path rule (`buildComponentPath`) move to, or are shared
with, the registry so there is a single source of truth.

Under Spring Boot the registry is exposed as a bean so other components (a playground controller, a
verification test) can inject it. With the [Module Organization](module-organization.md) direction —
one dialect/prefix per module — the registry should **aggregate across all registered
`CompositionDialect` instances**, giving a single unified view of every component across every module.

## Who needs it (and why do it first)

This is the shared foundation under three other ideas, which is why it's worth extracting before
tackling them:

- **[Module Organization](module-organization.md)** — fail-fast collision detection needs to see all
  registered tag names to spot duplicates; the unified cross-dialect view lives here.
- **[Component Playground](component-playground.md)** — needs to enumerate components (and attach
  stories) to list and render them.
- **[Developer Tooling](developer-tooling.md)** — scaffolding reuses the same discovery and naming
  logic; the registry is its natural home.

## Scope

Deliberately small: a **behavior-preserving refactor** (move discovery out of `getProcessors`) plus a
**query surface** (the descriptor list + a bean). No change to how components render. That makes it
low-risk, independently testable, and high-leverage — a good first step that de-risks everything
built on top.

### Pros

- Single source of truth for discovery, naming, and template-path rules
- Unblocks Module Organization, the Playground, and Developer Tooling
- Small, behavior-preserving, testable in isolation

### Cons

- A little indirection between discovery and processor construction
- Needs a decision on the descriptor's fields and whether it aggregates across dialects (couples it
  to the Module Organization model)

## See Also

- [Module Organization](module-organization.md) — the cross-dialect aggregation and collision detection
- [Component Playground](component-playground.md) — enumerates the registry to browse components
- [Developer Tooling](developer-tooling.md) — scaffolding reuses discovery and naming
