# Component Co-location

## The Idea

Instead of splitting component files across the conventional JVM source tree, keep everything that belongs to a component in one place:

```
src/main/kotlin/com/example/components/card/
  Card.kt        ← Kotlin component class
  card.html      ← Thymeleaf template
  card.js        ← JS behaviour (optional)
  card.css       ← Styles (optional)
```

Compared to the conventional split:

```
src/main/kotlin/com/example/components/Card.kt
src/main/resources/templates/components/card.html
src/main/resources/static/components/card.js
src/main/resources/static/components/card.css
```

## Motivation

The JVM convention of separating sources and resources is a build artifact, not a principled design decision. Modern component-based frontend frameworks (Vue SFCs, Svelte, Astro) have demonstrated that co-location significantly improves developer experience:

- Everything about a component is in one directory — easy to find, rename, or delete
- Deleting a component is atomic: remove one folder
- The relationship between class, template, and behaviour is visually obvious

## Template Resolution

The dialect already knows the component class. From the class it can derive the template path without any additional configuration:

`com.example.components.card.Card` → `com/example/components/card/card.html` on the classpath

The dialect can support both styles transparently by checking the package-derived path first and falling back to the configured `componentsPath`. No flag needed — both layouts just work.

This also makes `componentsPath` config largely obsolete for co-located projects.

## Gradle Configuration

Non-Kotlin files in `src/main/kotlin` are ignored by Gradle by default. One additional block is needed in `build.gradle.kts`:

```kotlin
sourceSets.main {
    resources {
        srcDirs("src/main/kotlin")
        include("**/*.html", "**/*.js", "**/*.css")
    }
}
```

This copies the non-Kotlin files into the classpath output alongside the compiled classes.

### Pros

- True component cohesion — one folder per component
- Matches the mental model of modern component frameworks
- No cross-tree navigation when working on a component
- Atomic refactoring and deletion
- `componentsPath` config becomes unnecessary

### Cons

- Breaks JVM convention — surprises developers who expect resources in `src/main/resources`
- Requires an extra Gradle `sourceSets` block (can be handled by a companion Gradle plugin)
- IDEs may need configuration to treat HTML in the Kotlin source tree correctly (IntelliJ handles it well in practice)
- Not compatible with Maven's default layout without equivalent configuration

## Opt-in, Not Required

Co-location should be optional. The dialect supports both layouts by probing paths at template resolution time. Developers can choose their preferred style; the dialect adapts.

## See Also

- [Component-Scoped Assets](component-scoped-assets.md) — co-location is a prerequisite for per-component JS/CSS collection
- [Developer Tooling](developer-tooling.md) — the Gradle plugin handles the `sourceSets` configuration automatically
