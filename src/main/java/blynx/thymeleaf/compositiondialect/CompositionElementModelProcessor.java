package blynx.thymeleaf.compositiondialect;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.thymeleaf.context.ITemplateContext;
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
import org.thymeleaf.standard.processor.StandardReplaceTagProcessor;
import org.thymeleaf.templatemode.TemplateMode;

/**
 * Core rendering engine: for each occurrence of a component tag it instantiates the component,
 * exposes it as the {@code this} local variable, and renders the component's template with the
 * caller's slot content spliced in around {@code c:slot} markers.
 */
public class CompositionElementModelProcessor extends AbstractElementModelProcessor {

    private static final int PRECEDENCE = StandardReplaceTagProcessor.PRECEDENCE;

    private final String dialectPrefix;
    private final String elementName;
    private final Class<? extends CompositionComponent> componentClass;
    private final String slotTagName;
    private final String slotNameAttributeName;
    private final String componentPath;
    private final Constructor<? extends CompositionComponent> componentConstructor;

    private volatile FragmentInfo cachedFragment = null;

    /** Events between slot markers; {@code segments.length == slotMarkerNames.length + 1}. */
    private record FragmentInfo(IModel[] segments, String[] slotMarkerNames) {
    }

    public CompositionElementModelProcessor(String dialectPrefix, String elementName,
                                            Class<? extends CompositionComponent> componentClass,
                                            String componentsPath) {
        super(TemplateMode.HTML, dialectPrefix, elementName, true, null, false, PRECEDENCE);
        this.dialectPrefix = dialectPrefix;
        this.elementName = elementName;
        this.componentClass = componentClass;
        this.slotTagName = dialectPrefix + ":slot";
        this.slotNameAttributeName = dialectPrefix + ":name";
        this.componentPath = buildComponentPath(componentsPath, componentClass, elementName);
        try {
            this.componentConstructor = componentClass.getConstructor(CompositionComponentContext.class);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(CompositionDialect.DIALECT_NAME + ": Component \"" + elementName + "\" ("
                    + componentClass.getName() + ") must declare a public constructor taking a single "
                    + CompositionComponentContext.class.getSimpleName() + " argument", e);
        }
    }

    @Override
    protected void doProcess(ITemplateContext context, IModel tag, IElementModelStructureHandler structureHandler) {
        FragmentInfo fragment = getOrLoadFragment(context);
        Map<String, List<ITemplateEvent>> slots = extractSlots(tag, context.getModelFactory());
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
            componentInstance = componentConstructor.newInstance(componentContext);
        } catch (Exception e) {
            throw new TemplateProcessingException(CompositionDialect.DIALECT_NAME
                    + ": Could not instantiate component \"" + elementName + "\" (" + componentClass.getName() + ")", e);
        }
        structureHandler.setLocalVariable("this", componentInstance);

        tag.reset();
        renderFragmentInto(tag, fragment, slots);
    }

    private FragmentInfo getOrLoadFragment(ITemplateContext context) {
        FragmentInfo cached = cachedFragment;
        if (cached != null) {
            return cached;
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

        // Pre-split the fragment into Model segments separated by slot markers.
        // At render time each segment is bulk-copied via addModel (System.arraycopy)
        // instead of inserted event-by-event.
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
        cachedFragment = info;
        return info;
    }

    private void renderFragmentInto(IModel target, FragmentInfo fragment, Map<String, List<ITemplateEvent>> slots) {
        IModel[] segments = fragment.segments();
        String[] slotNames = fragment.slotMarkerNames();

        target.addModel(segments[0]);
        for (int i = 0; i < slotNames.length; i++) {
            List<ITemplateEvent> content = slots.get(slotNames[i]);
            if (content != null) {
                for (ITemplateEvent event : content) {
                    target.add(event);
                }
            }
            target.addModel(segments[i + 1]);
        }
    }

    private Map<String, List<ITemplateEvent>> extractSlots(IModel tag, IModelFactory modelFactory) {
        Map<String, List<ITemplateEvent>> slots = new HashMap<>(4);
        String slotName = CompositionComponent.DEFAULT_SLOT;
        int level = 0;
        for (int i = 1; i < tag.size() - 1; i++) {
            ITemplateEvent event = tag.get(i);
            if (event instanceof IOpenElementTag) {
                level++;
            } else if (event instanceof ICloseElementTag) {
                level--;
            }
            if (event instanceof IProcessableElementTag processableTag && processableTag.hasAttribute(slotTagName)) {
                if (level == 1) {
                    String value = processableTag.getAttributeValue(slotTagName);
                    slotName = value != null ? value : CompositionComponent.DEFAULT_SLOT;
                }
                event = modelFactory.removeAttribute(processableTag, slotTagName);
            }
            slots.computeIfAbsent(slotName, key -> new ArrayList<>()).add(event);
            if (level == 0 && event instanceof ICloseElementTag) {
                slotName = CompositionComponent.DEFAULT_SLOT;
            }
        }
        return slots;
    }

    private Map<String, Object> extractAttrs(IProcessableElementTag rootElement, ITemplateContext context) {
        var allAttributes = rootElement.getAllAttributes();
        Map<String, Object> attrs = new HashMap<>(allAttributes.length);
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

    private String buildComponentPath(String componentsPath, Class<? extends CompositionComponent> componentClass,
                                      String elementName) {
        List<String> pathParts = new ArrayList<>();
        if (componentsPath != null && !componentsPath.isEmpty()) {
            pathParts.add(trimSlashes(componentsPath));
        }
        // get component path field of reflected Component class
        String componentPath;
        try {
            componentPath = trimSlashes((String) componentClass.getField("path").get(componentClass));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(CompositionDialect.DIALECT_NAME
                    + ": Could not read the static \"path\" field of component " + componentClass.getName(), e);
        }
        if (!componentPath.isEmpty()) {
            pathParts.add(componentPath);
        }
        pathParts.add(elementName);
        return String.join("/", pathParts);
    }

    private static String trimSlashes(String value) {
        return value.replaceAll("^/+|/+$", "");
    }
}
