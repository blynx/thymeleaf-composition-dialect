# ADR minis — template-first inversion session

Brief decision + reason, no more. See `TEMPLATE-FIRST-COMPONENTS.md` for the full design doc these
implement.

## Design choices

### Implemented

- **Clean break for Stage 3, no back-compat shim for convention-matching.**
- **`<c:component>` is prefix-bound dialect grammar, exactly like `<c:slot>`.** A source's templates only
  ever register under the one prefix their own declaration spells.
- **`impl` + `props`/`<c:prop>` together is a hard error.** The class's own shape is authoritative for
  props; a template-side list next to `impl` couldn't mean anything else.
- **Classless `this` is a `Map`-backed `AnonymousComponent`.** Extending `AbstractMap` makes it both
  `instanceof CompositionComponent` (so `c:rest`/`hasSlot()` just work) and OGNL/SpEL-property-readable.
- **`AnonymousComponent`'s backing map is camel-keyed, not kebab.** The read site (`${this.autoHideSeconds}`)
  is always camel regardless of mode.
- **`impl` and `<c:prop type>` are resolved eagerly at scan time, not lazily at render.** A typo'd class or
  type fails at startup, not at first render.
- **`ComponentSource` gained `tagNamespace` as a new third field, not by reordering the 2-arg constructor.**
  Reordering would have silently changed the meaning of every existing 2-arg call site; namespace use goes
  through `ComponentSource.namespaced(...)` instead.
- **Library namespaces are enforced, not applied.** The library writes its own prefixed file names; the
  dialect only checks it did. Keeps "file name = tag name" always true and greppable.
- **Namespace uniqueness is checked before any enumeration, from the source list alone.** Cheap check,
  catches the mistake before doing any expensive walking.
- **`requireNoEmptySources()` runs automatically at `CompositionDialect` construction.** A misconfigured
  source looks identical to an empty one otherwise.
- **Classpath descriptor discovery is additive to `@Bean` sources; first claim of a
  `(templatesPath, namespace)` wins.** Explicit config should win over what was merely discovered.
- **`<c:component>`'s children must be self-closing `<c:prop>` declarations, never markup.** Enforced as a
  hard parse-time error rather than a lenient skip.
- **Dropped `c:` entirely as a prop channel; reserved for the dialect's own grammar only.** The
  typo-safety it bought wasn't worth an extra prefix to think about, given how permissive the surrounding
  ecosystem already is in the same spot.
- **Old class-scanning-era fixtures (abstract-base-skip, `@Composition` path/pathPrefix composition) were
  deleted, not adapted.** Those concepts don't exist under template-first discovery; adapting them would
  test nothing real.
- **All registry checks (`requireNoCollisions`, `requireNoReservedTagNames`, `requireNoEmptySources`) run
  automatically at construction.** Consistent "fail at boot" stance across the whole dialect.
- **`unresolvableTemplates`/`requireResolvableTemplates` kept their name and shape but changed meaning** —
  from "does the file exist" to "does the app's actual resolver chain agree with the classpath walk."
  Avoided API churn since the check's shape didn't actually change, only what counts as unresolvable.
- **Subdirectories stay cosmetic to the tag name; no auto-prefixing from the file path.** Preserves
  "file name = tag name, always"; an occasional collision is a one-line rename, not recurring friction.
- **Docs rewritten to be user-facing only, no implementation rationale, simple-to-complex.**
- **`components.md` restructured classless-first, building up to class-bound.** Mirrors the actual learning
  curve — you don't need Java at all for the simplest component.
- **Reflections replaced by ClassGraph for finding templates on the class path (`ClasspathTemplates`).**
  Reflections was unmaintained, brought four jars along for one resource enumeration, and could not see a
  component library nested inside a Boot fat jar — measured before the swap, not assumed. A JDK-only walk
  of `java.class.path` was written first and proved equivalent on every Boot shape (fat jar, extracted,
  manifest `Class-Path`), but `java.class.path` is the container's own bootstrap under a servlet container
  or application server, so it finds nothing there; unknown app servers were judged unlikely but plausible
  consumers. ClassGraph resolves roots from the class loader and carries handlers for those. Zero
  dependencies of its own, ~600 KB. Both implementations ran against the same fixtures before choosing.
- **`AnonymousComponent`'s constructor seeds `hasSlot`/`restAttributes` as synthetic map entries,
  computed eagerly.** Closes the bare-property-access bug (see Implementation details) without a custom
  OGNL/SpEL `PropertyAccessor`; a real declared prop of the same name still wins, merged in afterwards.

### Postponed

- **Per-component `prefix`/qualifier declared on `<c:component>`, overriding file-basename-as-tag-name.**
  Would let a component's exposed tag diverge from its file name — e.g. to resolve a subdirectory
  collision without renaming either file. The "avoid a scan" argument against applied prefixing doesn't
  actually hold here, since this implementation already does eager, registry-based tag lookup rather than
  lazy per-tag file derivation — an applied prefix costs nothing extra to resolve. But grep-ability and
  cross-reference consistency (a tag written in markup should visually match a file on disk) are
  independent of that and still argue against it. Revisit if a real case turns up where renaming the
  colliding file is actually the wrong lever, not just rare.

