package com.example.demojava.components;

import blynx.thymeleaf.compositiondialect.CompositionComponent;
import blynx.thymeleaf.compositiondialect.CompositionComponentContext;

public class Badge extends CompositionComponent {

    public String type;

    public Badge(CompositionComponentContext context) {
        super(context);
        Object typeAttr = context.getAttributes().get("type");
        this.type = typeAttr != null ? typeAttr.toString() : null;
    }

    public String classNames() {
        return "badge " + ("danger".equals(this.type) ? "bg-danger" : "bg-secondary");
    }
}
