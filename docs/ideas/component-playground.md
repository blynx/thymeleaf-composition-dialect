# Component Playground

A dev-time, server-side view to browse components and render them in isolation — Storybook's *value*
(browse, isolate, tweak) without Storybook's *tech* (no Node, no bundler, no story DSL, no MDX). The
components are discovered automatically; the "stories" are written in JVM code.

## The Idea

```
GET /playground                 → list every component, grouped by module/prefix
GET /playground/{prefix}/{tag}  → render one component (or one of its stories) in isolation
```

It's cheap because the two hard parts already exist:

1. **Discovery is done** — see [Component Registry](component-registry.md). The playground just
   enumerates the registry.
2. **String rendering is proven** — the test suite already renders a component from a raw string:
   `engine.process("<c:plain />", Context())`. That's exactly the mechanism the playground needs —
   build a tiny template containing the component tag and process it through the app's engine. (It
   needs a `StringTemplateResolver` present, which the playground's own dev-only config contributes.)

The whole thing is a Spring controller plus a couple of Thymeleaf templates. Ships behind a dev-only
flag — never in production (see Caveats).

## Stories (the primary interface)

Rather than driving everything through query strings (which can only carry string attributes and a
default slot), define **stories in JVM code**. A story is a small template snippet plus a typed
context — which gives full fidelity: real markup, named slots, and real objects.

```kotlin
class CardStories : ComponentStories {

    // simple: string attrs + default slot
    val primary = story("""
        <c:card variant="primary">
            <h2 c:slot="header">Welcome</h2>
            <p>Compose your layouts with components.</p>
        </c:card>
    """)

    // complex: a real domain object handed to the component
    val recentOrder = story(
        template = """<c:order-summary c:order="${'$'}{order}" />""",
        vars = mapOf("order" to sampleOrder(total = 42.00, items = 3)),
    )
}
```

Stories are discovered the same way components are — a `Reflections` scan for a `ComponentStories`
type (or `@Story` annotation) — so the infrastructure stays zero-config; the stories themselves are
content you opt to write. A builder can generate the snippet for trivial cases
(`story("card") { attr("variant", "danger"); slot("Hello") }`).

**How typed/complex args reach the component** — no new rendering code. `extractAttrs` in
`CompositionElementModelProcessor` evaluates `c:`-prefixed attributes as Thymeleaf expressions against
the context. So the playground seeds a story's `vars` into the `Context` and the snippet references
them with `c:order="${order}"` → the component's `attributes["order"]` receives the real object. That
bridges typed Kotlin land to the component.

**Bonus — stories double as test fixtures.** `CardStories.primary` is exactly the input you'd feed a
snapshot/approval rendering test. The same definitions power the playground *and* your tests — one
artifact, two payoffs. This is the main reason to prefer stories over ephemeral query params.

## Query-param overrides (secondary)

On top of a story, query params give a live-tweak layer for simple string attributes without a
recompile: `GET /playground/c/card?variant=danger`. Two limits, from how attributes work today:

- **Plain vs. expression attributes** — query params map cleanly to plain string attrs (the common
  case). Objects/lists/booleans-as-expressions don't fit a query string; that's what stories are for.
- **Slots** — a `?slot=...` param covers the default slot; named slots don't fit query params and
  belong in a story.

## The frame

- **Left:** component list from the registry.
- **Center:** an `<iframe src="/playground/c/card?variant=danger">` — iframe isolates the component's
  own CSS/markup from the playground chrome, and can pull in the app's stylesheet so it looks real.
- **Right:** a plain HTML form (attribute rows + slot textarea) that rewrites the iframe `src` on
  change — vanilla JS, or one `hx-get` with htmx. No build step.

## Dynamic-in-dev

Editing a story is a recompile, not an instant edit — but with Spring Boot DevTools that's a
~1–2s automatic restart, and the query-param override layer covers live nudging of string attrs
without touching code. Story for the canonical, typed scenario; query params for the live tweak.

## Caveats

- **Dev-only / security** — an endpoint that renders arbitrary components with arbitrary attributes
  must never run in production (info disclosure; expression-valued attrs are effectively template
  evaluation). Guard with `@ConditionalOnProperty` / `@Profile` and exclude from the prod build.
- **Spring-web-only** — the controller approach assumes a Spring MVC app; standalone users would wire
  their own route.
- **Data-dependent components** — anything that reads model attributes or fetches from Spring services
  (see [Spring Bean Access](spring-bean-access.md)) may render empty or error in isolation. Seeding
  context vars helps; arbitrary data deps remain a limit.
- **Stories are dev artifacts** — they should not ship in the production jar. Cleanest is a dedicated
  dev source set (or separate dev module) on the dev runtime classpath but excluded from prod — which
  lands in [Developer Tooling](developer-tooling.md) / Gradle-plugin territory.

### Pros

- Rides on discovery + engine you already have — small, near-zero-config infrastructure
- Server-side, zero frontend build — the opposite of Storybook's stack
- Stories are typed, high-fidelity, and double as rendering test fixtures
- Auto-discovers components across every module/prefix via the registry

### Cons

- Stories require a small API and dev-only packaging (dev source set)
- Full typed-control generation and named-slot editing need stories, not query params
- Dev/Spring-web bound; not usable for data-heavy page components without seeding

## See Also

- [Component Registry](component-registry.md) — the discovery this enumerates (do first)
- [Developer Tooling](developer-tooling.md) — the dev source-set packaging for stories
- [Component-Scoped Assets](component-scoped-assets.md) — pulling a component's own CSS/JS for a
  realistic isolated render
- [Module Organization](module-organization.md) — enumerating components across all module prefixes
