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
import org.thymeleaf.engine.TemplateModel;
import org.thymeleaf.exceptions.TemplateProcessingException;
import org.thymeleaf.model.ICloseElementTag;
import org.thymeleaf.model.IModel;
import org.thymeleaf.model.IModelFactory;
import org.thymeleaf.model.IOpenElementTag;
import org.thymeleaf.model.IProcessableElementTag;
import org.thymeleaf.model.IStandaloneElementTag;
import org.thymeleaf.model.ITemplateEvent;
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
    private final String slotTagName;
    private final String slotNameAttributeName;
    private final String callerTagName;
    private final String componentPath;
    private final Function<CompositionComponentContext, CompositionComponent> componentFactory;

    private volatile CachedFragment cachedFragment = null;

    // Interned c:slot name: the AttributeName overloads match by identity, while the String
    // overloads take a JVM-global lock for every tag that lacks the attribute.
    private volatile AttributeName slotAttributeName = null;

    /** Events between slot markers; {@code segments.length == slotMarkerNames.length + 1}. */
    private record FragmentInfo(IModel[] segments, String[] slotMarkerNames) {
    }

    private record CachedFragment(FragmentInfo info, ICacheEntryValidity validity) {
    }

    public CompositionElementModelProcessor(String dialectPrefix, ComponentDescriptor descriptor) {
        super(TemplateMode.HTML, dialectPrefix, descriptor.tagName(), true, null, false, PRECEDENCE);
        this.dialectPrefix = dialectPrefix;
        this.elementName = descriptor.tagName();
        this.componentClass = descriptor.componentClass();
        this.slotTagName = dialectPrefix + ":slot";
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
        Map<String, IModel> slots = extractSlots(tag, context.getModelFactory(),
                slotAttributeName(context.getConfiguration()));
        Map<String, Object> attrs = extractAttrs((IProcessableElementTag) tag.get(0), context);

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
        renderFragmentInto(tag, fragment, slots, context.getModelFactory());
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
        List<IModel> segments = new ArrayList<>();
        List<String> names = new ArrayList<>();

        IModel currentSegment = modelFactory.createModel();
        for (int i = 1; i < templateModel.size() - 1; i++) {
            ITemplateEvent event = templateModel.get(i);
            if (event instanceof IStandaloneElementTag standaloneTag
                    && standaloneTag.getElementCompleteName().equals(slotTagName)) {
                segments.add(currentSegment);
                String name = standaloneTag.getAttributeValue(slotNameAttributeName);
                names.add(name != null ? name : CompositionComponent.DEFAULT_SLOT);
                currentSegment = modelFactory.createModel();
            } else {
                currentSegment.add(event);
            }
        }
        segments.add(currentSegment);

        FragmentInfo info = new FragmentInfo(segments.toArray(new IModel[0]), names.toArray(new String[0]));
        // Cache only cacheable templates and re-check validity per hit (dev-mode edits, TTL expiry).
        // Not flushed by TemplateEngine.clearTemplateCaches().
        ICacheEntryValidity validity = templateModel.getTemplateData().getValidity();
        if (validity.isCacheable()) {
            cachedFragment = new CachedFragment(info, validity);
        }
        return info;
    }

    /**
     * Appends the component's segments with the caller's slot content spliced in between them, each piece
     * of content wrapped in a marker that steps back out of this component — the content was written
     * outside it and must keep reading the {@code this} that was in effect there.
     */
    private void renderFragmentInto(IModel target, FragmentInfo fragment, Map<String, IModel> slots,
                                    IModelFactory modelFactory) {
        IModel[] segments = fragment.segments();
        String[] slotNames = fragment.slotMarkerNames();

        target.addModel(segments[0]);
        for (int i = 0; i < slotNames.length; i++) {
            IModel content = slots.get(slotNames[i]);
            if (content != null) {
                target.add(modelFactory.createOpenElementTag(callerTagName));
                target.addModel(content);
                target.add(modelFactory.createCloseElementTag(callerTagName));
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

    private Map<String, Object> extractAttrs(IProcessableElementTag rootElement, ITemplateContext context) {
        var allAttributes = rootElement.getAllAttributes();
        Map<String, Object> attrs = HashMap.newHashMap(allAttributes.length);
        var expressionParser = StandardExpressions.getExpressionParser(context.getConfiguration());
        for (var attr : allAttributes) {
            String plainAttributeName = attr.getAttributeDefinition().getAttributeName().getAttributeName();
            if (dialectPrefix.equals(attr.getAttributeDefinition().getAttributeName().getPrefix())) {
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
