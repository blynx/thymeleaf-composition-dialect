package blynx.thymeleaf.compositiondialect.testcomponents

import blynx.thymeleaf.compositiondialect.CompositionComponent
import blynx.thymeleaf.compositiondialect.CompositionComponentContext

class Heading(context: CompositionComponentContext) : CompositionComponent(context) {
    val level: Int = context.attributes["level"]?.toString()?.toIntOrNull()
        ?: (context.variable("parentMagicHeadings") as? MagicHeadings)?.level
        ?: 1
}
