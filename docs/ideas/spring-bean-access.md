# Spring Bean Access

## The Idea

Allow component classes to fetch Spring beans directly via the component context:

```kotlin
class RecentOrders(ctx: CompositionComponentContext) : CompositionComponent(ctx) {
    val orders = ctx.getBean(OrderService::class.java).findRecent()
}
```

```html
<c:recent-orders />
```

No controller involvement. The component is responsible for its own data.

## What It Enables

**Self-fetching components** — the primary use case. Peripheral or widget-style components (`<c:cart-summary />`, `<c:notifications />`, `<c:weather-widget />`) fetch their own data rather than requiring every controller in the app to remember to populate the model.

**Feature flags** — component checks a toggle service to decide whether or how to render.

**Security** — component queries the security context to conditionally show content based on roles or permissions.

**Configuration** — read `Environment` or `@ConfigurationProperties` to adapt rendering based on app config.

**Caching** — back expensive component computations with a `CacheManager`.

## Why `getBean()` Over Full Context Access

The full Thymeleaf `ITemplateContext` can indirectly reach much of the Spring container. Explicit `getBean()` on `CompositionComponentContext` is deliberately narrower — the dependency is declared at the call site, visible and intentional, rather than accessed implicitly through internals.

## On Architecture

`getBean(OrderRepository::class.java)` looks like a coupling smell — UI component directly touching the persistence layer. But the alternative is a controller silently populating a model attribute named `"orders"` that the component depends on implicitly. The `getBean()` call is at least honest about the dependency.

The dialect does not enforce architectural boundaries. Whether a component fetches its own data or receives it from a controller is a choice left to the team. Self-fetching components make sense for peripheral widgets; for primary page content, keeping the controller in charge is usually cleaner.

## Implementation

Purely additive — threaded through the existing chain as an optional parameter at each level.

```
CompositionDialectAutoConfiguration  ← wires Spring ApplicationContext → BeanProvider
  → CompositionDialect               ← accepts BeanProvider? = null
    → CompositionElementModelProcessor
      → CompositionComponentContext  ← exposes getBean()
```

Rather than coupling `CompositionComponentContext` to `ApplicationContext` directly, a thin abstraction keeps the core dialect decoupled from Spring at the type level:

```kotlin
fun interface BeanProvider {
    fun <T> getBean(type: Class<T>): T
}
```

`CompositionComponentContext` gains:

```kotlin
fun <T> getBean(type: Class<T>): T =
    beanProvider?.getBean(type)
        ?: error("No BeanProvider available — are you running outside Spring?")
```

- **In Spring**: `CompositionDialectAutoConfiguration` wires `ApplicationContext` as the `BeanProvider` automatically
- **Outside Spring**: user can supply their own implementation (Guice, Micronaut, a plain map, whatever)
- **Not provided**: `getBean()` throws a clear error at call time, not at startup

This keeps `spring-context` out of the core dialect's compile-time dependencies — Spring wiring stays in the auto-configuration module only. Components that never call `getBean()` pay no cost.
