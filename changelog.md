# Changelog

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/). This library is pre-1.0 and
still drafting, so breaking changes appear in minor versions.

## 0.19.0

### Changed — breaking

- A slot marker's name is now the plain `name` attribute rather than `c:name`:
  `<c:slot name="header" />`. It matches the native `<slot name="…">` and `<c:prop name="…">`, which was
  already unprefixed. `c:` stays reserved for the attributes that ride on your own elements — `c:slot` at a
  call site, and `c:rest`. A marker still spelling `c:name` fails with a message telling you to drop the
  prefix, so the migration is a search and replace over your component templates.
- `name` is now the only attribute a `<c:slot />` may carry. Anything else on it is an error naming the
  attribute and the component's template. It used to be accepted and then dropped without a trace, because
  a marker's own tag never reaches the rendered output — `th:if` on a marker in particular looked as if it
  gated the slot while the content spliced in unconditionally, rendering the caller's content twice in the
  usual one-branch-per-variant shape. Put control flow on the element around the marker, or on a
  `<th:block>` where there is no element to carry it. See
  [Slots](docs/reference/slots.md#what-a-marker-may-carry).

### Fixed

- Spring Boot 4 no longer logs a `TypeNotPresentException` for this library's auto-configuration on every
  startup. Boot 4 moved Thymeleaf's own auto-configuration to a different package; the ordering against it
  is now declared by name for both the Boot 3 and Boot 4 locations, so whichever is present is used and the
  ordering is no longer silently dropped.

### Added

- [Setup](docs/reference/setup.md#testing) documents testing. A test slice such as `@WebMvcTest` does not
  apply this library's auto-configuration, and with no dialect registered a `<c:…>` tag is not an error — it
  renders as literal text, so a test asserting only the status code passes while the page is broken.

## 0.18.0

### Added

- A caller's `th:classappend`/`th:styleappend` on a component tag now merge onto the element carrying
  `c:rest`, alongside that element's own `class`/`style` in the component's template, instead of being
  rejected. A component that declares `class` as a prop keeps the caller's plain `class` out of the merge.
- Passing `th:classappend`/`th:styleappend` to a component whose template has no `c:rest` anywhere is an
  error naming the attribute and the template, rather than silently doing nothing.

### Changed

- `th:attrappend`/`th:attrprepend` on a component tag are rejected. Unlike
  `th:classappend`/`th:styleappend`, they name no single target attribute to merge onto. They remain legal
  inside a component's own template.
