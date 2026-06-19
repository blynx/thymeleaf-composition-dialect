# Setup

## Standalone

Create a package namespace for your component classes — it is supplied to the dialect so it can discover them automatically:

```kotlin
package com.example.demo.components
```

Add the dialect to Thymeleaf:

```kotlin
templateEngine.addDialect(CompositionDialect("com.example.demo.components"))
```

### `CompositionDialect` parameters

| Parameter | Required | Description |
|---|---|---|
| `componentPackage` | yes | Package to scan for component classes |
| `componentsPath` | no | Sub-path under the Thymeleaf templates root where component templates live |
| `prefix` | no | Tag prefix (default: `c`) |

## Spring Boot

Add the library to your dependencies. With Spring Boot auto-configuration, declaring the dialect as a bean is not required — set the package via `application.properties`:

```properties
thymeleaf.composition.component-package=com.example.demo.components
thymeleaf.composition.components-path=components
```

| Property | Required | Description |
|---|---|---|
| `thymeleaf.composition.component-package` | yes | Package to scan for component classes |
| `thymeleaf.composition.components-path` | no | Sub-path under templates root |
| `thymeleaf.composition.prefix` | no | Tag prefix (default: `c`) |

If you need to customise further, declare your own bean — auto-configuration backs off automatically:

```kotlin
@Configuration
class Config {
    @Bean
    fun compositionDialect() = CompositionDialect(
        componentPackage = "com.example.demo.components",
        componentsPath = "components"
    )
}
```
