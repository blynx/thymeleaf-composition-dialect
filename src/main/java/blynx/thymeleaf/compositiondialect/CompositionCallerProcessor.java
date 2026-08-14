package blynx.thymeleaf.compositiondialect;

import org.thymeleaf.context.ITemplateContext;
import org.thymeleaf.model.IProcessableElementTag;
import org.thymeleaf.processor.element.AbstractElementTagProcessor;
import org.thymeleaf.processor.element.IElementTagStructureHandler;
import org.thymeleaf.templatemode.TemplateMode;

/**
 * Internal marker wrapped around slot content by {@link CompositionElementModelProcessor}. Nobody authors
 * this tag; it exists because slot content is spliced <em>into</em> the subtree where the receiving
 * component published itself as {@code this}, and content the caller wrote must not read the component it
 * was handed to.
 *
 * <p>It steps one frame out — restoring the {@code this} that was in effect where the content was
 * written — and removes its own tags, leaving no trace in the output. Content handed on twice is wrapped
 * twice, so each hand-off unwinds one level and the content ends up reading its own writer.
 */
class CompositionCallerProcessor extends AbstractElementTagProcessor {

    /** Underscored to read as internal, and so {@code toTagName} cannot produce it from a class name. */
    static final String TAG_NAME = "_caller";
    private static final int PRECEDENCE = 50;

    CompositionCallerProcessor(String dialectPrefix) {
        super(TemplateMode.HTML, dialectPrefix, TAG_NAME, true, null, false, PRECEDENCE);
    }

    @Override
    protected void doProcess(ITemplateContext context, IProcessableElementTag tag,
                             IElementTagStructureHandler structureHandler) {
        ComponentFrame caller = ComponentFrame.callerOf(context.getVariable(ComponentFrame.VARIABLE));
        if (caller == null) {
            // Written at template level, where no component was rendering: `this` is genuinely unset there.
            structureHandler.removeLocalVariable(CompositionElementModelProcessor.COMPONENT_VARIABLE);
            structureHandler.removeLocalVariable(ComponentFrame.VARIABLE);
        } else {
            structureHandler.setLocalVariable(
                    CompositionElementModelProcessor.COMPONENT_VARIABLE, caller.component());
            structureHandler.setLocalVariable(ComponentFrame.VARIABLE, caller);
        }
        structureHandler.removeTags();
    }
}