### Abandoned

*(none yet)*

## Implementation details

- **`ClasspathHelper.forClassLoader()` silently returns zero URLs on a modern JDK.** Fixed by scanning
  `ClasspathHelper.forJavaClassPath()` (the `java.class.path` system property) instead. *(Reflections has
  since been replaced by ClassGraph, which solves exactly this: per-loader handlers instead of a
  `URLClassLoader` assumption.)*
- **Reflections' `Scanners.Resources` indexes by bare file name, not full path.** Filtering by directory
  segment has to happen against the full resource path returned separately, not through the scanner's own
  pattern argument. *(Moot since Reflections was replaced.)*
- **A physical `templates/` prefix baked into test resource layout never needs to be known by
  `ComponentSource`.** Matching resource paths by suffix pattern (`.../templatesPath/.../basename.html`)
  transparently absorbs whatever prefix a `ClassLoaderTemplateResolver` happens to be configured with.
- **Stripping a standalone `<c:component>` marker leaves a stray leading-newline text node behind.** It
  propagates as a visible line break exactly where a caller's content gets spliced in once the component
  nests inside anything — fixed by swallowing one immediately-following whitespace-only text run.
- **AttoParser wraps whatever a handler throws in its own `ParseException`.** A deliberate
  `IllegalStateException` with a specific, useful message was getting silently replaced by a generic
  "could not read" message until its `getCause()` was unwrapped and rethrown directly.
- **`AbstractElementModelProcessor`'s constructor can only match one specific element name.** A wildcard
  "any tag under this prefix" processor needs `MatchingElementName.forAllElementsWithPrefix`, which isn't
  reachable through that base class — had to implement `IElementModelProcessor` directly and reimplement
  the ~25-line exception-decoration logic `AbstractElementModelProcessor.process()` would otherwise give
  for free.
- **OGNL resolves `Map`-backed property access natively; SpEL needs a `MapAccessor` explicitly
  registered.** Verified with dedicated spikes against both engines directly.
- **The render-time fragment parser only recognized the self-closing `<c:component>` form at first.** The
  paired form fell through as literal markup and tripped the "no longer declares `<c:component>`"
  consistency check — caught by a failing test, not by inspection.
- **`BigDecimal("12.50").toString()` keeps the trailing zero.** A test asserted `"12.5"` and failed on
  `"12.50"` — a test bug, not a coercion bug.
- **Walking a chain to the deepest `getCause()` isn't always the most useful exception.** `PropCoercion`
  wraps a plain `IllegalArgumentException` ("no conversion from String to X") in a more specific
  `TemplateProcessingException` that names the component and attribute — the root cause is the least
  informative frame in that particular chain.
- **OGNL and SpEL both resolve bare property syntax on a `Map` (`this.hasSlot`, no parens) by calling
  `get("hasSlot")` directly — never falling back to a same-named method the way they do on a normal
  object.** On a classless component, that made `${this.hasSlot}` and `${this.restAttributes}` silently
  resolve to `null` instead of calling the real interface default methods, even though the parenthesised
  form (`${this.hasSlot()}`) worked. Worse for `hasSlot`: no exception at all, just an always-false
  branch, since `null` reads as falsy in `th:if`. Found by asking to see concrete template examples of an
  earlier-flagged caveat, not by inspection.

## Context

Environmental/project-state facts that shaped decisions below — not choices made this session.

- **No component libraries exist outside this repo's own fixtures.** Nothing external depends on
  class-scanning or convention-matching behavior, which is what made a clean break costless.
- **This project already had an established fail-loud philosophy before this session** — unresolved tags,
  undeclared `c:rest`, reserved names were all hard errors already. Most of this session's stricter choices
  are that same stance applied to new surface area, not a new philosophy.
- **Vanilla Thymeleaf's own `th:` mechanism already tolerates a mistyped attribute name silently** — an
  unrecognized `th:foo` just creates a literal `foo` attribute, no error. The baseline this project's
  stricter choices sit on is itself permissive in exactly this way.
- **`thymeleaf-spring6` is not a dependency of this project.** Plain Thymeleaf itself is `compileOnly`, so
  its Spring integration is never pulled in either — shaped how the OGNL/SpEL spike had to be tested (two
  separate harnesses, not one integration test).
- **Kebab-in-HTML/camel-in-Java prop naming, and slot markers being prefix-bound, were both already
  decided before this session.** This session extended those existing conventions to new surface
  (classless props, `<c:component>`) rather than inventing new ones.
- **The user added `.claude/rules/docs.md` mid-session** — a new standing convention that docs are for the
  library user only, no implementation rationale, simple-to-complex ordering.
- **Modern JDKs, and Gradle's own test worker, don't guarantee the application classloader is a
  `URLClassLoader`.** An environment fact independent of anything this project did, which several
  classpath-scanning APIs still quietly assume.
