# Setup

## Standalone

Put your components in one package. Give this package to the dialect. The dialect scans it and finds your components:

```java
package com.example.demo.components;
```

Add the dialect to Thymeleaf:

```java
templateEngine.addDialect(new CompositionDialect("com.example.demo.components"));
```

### `CompositionDialect` parameters

| Parameter | Required | Description |
|---|---|---|
| `componentPackage` | yes | Package to scan for components |
| `componentsPath` | no | Sub-path under the Thymeleaf templates root where component templates live |
| `prefix` | no | Tag prefix (default: `c`) |

## Spring Boot

Add the library to your dependencies. With Spring Boot auto-configuration, you do not need to declare the dialect as a bean. Set the package in `application.properties` instead:

```properties
thymeleaf.composition.component-package=com.example.demo.components
thymeleaf.composition.components-path=components
```

| Property | Required | Description |
|---|---|---|
| `thymeleaf.composition.component-package` | yes | Package to scan for components |
| `thymeleaf.composition.components-path` | no | Sub-path under the templates root |
| `thymeleaf.composition.prefix` | no | Tag prefix (default: `c`) |

For more control, declare your own bean. Auto-configuration backs off automatically when you do:

```java
@Configuration
class Config {
    @Bean
    CompositionDialect compositionDialect() {
        return new CompositionDialect("com.example.demo.components", "components");
    }
}
```
