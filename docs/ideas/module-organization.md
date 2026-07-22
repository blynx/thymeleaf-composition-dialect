# Module Organization

How to organize components when the application is a modulith — many modules, each owning
its own components — and how to keep same-named components in different modules from colliding.

## The Problem

Today the dialect discovers components by scanning a **single** `componentPackage`
(`CompositionDialect.getProcessors`), derives each tag name from the class's *simple* name
(`Card` → `<c:card>`), and resolves every template under one global `componentsPath`
(`CompositionElementModelProcessor.buildComponentPath`). That works for a single-module app but
breaks down in a modulith organized like:

```
module-a/
  components/
    Form.kt, This.kt, That.kt
module-b/
  components/
    This.kt, That.kt, Bla.kt
```

Two problems surface:

1. **Collisions break silently.** `module-a` and `module-b` both defining `This` produces two
   processors matching the same element name `<c:this>`. They land in the same `HashSet<IProcessor>`
   and the winner is undefined — no error, just one component quietly shadowing the other.
2. **No per-module discovery or template root.** Discovery is one package; the template root is one
   global `componentsPath`. A module can't own where its components live or how they're found.

The scratch notes (`_notes.md`) sketch two escape routes: a call-site override
(`<c:this c:from="module-b/components/">`), or letting the component class declare where its
template lives with a per-module base path — but both leave the core question open: *how do you
avoid colliding two same-named components from different modules?*

## The Idea: one dialect (prefix) per module

Thymeleaf is built to run **many dialects, each with its own prefix, in one engine** — that is how
`th:`, `sec:`, and `layout:` coexist. Lean into that: give each module its own `CompositionDialect`
with its own prefix.

```html
<c:button />                 <!-- shared design-system component -->
<catalog:product-card />     <!-- owned by the catalog module -->
<order:cart />               <!-- owned by the order module -->
```

Collisions become **structurally impossible**: `<catalog:product-card>` and `<order:product-card>`
are different tags. The `c` prefix is kept for a shared/design-system component set that everything
uses; module-specific prefixes carry each module's own components.

The important part: **this needs no changes to the rendering code.** Each component type still maps
to exactly one processor under exactly one prefix, so slots, attributes, the rest/spread handling,
and the per-processor fragment cache all keep working unchanged. Module organization is a
*registration* concern, not a *rendering* one.

## Registration: each module owns its dialect bean

Rather than a central registry, each module declares its own dialect in its own configuration — the
module owns its component namespace the same way it owns its packages:

```kotlin
@Configuration
class OrderComponentsConfig {
    @Bean
    fun orderComponentsDialect() = CompositionDialect(
        componentPackage = "com.example.order.components",
        prefix = "order",
    )
}
```

Spring Boot's Thymeleaf auto-configuration collects **every** `IDialect` bean and registers it on
the engine, so several `CompositionDialect` beans simply coexist. Standalone (non-Spring) usage is
the same shape — one `addDialect` per module:

```kotlin
engine.addDialect(CompositionDialect("com.example.catalog.components", prefix = "catalog"))
engine.addDialect(CompositionDialect("com.example.order.components", prefix = "order"))
```

**The one seam to address.** `CompositionDialectAutoConfiguration` currently contributes a single
`c` dialect guarded by `@ConditionalOnMissingBean`:

```kotlin
@Bean
@ConditionalOnMissingBean
fun compositionDialect(): CompositionDialect = CompositionDialect(/* … prefix = "c" */)
```

Because the condition keys on the `CompositionDialect` *type*, the default backs off the moment any
module declares its own `CompositionDialect` bean — so today you can't have the shared `c` default
*and* per-module beans at once. Making them coexist means narrowing that condition (e.g. back off
only on a bean named/qualified as the shared dialect, or make the shared default opt-in via a
property) so a shared `c` and N module dialects live side by side.

## Template location

With per-module dialects, each module owns its template root. Two layouts fit:

- **Package-derived co-location (recommended for a modulith).** Derive the template path from the
  component's package, e.g. `com.example.order.components.cart.Cart` →
  `com/example/order/components/cart/cart.html` on the classpath. Each module ships its own
  templates on its own classpath, with nothing central to configure. See
  [Component Co-location](component-co-location.md).
- **Per-module `componentsPath`.** Each module's dialect points at a sub-path under the shared
  templates root (`templates/order/cart.html`). Simpler, but centralizes templates under one tree,
  which is less modulith-shaped.

Either way, `buildComponentPath` currently makes a single resolution attempt with no fallback; a
"try the package-derived path, then fall back" scheme requires it to probe candidate paths (the same
change [Component Co-location](component-co-location.md) calls for). The existing per-class `path`
companion still works to organize templates into subdirectories *within* a module.

## Naming: prefix = module

The natural convention is that a module's prefix is its name — for a Spring Modulith app, the module
base package maps to the prefix (`order`, `catalog`, …). Shared, cross-cutting components stay under
`c`. So a call site reads as a mix of `<c:button>` (shared) and `<order:cart>` (module-local), and
the prefix tells you at a glance which module a component belongs to. Distinct module names give
distinct prefixes, and a modulith already enforces distinct module names.

## Cross-module use & encapsulation (convention)

Because all dialects share one engine, *any* template can reference *any* module's prefix — including
another module's internal components. That quietly punches through the module boundaries a modulith
exists to enforce.

The recommended discipline is a **convention**: distinguish public from internal components (e.g. by
package — `…components` public vs. `…components.internal` private) and treat reaching for another
module's internal component the same way you'd treat reaching for its internal types — as a smell.

Note plainly what Thymeleaf *cannot* do here: it resolves a tag by matching the element name against
the engine's global dialect set, with no notion of which template is calling, and templates are not
type-checked — so there is **no compile-time guarantee** the way an illegal Kotlin import would be
flagged. Spring Modulith's `verify()` analyzes *type* references and never sees a template tag, so
its guarantee for code does not extend to templates for free.

Enforcement is nonetheless *possible*, and left as a future refinement:

- **Build-time verification (≈ Modulith parity).** A `verify()`-style test that statically scans
  template sources for prefixed tags, maps each prefix and each template to its owning module, and
  fails the build on cross-module use of an internal component. The tags are literal text, so no
  rendering is needed.
- **Runtime guard (optional backstop).** The processor checks the calling template's module at
  render time and throws — catching what static scanning misses, but only when that path executes.

For now this design ships the convention only.

### Pros

- Collisions between modules are impossible by construction
- Zero changes to the rendering pipeline — purely a registration concern
- Idiomatic to Thymeleaf (prefixes are *the* namespacing mechanism) and to a modulith (each module
  owns its namespace in its own code)
- Modules are self-contained — own scan package, own templates
- A shared `c` design-system set coexists with module-local components

### Cons

- Consumers juggle multiple prefixes; a page composing across modules must know each one
- The single-`c` branding is diluted in a multi-module app
- The auto-config `@ConditionalOnMissingBean` backoff needs reworking so shared + module dialects coexist
- Encapsulation is convention-only — not enforced (see above)
- Each module must pick a prefix — naming discipline required

## See Also

- [Component Co-location](component-co-location.md) — package-derived template resolution, the
  natural per-module template layout
- [Developer Tooling](developer-tooling.md) — scaffolding could take a module/prefix argument to
  generate a component in the right module
- [Spring Bean Access](spring-bean-access.md) — relevant when modules expose services to their
  components
