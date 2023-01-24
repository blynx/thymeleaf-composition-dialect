![AI generated illustration "Space and Thyme"](docs/space-and-thyme.jpg "Space And Thyme")

_"An ikebana composition of just space and thyme"_ ([midjourney](https://www.midjourney.com/))

# Thymeleaf Composition Dialect

A flavour of ui component templating in Thymeleaf.

⚠️ Still drafting, Some things may change ⚠️

```html
<c:card>
	<c:slot name="header">
		<h2>Thymeleaf Composition Dialect</h2>
	</c:slot>
	<p>
		Compose your layouts with "components"
	</p>
	<h3>Features</h3>
	<ul>
		<li>Shorter, more readable syntax</li>
		<li>Component classes & instances</li>
		<li>Slots</li>
	</ul>
	<c:slot name="footer">
		<c:link type="with-glitter">show me</c:link>
	</c:slot>
</c:card>
```

## Setup

You will need to create a package namespace for your component classes. 
It will be supplied to the new Dialect as seen later and used to find all your components.
For example:

```java
package com.example.demo.components;
```

How to add the dialect to Thymeleaf directly:

```java
TemplateEngine templateEngine = new TemplateEngine();
templateEngine.addDialect(new CompositionDialect("com.example.demo.components", ...));
```

### Parameters of CompositionDialect

- 1 _componentPackage_: Package namespace of your component classes `"com.example.demo.components"`
- 2 _componentPath_ (optional): A sub path to the thymeleaf templates path where your components are located. `"path/to/components"`

### Spring Boot Config

Java and Kotlin configurations for Spring Boot could look like this:

```java
package com.example.demojava.config;

import blynx.thymeleaf.compositiondialect.CompositionDialect;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Config {

    @Bean
    public CompositionDialect CompositionDialect() {
        return new CompositionDialect("com.example.demojava.components", "components");
    }

}
```
```kotlin
package com.example.demo.config

import blynx.thymeleaf.compositiondialect.CompositionDialect
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class Config {

    @Bean
    fun compositionDialect(): CompositionDialect {
        return CompositionDialect(
            componentPackage = "com.example.demo.components",
            componentsPath = "components"
        )
    }
}
```

## Usage & Features

### Component Creation, Classes & Instances

For your component templates to be callable like `<c:button />` you have to create a corresponding class `Button` which extends `blynx.thymeleaf.compositiondialect.CompositionComponent`.
A class like `OtherButton` will translate to `<c:otherbutton />`.

See the examples below for how such a class could look like. Scroll further down for some more explanations.   

```java
package com.example.demojava.components;
        
import blynx.thymeleaf.compositiondialect.CompositionComponent;
import blynx.thymeleaf.compositiondialect.CompositionComponentContext;

public class Button extends CompositionComponent {
    // reserved field for dialect, configurable sub path for component 
    public static String path = "forms";
    
    public String type = "default";
    
    public Button(CompositionComponentContext context) {
        super(context);
        this.type = context.getAttributes().get("type").toString();
    }

    // methods will be available in component template
    public String classNames() {
        return "btn " + ((this.type == "danger") ? "btn-danger" : "btn-secondary");
    }
}
```
```kotlin
package com.example.demo.components

import blynx.thymeleaf.compositiondialect.CompositionComponent;
import blynx.thymeleaf.compositiondialect.CompositionComponentContext;

public class Button(ctx: CompositionComponentContext) : CompositionComponent(ctx) {
    // reserved field for dialect, configurable sub path for component
    val path: String = "forms"
    
    var type: String?

    init {
        this.type = context.attributes["type"].toString()
    }

    // methods will be available in component template
    public fun classNames(): String {
        return "btn " + (if (this.type == "danger") "btn-danger" else "btn-secondary")
    }
}
```

With the static field `path` it is possible to further structure your components.   

```
resources
 └ templates        -- configured Thymeleaf templates folder
    └ components    -- configured CompositionDialect path 
       └ forms      -- path on a component class
```

#### Defining Component Templates

```html
<!-- templates/components/forms/button.html -->
<button th:fragment="button" th:class="${button.classNames()}">
    <c:slot />
</button>
```

This fragment/component can now be rendered as `<c:button type="danger" />`

- The thymeleaf fragment attribute `th:fragment="button"` must be provided.
- The instance will be available by the simplified class name: `${button}`.
- `<c:slot />` is the standard slot where the tag contents will be placed when calling that component.

#### And Slots...

Slots can also have names:

```html
<!-- .../card.html -->
<div th:fragment="card" class="card">
    <header class="card-header" th:if="${card.hasSlot('header')}">
        <c:slot c:name="header" />
    </header>
    <div class="card-content">
        <c:slot />
    </div>
    <footer class="card-footer" th:if="${card.hasSlot('footer')}">
        <c:slot c:name="footer" />
    </footer>
</div>
```

The method `hasSlot()` is available to component instances. This is useful to conditionally render blocks of markup related to that slot.
Note that slots need to be accessed on the first level as below, but can be defined anywhere deep down in the template markup. 

```html
<!-- Calling card component with named slots -->
<c:card>
    <c:slot name="header">
        <h2>Thymeleaf Composition Dialect</h2>
    </c:slot>
    <p>
        Compose your layouts with "components"
    </p>
    <h3>Features</h3>
    <ul>
        <li>Shorter, more readable syntax</li>
        <li>Component classes & instances</li>
        <li>Slots</li>
    </ul>
    <c:slot name="footer">
        <c:link type="with-glitter">show me</c:link>
    </c:slot>
</c:card>
```

## Credits

Thanks [@tillsc](https://github.com/tillsc/) for adding the slots and instances :)
