package blynx.thymeleaf.compositiondialect;

import java.lang.invoke.CallSite;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.thymeleaf.IEngineConfiguration;
import org.thymeleaf.cache.ICacheEntryValidity;
import org.thymeleaf.context.ITemplateContext;
import org.thymeleaf.engine.AttributeName;
import org.thymeleaf.engine.ElementName;
import org.thymeleaf.engine.TemplateModel;
import org.thymeleaf.exceptions.TemplateProcessingException;
import org.thymeleaf.model.ICloseElementTag;
import org.thymeleaf.model.IElementTag;
import org.thymeleaf.model.IModel;
import org.thymeleaf.model.IModelFactory;
import org.thymeleaf.model.IOpenElementTag;
import org.thymeleaf.model.IProcessableElementTag;
import org.thymeleaf.model.IStandaloneElementTag;
import org.thymeleaf.model.ITemplateEvent;
import org.thymeleaf.model.IText;
import org.thymeleaf.processor.element.AbstractElementModelProcessor;
import org.thymeleaf.processor.element.IElementModelStructureHandler;
import org.thymeleaf.standard.expression.StandardExpressions;
import org.thymeleaf.standard.processor.StandardUnlessTagProcessor;
import org.thymeleaf.templatemode.TemplateMode;

/**
 * Core rendering engine: for each occurrence of a component tag it instantiates the component,
 * exposes it as the {@code this} local variable, and renders the component's template with the
 * caller's slot content spliced in around {@code c:slot} markers.
 */
public class CompositionElementModelProcessor extends AbstractElementModelProcessor {

    /** The component whose template is rendering, as templates see it. */
    static final String COMPONENT_VARIABLE = "this";

    /**
     * Behind every standard processor that decides <em>whether</em> an element exists — {@code th:each}
     * (200), {@code th:switch}/{@code th:case}, {@code th:if} (300), {@code th:unless} (400) — and ahead of
     * the ones that fill it in ({@code th:object}, {@code th:with}, {@code th:attr}, {@code th:text}).
     * Control flow therefore decides whether a component is rendered, and the component then owns the tag.
     *
     * <p>Not {@code th:replace}'s own precedence (100), which this used to copy: a processor that replaces
     * its element ends that element's processing, so at 100 a {@code th:if} on the component tag itself was
     * silently ignored — as it still is on a {@code th:replace}.
     */
    private static final int PRECEDENCE = StandardUnlessTagProcessor.PRECEDENCE + 50;

    private final String dialectPrefix;
    private final String elementName;
    private final Class<? extends CompositionComponent> componentClass;
    private final String slotNameAttributeName;
    private final String callerTagName;
    private final String componentPath;
    private final Function<CompositionComponentContext, CompositionComponent> componentFactory;

    private volatile CachedFragment cachedFragment = null;

    // Interned c:slot name: the AttributeName overloads match by identity, while the String
    // overloads take a JVM-global lock for every tag that lacks the attribute.
    private volatile AttributeName slotAttributeName = null;

    // Interned c:slot element name, resolved the same way and for the same reason as slotAttributeName
    // above. Matching by identity (rather than a raw string compare on getElementCompleteName()) is what
    // makes <c-slot />/<data-c-slot /> recognized as the same marker as <c:slot />, exactly as c:name and
    // data-c-name already both work on the attribute side.
    private volatile ElementName slotElementName = null;

    // The standard dialect's prefix is configurable, so th:text/th:utext can only be resolved once we
    // have an IEngineConfiguration in hand; cached the same way as slotAttributeName above.
    private volatile StandardTextAttributes standardTextAttributes = null;

    /**
     * Events between slot markers; {@code segments.length == slotMarkerNames.length + 1}.
     * {@code fallbacks[i]} holds marker {@code i}'s fallback content when it was written in paired form
     * ({@code <c:slot>...</c:slot>}), {@code null} when it was self-closing.
     */
    private record FragmentInfo(IModel[] segments, String[] slotMarkerNames, IModel[] fallbacks) {
    }

    private record CachedFragment(FragmentInfo info, ICacheEntryValidity validity) {
    }

    /** The standard dialect's {@code th:text}/{@code th:utext}, resolved under its configured prefix. */
    private record StandardTextAttributes(AttributeName text, AttributeName utext, String textCompleteName,
                                          String utextCompleteName, String blockTagName) {
    }

