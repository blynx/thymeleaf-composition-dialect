# Component-Scoped Assets

## The Idea

Each component can optionally ship its own JS and CSS, co-located with its template. The dialect collects which components are actually rendered on a given request and emits only the assets those components need — no global bundle required.

```html
<head>
  <c:assets />   ← outputs <script> and <link> tags for rendered components only
</head>
```

## How It Works

1. **Co-location convention** — a component's assets live alongside its template, identified by name:
   ```
   card/
     card.html
     card.js
     card.css
   ```
   See [Component Co-location](component-co-location.md) for the full co-location story.

2. **Render-time collection** — as the dialect processes `<c:card>`, `<c:nav-bar>` etc., it records which components were used into a request-scoped collector (a Spring-managed bean).

3. **Asset emission** — a `<c:assets />` tag (placed in `<head>`) flushes `<script>` and `<link>` tags for the collected set, deduplicated.

## JS Behaviour Approaches

Component-scoped assets are agnostic of how JS behaviour is structured. Common approaches are complementary, not mutually exclusive:

**Stimulus** — wires behaviour via `data-controller` attributes using the same kebab-case naming the dialect already applies (`Card` → `card`, `NavBar` → `nav-bar`). A co-located `card_controller.js` is the natural asset name. See [Component Lifecycle Hooks](component-lifecycle.md) for auto-emitting `data-controller` without template boilerplate.

**Alpine.js** — inline reactive behaviour via `x-data` and `x-*` attributes. The JS is typically imported globally rather than per-component, but per-component CSS still benefits from scoped collection.

**HTMX** — hypermedia attributes (`hx-get`, `hx-target` etc.) on elements. Like Alpine, JS is usually global; the asset collection story here is mostly CSS.

**Vanilla / custom** — any module-based JS that a component needs exclusively.

## Vite Integration

In production, asset URLs are fingerprinted (`card.abc123.js`). Vite produces a `manifest.json` mapping source paths to output paths. The dialect's asset tag can read this manifest at startup to resolve correct URLs:

- **Development**: point directly to source files (or Vite dev server URLs)
- **Production**: resolve via manifest to fingerprinted paths

## Global Bundle vs Scoped Collection

| | Global bundle | Scoped collection |
|---|---|---|
| Tooling complexity | Low | Higher (collector bean, asset tag) |
| Payload | All components, every page | Only components used on the page |
| Cache behaviour | Single long-lived bundle | Per-component files, fine-grained caching |
| Best for | Small projects, few components | Large component libraries |

### Pros

- Only ships assets for components actually used on the page
- Strong locality: JS, CSS, and HTML for a component live together
- Fine-grained browser caching per component

### Cons

- Per-page optimisation requires a request-scoped collector bean — ties the dialect more tightly to Spring
- Vite (or equivalent) needed for bundling and fingerprinting — adds frontend tooling to a JVM project

## `<c:assets />` Placement

Two modes with different constraints:

**Emit all component assets** — the dialect already scans all component classes at startup and can discover co-located assets at the same time. `<c:assets />` reads from that static set and can sit anywhere in the template, including `<head>`. No per-request tracking needed.

**Emit only assets for components used on this page** — requires knowing what was rendered before the tag is processed. Options:
- Place `<c:assets />` at the end of `<body>` (after all component usage)
- Response filter: `<c:assets />` emits a placeholder token; a servlet filter buffers the response and substitutes the real tags after rendering completes
- Two-pass rendering: first pass collects used components, second renders with that knowledge — Thymeleaf has no native support for this

For most projects, emitting all component assets is the right default. The total size is small and it eliminates the placement constraint entirely. Per-page optimisation is only worth the complexity for large component libraries.

## Open Questions

- Should `<c:assets />` support separate `<c:scripts />` and `<c:styles />` tags for placement flexibility (`<link>` in `<head>`, `<script>` at end of `<body>`)?
- How to handle shared dependencies between components (e.g., two components both need a utility library)?
- Should asset collection be opt-in per component, or automatic for any component that has a co-located `.js`/`.css`?
