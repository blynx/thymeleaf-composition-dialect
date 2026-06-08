package blynx.thymeleaf.compositiondialect.testcomponents

import blynx.thymeleaf.compositiondialect.CompositionComponent
import blynx.thymeleaf.compositiondialect.CompositionComponentContext

class MagicHeadings(context: CompositionComponentContext) : CompositionComponent(context) {
    val level: Int = ((context.variable("parentMagicHeadings") as? MagicHeadings)?.level ?: 0) + 1
    init { context.setVariable("parentMagicHeadings", this) }
}