    public CompositionElementModelProcessor(String dialectPrefix, ComponentDescriptor descriptor) {
        super(TemplateMode.HTML, dialectPrefix, descriptor.tagName(), true, null, false, PRECEDENCE);
        this.dialectPrefix = dialectPrefix;
        this.elementName = descriptor.tagName();
        this.componentClass = descriptor.componentClass();
        this.slotNameAttributeName = dialectPrefix + ":name";
        this.callerTagName = dialectPrefix + ":" + CompositionCallerProcessor.TAG_NAME;
        this.componentPath = descriptor.templatePath();
        Constructor<? extends CompositionComponent> componentConstructor;
        try {
            componentConstructor = componentClass.getConstructor(CompositionComponentContext.class);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(CompositionDialect.DIALECT_NAME + ": Component \"" + elementName + "\" ("
                    + componentClass.getName() + ") must declare a public constructor taking a single "
                    + CompositionComponentContext.class.getSimpleName() + " argument", e);
        }
        this.componentFactory = createComponentFactory(componentConstructor);
    }

    @Override
    protected void doProcess(ITemplateContext context, IModel tag, IElementModelStructureHandler structureHandler) {
        FragmentInfo fragment = getOrLoadFragment(context);
        IModelFactory modelFactory = context.getModelFactory();
        IProcessableElementTag rootElement = (IProcessableElementTag) tag.get(0);
        StandardTextAttributes textAttrs = standardTextAttributes(context.getConfiguration());

        Map<String, IModel> slots = extractSlots(tag, modelFactory, slotAttributeName(context.getConfiguration()));
        removeBlankDefaultSlot(slots);
        IModel textShorthand = textShorthandSlotContent(rootElement, textAttrs, modelFactory);
        if (textShorthand != null) {
            // th:text/th:utext silently wins over any explicit default-slot children, mirroring th:text's
            // own "replaces whatever body was there" behavior on a hand-written element.
            slots.put(CompositionComponent.DEFAULT_SLOT, textShorthand);
        }
        Map<String, Object> attrs = extractAttrs(rootElement, context, textAttrs);

        CompositionComponentContext componentContext = new CompositionComponentContext(
                new TrackingAttributes(attrs),
                slots.keySet(),
                context.getLocale(),
                (code, params) -> context.getMessage(CompositionElementModelProcessor.class, code, params, false),
                context::getVariable,
                structureHandler::setLocalVariable);

        CompositionComponent componentInstance;
        try {
            componentInstance = componentFactory.apply(componentContext);
        } catch (Exception e) {
            throw new TemplateProcessingException(CompositionDialect.DIALECT_NAME
                    + ": Could not instantiate component \"" + elementName + "\" (" + componentClass.getName() + ")", e);
        }
        structureHandler.setLocalVariable(COMPONENT_VARIABLE, componentInstance);
        // Read before the line above takes effect, so this is the frame the tag was written in.
        structureHandler.setLocalVariable(ComponentFrame.VARIABLE, new ComponentFrame(componentInstance,
                context.getVariable(ComponentFrame.VARIABLE) instanceof ComponentFrame frame ? frame : null));

        tag.reset();
        renderFragmentInto(tag, fragment, slots, modelFactory);
    }

    private FragmentInfo getOrLoadFragment(ITemplateContext context) {
        CachedFragment cached = cachedFragment;
        if (cached != null && cached.validity().isCacheStillValid()) {
            return cached.info();
        }

        TemplateModel templateModel;
        try {
            templateModel = context.getConfiguration().getTemplateManager()
                    .parseStandalone(context, componentPath, (Set<String>) null, (TemplateMode) null, true, true);
        } catch (Exception e) {
            throw new TemplateProcessingException(CompositionDialect.DIALECT_NAME
                    + ": Could not load template for component \"" + elementName + "\" from \"" + componentPath
                    + ".html\" (relative to thymeleaf templates path)");
        }

        // Pre-split around the slot markers so rendering bulk-copies each segment via addModel.
        IModelFactory modelFactory = context.getModelFactory();
        ElementName slotElement = slotElementName(context.getConfiguration());
        List<IModel> segments = new ArrayList<>();
        List<String> names = new ArrayList<>();
        List<IModel> fallbacks = new ArrayList<>();

        IModel currentSegment = modelFactory.createModel();
        int end = templateModel.size() - 1;
        for (int i = 1; i < end; i++) {
            ITemplateEvent event = templateModel.get(i);
            if (!isSlotMarker(event, slotElement)) {
                currentSegment.add(event);
                continue;
            }
            segments.add(currentSegment);
            names.add(markerSlotName(event));
            if (event instanceof IStandaloneElementTag) {
                fallbacks.add(null);
            } else {
                int closeAt = matchingCloseIndex(templateModel, i, end, slotElement);
                IModel fallback = modelFactory.createModel();
                for (int j = i + 1; j < closeAt; j++) {
                    fallback.add(templateModel.get(j));
                }
                fallbacks.add(fallback);
                i = closeAt;
            }
            currentSegment = modelFactory.createModel();
        }
        segments.add(currentSegment);

        FragmentInfo info = new FragmentInfo(segments.toArray(new IModel[0]), names.toArray(new String[0]),
                fallbacks.toArray(new IModel[0]));
        // Cache only cacheable templates and re-check validity per hit (dev-mode edits, TTL expiry).
        // Not flushed by TemplateEngine.clearTemplateCaches().
        ICacheEntryValidity validity = templateModel.getTemplateData().getValidity();
        if (validity.isCacheable()) {
            cachedFragment = new CachedFragment(info, validity);
        }
        return info;
    }

