package blynx.thymeleaf.compositiondialect;

import org.thymeleaf.context.ITemplateContext;
import org.thymeleaf.exceptions.TemplateProcessingException;
import org.thymeleaf.model.IProcessableElementTag;
import org.thymeleaf.processor.element.AbstractElementTagProcessor;
import org.thymeleaf.processor.element.IElementTagStructureHandler;
import org.thymeleaf.templatemode.TemplateMode;

/**
 * Catches every {@code c:slot} that {@link CompositionElementModelProcessor#getOrLoadFragment} did not
 * already recognize and consume as a marker. A recognized marker is stripped out while a component's
 * fragment is loaded, before either the caller's content or the component's own markup is ever handed back
 * to the engine for normal processing — so whatever reaches this processor is, by definition, misspelled,
 * misplaced (written outside any component template), or otherwise not a marker this dialect understands.
 *
 * <p>Registered for both {@link TemplateMode#HTML} and {@link TemplateMode#XML}: every other processor in
 * this dialect is HTML-only, which would otherwise let a stray {@code c:slot} through silently in an
 * XML-mode template.
 */
class CompositionSlotPlacementProcessor extends AbstractElementTagProcessor {

    private static final int PRECEDENCE = 100;
    private static final String ELEMENT_NAME = "slot";

    CompositionSlotPlacementProcessor(TemplateMode templateMode, String dialectPrefix) {
        super(templateMode, dialectPrefix, ELEMENT_NAME, true, null, false, PRECEDENCE);
    }

    @Override
    protected void doProcess(ITemplateContext context, IProcessableElementTag tag,
                             IElementTagStructureHandler structureHandler) {
        throw new TemplateProcessingException(CompositionDialect.DIALECT_NAME
                + ": <" + getDialectPrefix() + ":" + ELEMENT_NAME + "> was not recognized as a slot marker. "
                + "It only means something written directly inside a component's own template — check that "
                + "it is spelled correctly, is not itself nested inside another slot's fallback content, and "
                + "is not appearing in a caller template instead of a component template.");
    }
}
