# Developer Tooling: Scaffolding and Build Integration

## The Problem

Creating a new component requires several steps: a Kotlin class, an HTML template, optionally JS and CSS, all wired by naming convention. This is mechanical work that should be automatable. The Java/Kotlin ecosystem has no broadly adopted equivalent of `mix gen` or `rails generate` — this is a gap worth filling.

## Scaffolding

### What It Generates

A scaffold command creates the skeleton for a new component:

```bash
# generates Card.kt, card.html, card.js, card.css in the right place
generate-component Card com.example.components
```

Output:
```
src/main/kotlin/com/example/components/card/
  Card.kt        ← extends CompositionComponent, empty implementation
  card.html      ← minimal Thymeleaf template stub
  card.js        ← optional, empty or Stimulus controller stub
  card.css       ← optional, empty
```

The naming conventions (PascalCase → kebab-case) are already encoded in the dialect — the scaffolding tool reuses the same logic.

### Delivery Options

#### 1. Bundled in the Dialect JAR

A `main` function in the dialect JAR itself. Consumers invoke it directly:

```bash
java -cp ... blynx.thymeleaf.compositiondialect.cli.GenerateCLI Card com.example.components
```

**Pros:** zero extra dependencies, conventions always in sync with the dialect  
**Cons:** CLI code ships in the production JAR (harmless in practice — it's never called, but philosophically impure)

#### 2. Standalone CLI Artifact

A separate published artifact (`thymeleaf-composition-dialect-cli`) containing only the `main` class, depending on the dialect for its conventions. Consumers add it as `developmentOnly` in Spring Boot (excluded from the production fat JAR) or download and run it standalone.

**Pros:** production JAR stays clean, clear separation of concerns  
**Cons:** extra artifact to publish and maintain, consumers need to add a second dependency

#### 3. Gradle Plugin Task

The scaffolding logic lives inside the companion Gradle plugin (see below) as a Gradle task:

```bash
./gradlew generateComponent --name Card --package com.example.components
```

**Pros:** no extra artifact, integrates naturally with the build, handles `sourceSets` config too  
**Cons:** Gradle-only — Maven users get nothing

**Recommended starting point:** bundle in the dialect JAR. The code is small, the conventions are right there, and it avoids a separate artifact. Extract later if needed.

---

## Gradle Plugin

A companion Gradle plugin handles build-time integration that a library JAR cannot do on its own.

### Responsibilities

1. **`sourceSets` configuration** for co-location — adds `src/main/kotlin` as a resource source dir with HTML/JS/CSS includes, so consumers don't have to write this block themselves
2. **Scaffolding task** — `generateComponent` as described above
3. **Future: asset pipeline hooks** — wiring Vite builds into the Gradle lifecycle

### Consumer Usage

```kotlin
plugins {
    id("blynx.thymeleaf.composition") version "1.x"
}
```

That's it — `sourceSets` is configured, `generateComponent` task is available.

### Pros

- Zero boilerplate for co-location adopters
- Single entry point for all dialect-related build tooling
- Familiar pattern (Kotlin plugin, Spring Boot plugin work the same way)

### Cons

- Significant scope increase — Gradle plugin development, testing, and publishing to the Gradle Plugin Portal is non-trivial
- Gradle-only: Maven users must configure `sourceSets` equivalent manually

---

## Maven Plugin

A Maven Mojo equivalent of the Gradle plugin, providing the same scaffolding goal:

```bash
mvn composition:generate -Dname=Card -Dpackage=com.example.components
```

### Pros

- Reaches enterprise/Maven-first projects
- Same DX parity as the Gradle plugin

### Cons

- Entirely separate codebase (Maven plugin API vs Gradle plugin API)
- Two plugins to maintain, test, and publish
- Maven plugin development is more ceremonial than Gradle

**Recommendation:** skip the Maven plugin initially. If Maven demand materialises, a standalone CLI (see above) is a better second step — same UX for both ecosystems, one codebase.

---

## Summary: Recommended Phasing

| Phase | What | Why |
|-------|------|-----|
| Now | Bundle `generate` CLI in dialect JAR | Minimal effort, always in sync with conventions |
| Later | Gradle plugin | Removes `sourceSets` boilerplate for co-location adopters |
| If needed | Standalone CLI artifact | Clean separation, works with any build tool |
| Skip (for now) | Maven plugin | CLI covers Maven users well enough |
