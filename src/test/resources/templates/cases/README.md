# Cases

One template per behaviour. Each file is meant to be readable on its own: what it demonstrates and what
should come out. `CasesTest` renders them and states the outcome — read the template first.

```
<!--
  CASE      what this demonstrates, and why it is not obvious
  EXPECT    what should come out
-->
<c:panel title="mine">body</c:panel>
```

Everything in the comment is prose for the reader; the comment itself is stripped from the output before
assertions run.

## Layout

| folder | what it pins down |
|---|---|
| `slots/` | default and named slots, nesting, and content handed on between components |
| `scope/` | what `${this}` refers to |
| `control-flow/` | `th:if` / `th:each` on a component tag |
| `context/` | values a component publishes for its content — the dynamic counterpart to `${this}` |
| `attributes/` | how attributes reach a component, and `c:rest` |
| `modules/` | components scanned from more than one source, as an imported component library is |

A case that needs a second template to exist — one to insert, say — keeps it beside itself as
`<the-case>.target.html`, so the pair is obvious.

## Expected output

A case may also keep its **rendered result** beside it as `<the-case>.expected.html` — the markup that comes
out, nothing else — so the whole story can be read without running anything. `CaseRenderer` compares against
it on every render, which is the point: documentation that cannot rot.

- Only the result goes in the file. No comments, no prose about it: the case's own `EXPECT` line is where
  the explaining happens.
- If a case renders nothing *without erroring*, the file is **there and empty**. An empty file states
  that; a missing one states nothing.
- A case that documents an **error** renders nothing at all and keeps no file. What it produces is a
  message, and `CasesTest` states which one via `CaseRenderer.failureOf`.
- Indentation and blank lines are the renderer's business rather than the spec's, so both sides are compared
  line by line with those removed. Indent the file for whoever reads it.
- `everyExpectedOutputMatchesItsCase` guards against one left behind by a rename.

## Components

The components the cases use live in `templates/casescomponents/`, with classes in
`src/test/java/blynx/thymeleaf/compositiondialect/casescomponents`. They are deliberately outside `cases/`,
so that everything under `cases/` is a case:

- `panel` (one default slot, stamps its own title) and `layout` (named slots: header, default, footer)
  cover nesting.
- `handoff` passes content on to another component.
- `outline` and `heading` publish and read a value.
- `relay` demonstrates `c:rest`.
- `inserter` pulls a plain Thymeleaf fragment (`inserter-body.html`, the only file in `casescomponents/`
  without a class) into its own template, for the insertion cases.

A second set stands in for an **imported component library**: `templates/libcomponents/`, with classes in
`src/java/…/libcomponents`. It is a separate `ComponentSource` with its own templates path, scanned into
the same dialect, and its classes carry the library's own name — `DsCard` for `<c:ds-card>` — which is all
that keeps its tags apart from the application's. Every case in the suite renders through that two-source
dialect, so the whole spec is also the evidence that a second source changes nothing about the first.
