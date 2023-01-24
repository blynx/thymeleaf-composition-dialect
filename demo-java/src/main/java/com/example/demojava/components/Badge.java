package com.example.demojava.components;

import blynx.thymeleaf.compositiondialect.CompositionComponent;
import blynx.thymeleaf.compositiondialect.CompositionComponentContext;

public class Badge extends CompositionComponent {

    public String type;

    public Badge(CompositionComponentContext context) {
        super(context);
        this.type = context.getAttributes().get("type").toString();
    }

    public String classNames() {
        return "badge " + ((this.type == "danger") ? "bg-danger" : "bg-secondary");
    }
}