    /**
     * Index of the close tag pairing with the open marker at {@code openAt}, found by tracking generic
     * open/close nesting depth from 1 — the same balanced-bracket technique as any other tag-matching,
     * regardless of what the nested markup is. Standalone tags in between don't affect depth; another
     * {@code c:slot} marker found while scanning is rejected outright, since native {@code <slot>} permits
     * a fallback to contain another slot but our marker recognition has no notion of nesting depth to
     * resolve that safely.
     */
    private int matchingCloseIndex(TemplateModel templateModel, int openAt, int end, ElementName slotElement) {
        int depth = 1;
        for (int j = openAt + 1; j < end; j++) {
            ITemplateEvent event = templateModel.get(j);
            if (isSlotMarker(event, slotElement)) {
                throw new TemplateProcessingException(CompositionDialect.DIALECT_NAME
                        + ": <" + dialectPrefix + ":slot> found inside another slot's fallback content, in "
                        + "component \"" + elementName + "\" (" + componentPath + ".html) — a slot's fallback "
                        + "content may not itself contain another slot marker.");
            }
            if (event instanceof IOpenElementTag) {
                depth++;
            } else if (event instanceof ICloseElementTag) {
                depth--;
                if (depth == 0) {
                    return j;
                }
            }
        }
        throw new TemplateProcessingException(CompositionDialect.DIALECT_NAME
                + ": <" + dialectPrefix + ":slot> in component \"" + elementName + "\" (" + componentPath
                + ".html) is never closed.");
    }

    /** Whether {@code event} is a {@code c:slot} marker's own open or standalone tag — never its close tag. */
    private boolean isSlotMarker(ITemplateEvent event, ElementName slotElement) {
        return (event instanceof IStandaloneElementTag || event instanceof IOpenElementTag)
                && ((IElementTag) event).getElementDefinition().getElementName().equals(slotElement);
    }

    private String markerSlotName(ITemplateEvent event) {
        String name = ((IProcessableElementTag) event).getAttributeValue(slotNameAttributeName);
        return name != null ? name : CompositionComponent.DEFAULT_SLOT;
    }

    /**
     * Appends the component's segments with the caller's slot content spliced in between them, each piece
     * of content wrapped in a marker that steps back out of this component — the content was written
     * outside it and must keep reading the {@code this} that was in effect there. A slot the caller left
     * empty falls back to its own fallback content, if it has any — added unwrapped, since a fallback is
     * the component's own markup and must keep reading this component's {@code this}, not step back out.
     */
    private void renderFragmentInto(IModel target, FragmentInfo fragment, Map<String, IModel> slots,
                                    IModelFactory modelFactory) {
        IModel[] segments = fragment.segments();
        String[] slotNames = fragment.slotMarkerNames();
        IModel[] fallbacks = fragment.fallbacks();

        target.addModel(segments[0]);
        for (int i = 0; i < slotNames.length; i++) {
            IModel content = slots.get(slotNames[i]);
            if (content != null) {
                target.add(modelFactory.createOpenElementTag(callerTagName));
                target.addModel(content);
                target.add(modelFactory.createCloseElementTag(callerTagName));
            } else if (fallbacks[i] != null) {
                target.addModel(fallbacks[i]);
            }
            target.addModel(segments[i + 1]);
        }
    }

