package com.example.demo.components

import blynx.thymeleaf.compositiondialect.CompositionComponent
import blynx.thymeleaf.compositiondialect.CompositionComponentContext

class Badge(context: CompositionComponentContext) : CompositionComponent(context) {
    var type: String?

    init {
        this.type = context.attributes["type"].toString()
    }

    public fun classNames(): String {
        return "badge " + (if (this.type == "danger") "bg-danger" else "bg-secondary")
    }
}
