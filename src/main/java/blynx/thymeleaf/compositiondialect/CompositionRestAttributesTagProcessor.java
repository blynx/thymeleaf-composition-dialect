package blynx.thymeleaf.compositiondialect;

import java.util.Map;

import org.thymeleaf.context.ITemplateContext;
import org.thymeleaf.engine.AttributeName;
import org.thymeleaf.exceptions.TemplateProcessingException;
import org.thymeleaf.model.IProcessableElementTag;
import org.thymeleaf.processor.element.AbstractAttributeTagProcessor;
import org.thymeleaf.processor.element.IElementTagStructureHandler;
import org.thymeleaf.standard.expression.IStandardExpressionParser;
import org.thymeleaf.standard.expression.StandardExpressions;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.util.EscapedAttributeUtils;

/**
 * Implements the {@code c:rest} attribute: spreads every attribute that the component did not
 * read onto the element it is placed on, and — since this is the one placement decision a template makes
 * for "what the caller passes through" — also merges a caller's {@code th:classappend}/{@code th:styleappend}
 * onto the same element, rather than {@link CompositionElementModelProcessor} rejecting them outright the
 * way it does {@code th:attrappend}/{@code th:attrprepend} (see {@link CompositionElementModelProcessor
 * #APPEND_MERGE_TARGETS}).
 *
 * <p>Whose attributes those are is decided by whichever component is rendering — the same unprefixed
 * {@code this} the rest of the dialect reads. So this means "the component whose template I am written
 * in", not "a component registered under my prefix"; under two dialects at different prefixes, either
 * spelling acts on whatever is rendering. See {@code MultiPrefixTest}.
 */
public class CompositionRestAttributesTagProcessor extends AbstractAttributeTagProcessor {

    public static final int PRECEDENCE = 200;
    private static final String ATTRIBUTE_NAME = "rest";

    // Interned th:classappend/th:styleappend attribute names, resolved and cached the same way
    // CompositionElementModelProcessor caches its own interned names — plain AttributeName/ElementName
    // instances carry no engine affinity, unlike an element definition's attached processors, so caching
    // without checking configuration identity is safe here too.
    private volatile AttributeName classappendAttributeName = null;
    private volatile AttributeName styleappendAttributeName = null;

    public CompositionRestAttributesTagProcessor(String dialectPrefix) {
        super(TemplateMode.HTML, dialectPrefix, null, false, ATTRIBUTE_NAME, true, PRECEDENCE, true);
    }

    @Override
    protected void doProcess(ITemplateContext context, IProcessableElementTag tag, AttributeName attributeName,
                             String attributeValue, IElementTagStructureHandler structureHandler) {
        // No component rendering means this was written where there is nothing to spread — a page, or
        // content being passed to a component, which steps back out to where it was written. Rejected
        // rather than quietly doing nothing, as the same misplacement already is for <c:slot>.
        if (!(context.getVariable(CompositionElementModelProcessor.COMPONENT_VARIABLE)
                instanceof CompositionComponent component)) {
            throw new TemplateProcessingException(CompositionDialect.DIALECT_NAME
                    + ": " + getDialectPrefix() + ":" + ATTRIBUTE_NAME + " was used where no component is "
                    + "rendering. It spreads the attributes of the component whose template it is written "
                    + "in, so it only means something inside a component's own template — not in a page, "
                    + "and not in content being passed to a component.");
        }
        Map<String, Object> rest = component.restAttributes();
        rest.forEach((key, value) -> structureHandler.setAttribute(key, value != null ? value.toString() : ""));

        Map<String, String> appends = component.context().attributes().appends();
        if (!appends.isEmpty()) {
            mergeAppend(context, tag, structureHandler, rest, appends, "class",
                    classappendAttributeName(context));
            mergeAppend(context, tag, structureHandler, rest, appends, "style",
                    styleappendAttributeName(context));
        }
    }