    private Map<String, IModel> extractSlots(IModel tag, IModelFactory modelFactory, AttributeName slotAttr) {
        Map<String, IModel> slots = HashMap.newHashMap(4);
        // defaultSlot is held separately because every top-level close switches back to it
        IModel defaultSlot = null;
        IModel currentSlot = null;
        String slotName = CompositionComponent.DEFAULT_SLOT;
        int level = 0;
        for (int i = 1, end = tag.size() - 1; i < end; i++) {
            ITemplateEvent event = tag.get(i);
            boolean opens = event instanceof IOpenElementTag;
            boolean standalone = event instanceof IStandaloneElementTag;
            if (opens) {
                level++;
            } else if (event instanceof ICloseElementTag) {
                level--;
            }
            // Like native shadow-DOM slotting, c:slot is consumed on direct children only;
            // deeper occurrences are left for nested component invocations to assign.
            if (((opens && level == 1) || (standalone && level == 0))
                    && event instanceof IProcessableElementTag processableTag
                    && processableTag.hasAttribute(slotAttr)) {
                String value = processableTag.getAttributeValue(slotAttr);
                String newName = value != null ? value : CompositionComponent.DEFAULT_SLOT;
                if (!newName.equals(slotName)) {
                    slotName = newName;
                    currentSlot = null;
                }
                event = modelFactory.removeAttribute(processableTag, slotAttr);
            }
            if (currentSlot == null) {
                currentSlot = slots.computeIfAbsent(slotName, key -> modelFactory.createModel());
                if (slotName.equals(CompositionComponent.DEFAULT_SLOT)) {
                    defaultSlot = currentSlot;
                }
            }
            currentSlot.add(event);
            if (level == 0 && (standalone || event instanceof ICloseElementTag)) {
                slotName = CompositionComponent.DEFAULT_SLOT;
                currentSlot = defaultSlot;
            }
        }
        return slots;
    }

    /**
     * Whitespace-only default-slot content (e.g. just the indentation between a component's tags) is
     * treated as if the caller passed nothing at all — deviating from native {@code <slot>}, where a bare
     * text node still counts as content and silently defeats a default-slot fallback. Named slots are never
     * affected: only an element can carry {@code c:slot}, and a bare text node cannot.
     */
    private void removeBlankDefaultSlot(Map<String, IModel> slots) {
        IModel defaultSlot = slots.get(CompositionComponent.DEFAULT_SLOT);
        if (defaultSlot != null && isBlank(defaultSlot)) {
            slots.remove(CompositionComponent.DEFAULT_SLOT);
        }
    }

    private static boolean isBlank(IModel model) {
        for (int i = 0; i < model.size(); i++) {
            if (!(model.get(i) instanceof IText text) || !text.getText().isBlank()) {
                return false;
            }
        }
        return true;
    }

    /**
     * {@code th:text}/{@code th:utext} on the component tag itself, synthesized as the equivalent
     * hand-written {@code <th:block th:text="...">} and used as the default slot's content — see
     * docs/reference/slots.md. Built via {@link IModelFactory} (open tag + copy the attribute verbatim +
     * close tag), so the value is neither parsed nor evaluated here: it rides the normal slot-content path
     * and {@code th:text} fires on the fresh tag under its own unchanged precedence.
     *
     * <p>Returns {@code null} when neither attribute is present, or when the standard dialect isn't
     * configured at all (in which case neither attribute could mean anything).
     */
    private IModel textShorthandSlotContent(IProcessableElementTag rootElement, StandardTextAttributes textAttrs,
                                            IModelFactory modelFactory) {
        if (textAttrs == null) {
            return null;
        }
        String text = rootElement.hasAttribute(textAttrs.text()) ? rootElement.getAttributeValue(textAttrs.text()) : null;
        String utext = rootElement.hasAttribute(textAttrs.utext()) ? rootElement.getAttributeValue(textAttrs.utext()) : null;
        if (text == null && utext == null) {
            return null;
        }
        // Both present at once: don't validate ourselves, copy both onto the synthetic tag and let the
        // standard dialect's own precedence resolve it, exactly as it would on a hand-written element.
        IOpenElementTag block = modelFactory.createOpenElementTag(textAttrs.blockTagName());
        if (text != null) {
            block = modelFactory.setAttribute(block, textAttrs.textCompleteName(), text);
        }
        if (utext != null) {
            block = modelFactory.setAttribute(block, textAttrs.utextCompleteName(), utext);
        }
        IModel content = modelFactory.createModel();
        content.add(block);
        content.add(modelFactory.createCloseElementTag(textAttrs.blockTagName()));
        return content;
    }

