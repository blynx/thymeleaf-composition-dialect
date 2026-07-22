# Ideas

Design explorations for features not yet built — roadmap thinking, not commitments. Listed in
suggested priority order; each links to its write-up. Raw, un-triaged notes live in
[_notes.md](_notes.md).

## How this is prioritized

1. **Current need** — what's wanted now (Module Organization).
2. **Foundations first** — ideas that unblock or set conventions for others rank ahead of the ideas
   that depend on them.
3. **Value-to-effort** — cheap, high-leverage improvements to everyday component authoring rank high.
4. **Dependencies** — an idea sits below anything it requires.
5. **Coupling / risk** — heavier or more Spring-coupled ideas come once the conventions they build on
   are stable.

## Foundations (do first)

- **[Component Registry](component-registry.md)** — A small, behavior-preserving refactor that lifts
  component discovery out of `CompositionDialect.getProcessors()` into a queryable list. Not a feature,
  but the shared substrate under several ideas: it's what Module Organization's collision detection
  reads, what the Playground enumerates, and where Developer Tooling's scaffolding gets its
  discovery/naming. Low-risk and high-leverage — worth doing before idea #1.

## Priority order

1. **[Module Organization](module-organization.md)** — _Now._ Fixes a correctness gap (same-named
   components in different modules silently collide) and unblocks modulith adoption via one dialect
   (prefix) per module. Mostly a registration/config change, no rendering-code change.

2. **[Component Co-location](component-co-location.md)** — The physical-layout foundation for much of
   the rest: it defines where a component's files live, is the recommended template layout for Module
   Organization (package-derived paths), and is a hard prerequisite for Component-Scoped Assets. A
   contained change (`sourceSets` + path-probing in `buildComponentPath`).

3. **[Class Merging](class-merging.md)** — Best value-to-effort for day-to-day authoring; utility-sized
   (`mergeClasses` / `twMerge`). Lets callers extend or override a component's own CSS classes — table
   stakes for Tailwind / utility-CSS UIs. Ships standalone (expose `${this.classes}`); `tailwind-merge`
   can be an optional dependency.

4. **[Component Lifecycle Hooks](component-lifecycle.md)** — A small primitive (`rootAttributes()`) that
   multiplies value: delivers merged classes onto the root element without template boilerplate and
   unlocks automatic Stimulus / Alpine / ARIA / test-id wiring. Opt-in via `open fun`, zero cost when
   unused. Amplifies #3 and sets up #7.

5. **[Spring Bean Access](spring-bean-access.md)** — Unlocks self-fetching widget components
   (`<c:cart-summary />` fetching its own data). Clean, purely additive, and keeps Spring optional via a
   `BeanProvider` abstraction. A capability more than a daily need, and mildly contentious
   architecturally (UI touching services) — hence mid.

6. **[Component Playground](component-playground.md)** — A dev-time, server-side view to browse and
   render components in isolation — "Storybook's value without its tech." Rides on the Component
   Registry (discovery) and the engine's string-template rendering, so the infrastructure is small and
   near-zero-config. Primary interface is **stories written in JVM code** (typed, high-fidelity, and
   they double as rendering test fixtures), with query-param overrides for live tweaking. Depends on
   the Component Registry; its dev-only story packaging leans on Developer Tooling (#8).

7. **[Component-Scoped Assets](component-scoped-assets.md)** — High payoff for a large component library
   but the heaviest idea (request-scoped collector, Vite/manifest, `<c:assets />` placement / two-pass
   concerns) and the most Spring-coupled. Depends on Co-location (#2) and pairs with Lifecycle (#4). Start
   with the simple "emit all component assets" mode the doc recommends.

8. **[Developer Tooling](developer-tooling.md)** — Scaffolding CLI + Gradle/Maven plugins. A real DX win
   but a scope increase (separate artifacts, plugin publishing), and best done *after* the conventions it
   scaffolds (co-location layout, module/prefix naming) are stable — otherwise it scaffolds a moving
   target. Its own phasing agrees: a small `generate` CLI first, plugin later.

## Dependencies at a glance

- **Component Registry → (unblocks) Module Organization, Component Playground, Developer Tooling** —
  the shared discovery substrate; do first.
- **Component-Scoped Assets → Component Co-location** (co-location is a prerequisite for per-component JS/CSS).
- **Module Organization → Component Co-location** (recommended template location reuses package-derived paths).
- **Class Merging ↔ Component Lifecycle Hooks** (lifecycle delivers merged classes to the root; merging is
  the lifecycle hook's primary motivating case).
- **Component-Scoped Assets → Component Lifecycle Hooks** (auto-emitting `data-controller` for Stimulus).
- **Component Playground → Component Registry** (enumerates it), **+ soft: Developer Tooling** (dev
  source-set packaging for stories). Playground stories also double as rendering test fixtures.
- **Developer Tooling → Component Co-location + Module Organization** (scaffolds those conventions; do once stable).