    /**
     * Combines, in order, whatever the caller's plain {@code class}/{@code style} already put there (read
     * from {@code rest} rather than {@code tag} — {@code structureHandler.setAttribute} above only queues a
     * change for the engine to apply once this method returns, so {@code tag} itself never reflects it),
     * else the element's own static value; then the template's own {@code th:classappend}/
     * {@code th:styleappend}, evaluated here — before the standard dialect's own processor
     * ({@code StandardClassappendTagProcessor}/{@code StandardStyleappendTagProcessor}, precedence 1100,
     * after this processor's 200 — would get to it — read as raw text and evaluated manually, the same
     * technique {@code CompositionElementModelProcessor#extractAttrs} already uses for the call-site tag,
     * removing the attribute so the standard processor never runs on it a second time; then the caller's own
     * contribution. Space-joined at each non-empty step, matching {@code StandardClassappendTagProcessor}/
     * {@code StandardStyleappendTagProcessor}'s own rule, including their HTML-escaping of each newly
     * evaluated fragment (the element's pre-existing value is left as-is, exactly as those processors leave
     * it — it is already-escaped markup text, not a fresh expression result).
     */
    private static void mergeAppend(ITemplateContext context, IProcessableElementTag tag,
                                    IElementTagStructureHandler structureHandler, Map<String, Object> rest,
                                    Map<String, String> appends, String targetAttributeName,
                                    AttributeName templateAppendAttributeName) {
        String callerValue = appends.get(targetAttributeName);
        if (callerValue == null) {
            return;
        }
        String merged = rest.containsKey(targetAttributeName)
                ? stringify(rest.get(targetAttributeName))
                : tag.getAttributeValue(targetAttributeName);
        if (merged == null) {
            merged = "";
        }
        if (templateAppendAttributeName != null && tag.hasAttribute(templateAppendAttributeName)) {
            String rawExpression = tag.getAttributeValue(templateAppendAttributeName);
            structureHandler.removeAttribute(templateAppendAttributeName);
            if (rawExpression != null) {
                IStandardExpressionParser expressionParser =
                        StandardExpressions.getExpressionParser(context.getConfiguration());
                Object result = expressionParser.parseExpression(context, rawExpression).execute(context);
                String templateValue = result != null ? result.toString() : null;
                if (templateValue != null && !templateValue.isEmpty()) {
                    merged = append(merged, EscapedAttributeUtils.escapeAttribute(TemplateMode.HTML, templateValue));
                }
            }
        }
        merged = append(merged, EscapedAttributeUtils.escapeAttribute(TemplateMode.HTML, callerValue));
        structureHandler.setAttribute(targetAttributeName, merged);
    }

    private static String stringify(Object value) {
        return value != null ? value.toString() : "";
    }

    private static String append(String current, String addition) {
        return current.isEmpty() ? addition : current + ' ' + addition;
    }

    private AttributeName classappendAttributeName(ITemplateContext context) {
        AttributeName name = classappendAttributeName;
        if (name == null) {
            name = standardAttributeName(context, "classappend");
            classappendAttributeName = name;
        }
        return name;
    }

    private AttributeName styleappendAttributeName(ITemplateContext context) {
        AttributeName name = styleappendAttributeName;
        if (name == null) {
            name = standardAttributeName(context, "styleappend");
            styleappendAttributeName = name;
        }
        return name;
    }

    /** {@code null} when the standard dialect isn't configured — which also means {@code appends} could
     * never have been populated in the first place, so callers already only reach this when it is. */
    private AttributeName standardAttributeName(ITemplateContext context, String plainName) {
        String standardPrefix = context.getConfiguration().getStandardDialectPrefix();
        if (standardPrefix == null) {
            return null;
        }
        return context.getConfiguration().getAttributeDefinitions()
                .forName(getTemplateMode(), standardPrefix, plainName).getAttributeName();
    }
}
