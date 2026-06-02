package blynx.thymeleaf.compositiondialect.testcomponents

import blynx.thymeleaf.compositiondialect.CompositionComponent
import blynx.thymeleaf.compositiondialect.CompositionComponentContext

class Label(context: CompositionComponentContext) : CompositionComponent(context) {
    val text: String? = context.attributes["text"]?.toString()
    val value: Any? = context.attributes["value"]
}