    private Map<String, Object> extractAttrs(IProcessableElementTag rootElement, ITemplateContext context,
                                             StandardTextAttributes textAttrs) {
        var allAttributes = rootElement.getAllAttributes();
        Map<String, Object> attrs = HashMap.newHashMap(allAttributes.length);
        var expressionParser = StandardExpressions.getExpressionParser(context.getConfiguration());
        for (var attr : allAttributes) {
            AttributeName attributeName = attr.getAttributeDefinition().getAttributeName();
            // Already relocated onto the synthetic th:block by textShorthandSlotContent; skip so they
            // don't also leak into restAttributes/c:rest as unevaluated raw strings.
            if (textAttrs != null && (attributeName.equals(textAttrs.text()) || attributeName.equals(textAttrs.utext()))) {
                continue;
            }
            String plainAttributeName = attributeName.getAttributeName();
            if (dialectPrefix.equals(attributeName.getPrefix())) {
                attrs.put(plainAttributeName,
                        expressionParser.parseExpression(context, attr.getValue()).execute(context));
            } else {
                attrs.put(plainAttributeName, attr.getValue());
            }
        }
        return attrs;
    }

    private AttributeName slotAttributeName(IEngineConfiguration configuration) {
        AttributeName name = slotAttributeName;
        if (name == null) {
            // Benign race: the repository interns the name, so every thread resolves the same instance.
            name = configuration.getAttributeDefinitions()
                    .forName(getTemplateMode(), dialectPrefix, "slot").getAttributeName();
            slotAttributeName = name;
        }
        return name;
    }

    private ElementName slotElementName(IEngineConfiguration configuration) {
        ElementName name = slotElementName;
        if (name == null) {
            // Benign race, as with slotAttributeName above: every thread resolves the same interned instance.
            name = configuration.getElementDefinitions()
                    .forName(getTemplateMode(), dialectPrefix, "slot").getElementName();
            slotElementName = name;
        }
        return name;
    }

    /**
     * Resolves {@code th:text}/{@code th:utext} under whatever prefix the standard dialect is actually
     * configured with, rather than hardcoding {@code "th"}. {@code null} when the standard dialect isn't
     * present at all.
     */
    private StandardTextAttributes standardTextAttributes(IEngineConfiguration configuration) {
        StandardTextAttributes cached = standardTextAttributes;
        if (cached != null) {
            return cached;
        }
        String standardPrefix = configuration.getStandardDialectPrefix();
        if (standardPrefix == null) {
            return null;
        }
        // Benign race, as with slotAttributeName above: every thread resolves the same interned instances.
        var attributeDefinitions = configuration.getAttributeDefinitions();
        StandardTextAttributes resolved = new StandardTextAttributes(
                attributeDefinitions.forName(getTemplateMode(), standardPrefix, "text").getAttributeName(),
                attributeDefinitions.forName(getTemplateMode(), standardPrefix, "utext").getAttributeName(),
                standardPrefix + ":text",
                standardPrefix + ":utext",
                standardPrefix + ":block");
        standardTextAttributes = resolved;
        return resolved;
    }

    private static Function<CompositionComponentContext, CompositionComponent> createComponentFactory(
            Constructor<? extends CompositionComponent> constructor) {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            MethodHandle handle = lookup.unreflectConstructor(constructor);
            CallSite callSite = LambdaMetafactory.metafactory(lookup, "apply",
                    MethodType.methodType(Function.class),
                    MethodType.methodType(Object.class, Object.class),
                    handle,
                    MethodType.methodType(CompositionComponent.class, CompositionComponentContext.class));
            @SuppressWarnings("unchecked")
            Function<CompositionComponentContext, CompositionComponent> factory =
                    (Function<CompositionComponentContext, CompositionComponent>) callSite.getTarget().invokeExact();
            return factory;
        } catch (Throwable e) {
            // e.g. JPMS setups where the component's package is not accessible from this module
            return componentContext -> {
                try {
                    return constructor.newInstance(componentContext);
                } catch (ReflectiveOperationException ex) {
                    throw new IllegalStateException(ex);
                }
            };
        }
    }
}
