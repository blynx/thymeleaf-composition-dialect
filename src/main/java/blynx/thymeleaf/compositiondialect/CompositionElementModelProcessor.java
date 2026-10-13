package blynx.thymeleaf.compositiondialect;

import java.lang.invoke.CallSite;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
import org.thymeleaf.processor.AbstractProcessor;
import org.thymeleaf.processor.element.IElementModelProcessor;
import org.thymeleaf.processor.element.IElementModelStructureHandler;
import org.thymeleaf.processor.element.MatchingAttributeName;
import org.thymeleaf.processor.element.MatchingElementName;
import org.thymeleaf.standard.expression.IStandardExpressionParser;
import org.thymeleaf.standard.expression.StandardExpressions;
import org.thymeleaf.standard.processor.StandardUnlessTagProcessor;
import org.thymeleaf.templatemode.TemplateMode;

/**
 * Core rendering engine: one instance per dialect, claiming every tag under the dialect's prefix. For each
 * occurrence it looks the tag name up in the {@link ComponentRegistry}, instantiates the component, exposes
 * it as the {@code this} local variable, and renders the component's template with the caller's slot
 * content spliced in around {@code c:slot} markers. A tag name the registry does not know is, by
 * definition, unresolved — a typo, a missing {@link ComponentSource}, or a tag meant for a different
 * dialect prefix entirely.
 *
 * <p>Implements {@link IElementModelProcessor} directly rather than extending
 * {@link org.thymeleaf.processor.element.AbstractElementModelProcessor}: that base class's constructor can
 * only build a {@link MatchingElementName} for one specific element name, while this processor must match
 * every element under the prefix ({@link MatchingElementName#forAllElementsWithPrefix}) — one wildcard
 * processor standing in for what used to be one processor per registered component, so that adding a
 * component template is a registry lookup, not a new processor.
 */
public class CompositionElementModelProcessor extends AbstractProcessor implements IElementModelProcessor {

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
    static final int PRECEDENCE = StandardUnlessTagProcessor.PRECEDENCE + 50;

    private final String dialectPrefix;
    private final ComponentRegistry registry;
    private final MatchingElementName matchingElementName;
    private final String callerTagName;

    /**
     * The one attribute a {@code c:slot} marker may carry, spelled unprefixed exactly as on a native
     * {@code <slot name="…">} — the marker element is the dialect's own, so there is no host element's
     * attribute for it to collide with, the same reason {@code <c:prop name="…">} is unprefixed too.
     */
    private static final String MARKER_NAME_ATTRIBUTE = "name";

    /** Keyed by tag name (unprefixed): this instance only ever sees tags under its own single prefix. */
    private final ConcurrentHashMap<String, CachedFragment> fragmentCache = new ConcurrentHashMap<>();

    // Interned c:slot name: the AttributeName overloads match by identity, while the String
    // overloads take a JVM-global lock for every tag that lacks the attribute.
    private volatile AttributeName slotAttributeName = null;

    // Interned c:slot element name, resolved the same way and for the same reason as slotAttributeName
    // above. Matching by identity (rather than a raw string compare on getElementCompleteName()) is what
    // makes <c-slot />/<data-c-slot /> recognized as the same marker as <c:slot />, exactly as the
    // call-site c:slot/data-c-slot attribute already works either way.
    private volatile ElementName slotElementName = null;

    // Interned c:component element name, resolved and cached the same way as slotElementName above —
    // used at render time only to recognize and strip the declaration; the registry already resolved it.
    private volatile ElementName componentElementName = null;

    // Interned c:rest attribute name, resolved and cached the same way as slotAttributeName above — used
    // only to answer "does this template put c:rest anywhere", once per template load.
    private volatile AttributeName restAttributeName = null;

    // The standard dialect's prefix is configurable, so th:text/th:utext can only be resolved once we
    // have an IEngineConfiguration in hand; cached the same way as slotAttributeName above.
    private volatile StandardAttributes standardTextAttributes = null;

    // The c:_caller wrapper put around every piece of slot content, built once instead of per slot fill.
    private volatile SyntheticTags syntheticTags = null;

    /**
     * Events between slot markers; {@code segments.length == slotMarkerNames.length + 1}.
     * {@code fallbacks[i]} holds marker {@code i}'s fallback content, parsed the very same way as the
     * top-level fragment — so a {@code c:slot} marker nested inside a fallback is found too, recursively —
     * when marker {@code i} was written in paired form ({@code <c:slot>...</c:slot>}); {@code null} when it
     * was self-closing.
     *
     * <p>{@code declaredSlotNames} is the union of {@code slotMarkerNames} and every nested fragment's own
     * {@code declaredSlotNames}, collected once here so it costs nothing per render — the same reason the
     * segments are pre-split at all. A name declared only inside a fallback is still something a caller may
     * target directly by name; the nesting is invisible at the call site.
     */
    private record FragmentInfo(IModel[] segments, String[] slotMarkerNames, FragmentInfo[] fallbacks,
                                Set<String> declaredSlotNames) {

        static FragmentInfo of(IModel[] segments, String[] slotMarkerNames, FragmentInfo[] fallbacks) {
            Set<String> declared = new HashSet<>(List.of(slotMarkerNames));
            for (FragmentInfo fallback : fallbacks) {
                if (fallback != null) {
                    declared.addAll(fallback.declaredSlotNames());
                }
            }
            return new FragmentInfo(segments, slotMarkerNames, fallbacks, Set.copyOf(declared));
        }
    }

    /**
     * Everything a component tag needs that is derived once and then reused across renders: the descriptor
     * it resolved to, the parsed fragment and the constructor handle for its component class. All are cheap
     * to rebuild, so they share one cache entry and one validity, rather than each keeping a separate
     * lazily-built field. Holding the descriptor here is what lets a cache hit skip the registry lookup
     * entirely — the cache is keyed by tag name and this instance only sees its own prefix, so a hit has
     * already answered the same question {@link ComponentRegistry#descriptorFor} would.
     */
    private record CachedFragment(ComponentDescriptor descriptor, FragmentInfo info,
                                  Function<CompositionComponentContext, CompositionComponent> componentFactory,
                                  ICacheEntryValidity validity, boolean hasRest) {
    }

    /** The standard dialect's prefix and the names of its own that this processor handles specially. */
    private record StandardAttributes(String prefix, AttributeName text, AttributeName utext,
                                      String textCompleteName, String utextCompleteName, String blockTagName) {
    }

    /**
     * The tags this processor synthesizes rather than reads out of a template: the {@code c:_caller} wrapper
     * that goes around every piece of slot content, and the bare {@code th:block} the {@code th:text}
     * shorthand becomes. Template events are immutable, so one instance of each can be handed to every
     * render — worth doing because building one resolves its name through the engine's element-definition
     * repository, whose case-insensitive binary search was the hottest single frame under this processor.
     *
     * <p>Held against the configuration it was built from, rather than in a bare field like the interned
     * names above: an element definition carries the processors matched to it, so a dialect instance added
     * to two engines must not hand one engine's definitions to the other. {@code block*} is {@code null}
     * when the standard dialect isn't configured, exactly as {@link StandardAttributes} is.
     */
    private record SyntheticTags(IEngineConfiguration configuration, IOpenElementTag callerOpen,
                                 ICloseElementTag callerClose, IOpenElementTag blockOpen,
                                 ICloseElementTag blockClose) {
    }

    public CompositionElementModelProcessor(String dialectPrefix, ComponentRegistry registry) {
        super(TemplateMode.HTML, PRECEDENCE);
        this.dialectPrefix = dialectPrefix;
        this.registry = registry;
        this.matchingElementName = MatchingElementName.forAllElementsWithPrefix(TemplateMode.HTML, dialectPrefix);
        this.callerTagName = dialectPrefix + ":" + CompositionCallerProcessor.TAG_NAME;
    }

    @Override
    public MatchingElementName getMatchingElementName() {
        return matchingElementName;
    }

    @Override
    public MatchingAttributeName getMatchingAttributeName() {
        return null;
    }

    /**
     * Mirrors {@link org.thymeleaf.processor.element.AbstractElementModelProcessor#process}, which this
     * class cannot extend (see the class javadoc): decorates a failure with the template name/line/col of
     * the tag being processed, when the exception does not already carry them.
     */
    @Override
    public void process(ITemplateContext context, IModel tag, IElementModelStructureHandler structureHandler) {
        ITemplateEvent firstEvent = tag.get(0);
        try {
            doProcess(context, tag, structureHandler);
        } catch (TemplateProcessingException e) {
            String templateName = firstEvent.getTemplateName();
            if (templateName != null && !e.hasTemplateName()) {
                e.setTemplateName(templateName);
            }
            if (firstEvent.getLine() != -1 && firstEvent.getCol() != -1 && !e.hasLineAndCol()) {
                e.setLineAndCol(firstEvent.getLine(), firstEvent.getCol());
            }
            throw e;
        } catch (Exception e) {
            throw new TemplateProcessingException("Error during execution of processor '" + getClass().getName() + "'",
                    firstEvent.getTemplateName(), firstEvent.getLine(), firstEvent.getCol(), e);
        }
    }

    private void doProcess(ITemplateContext context, IModel tag, IElementModelStructureHandler structureHandler) {
        IModelFactory modelFactory = context.getModelFactory();
        IProcessableElementTag rootElement = (IProcessableElementTag) tag.get(0);
        String tagName = rootElement.getElementDefinition().getElementName().getElementName();

        CachedFragment cached = getOrLoadFragment(context, rootElement, tagName);
        ComponentDescriptor descriptor = cached.descriptor();
        FragmentInfo fragment = cached.info();
        StandardAttributes textAttrs = standardTextAttributes(context.getConfiguration());
        SyntheticTags synthetic = syntheticTags(context.getConfiguration(), modelFactory, textAttrs);

        Map<String, IModel> slots = extractSlots(tag, modelFactory, slotAttributeName(context.getConfiguration()));
        removeBlankDefaultSlot(slots);
        IModel textShorthand = textShorthandSlotContent(rootElement, textAttrs, synthetic, modelFactory);
        if (textShorthand != null) {
            // th:text/th:utext silently wins over any explicit default-slot children, mirroring th:text's
            // own "replaces whatever body was there" behavior on a hand-written element.
            slots.put(CompositionComponent.DEFAULT_SLOT, textShorthand);
        }
        // After the blank-default removal and the th:text shorthand above, so that indentation-only bodies
        // stay exempt and th:text is checked like the slot content it becomes.
        requireOnlyDeclaredSlots(slots.keySet(), fragment, descriptor);
        ExtractedAttrs extracted = extractAttrs(rootElement, context, textAttrs, descriptor);
        Map<String, Object> attrs = extracted.attrs();
        Map<String, String> appends = extracted.appends();
        if (!appends.isEmpty() && !cached.hasRest()) {
            requireRestForAppends(appends, textAttrs, descriptor);
        }

        CompositionComponentContext componentContext = new CompositionComponentContext(
                new ComponentAttributes(attrs, descriptor.props(), appends),
                slots.keySet(),
                context.getLocale(),
                (code, params) -> context.getMessage(CompositionElementModelProcessor.class, code, params, false),
                context::getVariable,
                structureHandler::setLocalVariable);

        CompositionComponent componentInstance;
        try {
            componentInstance = cached.componentFactory().apply(componentContext);
        } catch (Exception e) {
            throw new TemplateProcessingException(CompositionDialect.DIALECT_NAME
                    + ": Could not instantiate component \"" + tagName + "\" (" + describeImpl(descriptor) + ")", e);
        }
        structureHandler.setLocalVariable(COMPONENT_VARIABLE, componentInstance);
        // Read before the line above takes effect, so this is the frame the tag was written in.
        structureHandler.setLocalVariable(ComponentFrame.VARIABLE, new ComponentFrame(componentInstance,
                context.getVariable(ComponentFrame.VARIABLE) instanceof ComponentFrame frame ? frame : null));

        tag.reset();
        renderFragmentInto(tag, fragment, slots, synthetic);
    }

    private TemplateProcessingException unresolvedTag(IProcessableElementTag rootElement, String tagName) {
        return new TemplateProcessingException(CompositionDialect.DIALECT_NAME
                + ": <" + rootElement.getElementCompleteName() + "> is not a registered component tag — "
                + registry.describeUnresolvedTag(tagName) + ".");
    }

    private static String describeImpl(ComponentDescriptor descriptor) {
        return descriptor.componentClass().map(Class::getName).orElse("classless — no impl bound");
    }

    private CachedFragment getOrLoadFragment(ITemplateContext context, IProcessableElementTag rootElement,
                                             String tagName) {
        CachedFragment cached = fragmentCache.get(tagName);
        if (cached != null && cached.validity().isCacheStillValid()) {
            return cached;
        }
        ComponentDescriptor descriptor = registry.descriptorFor(dialectPrefix, tagName);
        if (descriptor == null) {
            throw unresolvedTag(rootElement, tagName);
        }

        TemplateModel templateModel;
        try {
            templateModel = context.getConfiguration().getTemplateManager()
                    .parseStandalone(context, descriptor.templatePath(), (Set<String>) null, (TemplateMode) null, true, true);
        } catch (Exception e) {
            throw new TemplateProcessingException(CompositionDialect.DIALECT_NAME
                    + ": Could not load template for component \"" + tagName + "\" from \"" + descriptor.templatePath()
                    + ".html\" (relative to thymeleaf templates path)");
        }

        IModelFactory modelFactory = context.getModelFactory();
        ElementName slotElement = slotElementName(context.getConfiguration());
        ElementName componentElement = componentElementName(context.getConfiguration());
        int[] declarationCount = new int[1];
        FragmentInfo info = parseFragment(templateModel, 1, templateModel.size() - 1, modelFactory, slotElement,
                componentElement, descriptor, declarationCount);
        requireExactlyOneDeclaration(declarationCount[0], descriptor);
        Function<CompositionComponentContext, CompositionComponent> componentFactory = createComponentFactory(descriptor);

        // Cache only cacheable templates and re-check validity per hit (dev-mode edits, TTL expiry).
        // Not flushed by TemplateEngine.clearTemplateCaches().
        ICacheEntryValidity validity = templateModel.getTemplateData().getValidity();
        boolean hasRest = hasRest(templateModel, context.getConfiguration());
        CachedFragment fresh = new CachedFragment(descriptor, info, componentFactory, validity, hasRest);
        if (validity.isCacheable()) {
            // Plain put, not computeIfAbsent: the mapping function would hold a bin lock across a template
            // parse. A race duplicates this work harmlessly; both threads put the same equivalent entry.
            fragmentCache.put(tagName, fresh);
        }
        return fresh;
    }

    /**
     * Pre-splits the events in {@code [start, end)} around the slot markers directly in them, so rendering
     * bulk-copies each segment via addModel. Recurses into a paired marker's own fallback range, exactly the
     * same way, so a marker nested inside another marker's fallback content is found and split out too —
     * with no notion of "nesting depth" required, since each recursive call only ever looks at its own range.
     *
     * <p>A {@code <c:component>} declaration — self-closing, or paired with its {@code <c:prop>} children —
     * is recognized the same way, anywhere in this same range, and stripped out entirely (added to no
     * segment) rather than split around — the render path does not need what it says, since {@link
     * ComponentRegistry} already resolved it at startup, but it must not appear literally in the rendered
     * output either. The paired form's children are never inspected here: they were already validated
     * once, when the declaration was first read, and nothing in them is markup to preserve. {@code
     * declarationCount} accumulates across every recursive call so {@link #getOrLoadFragment} can re-check
     * "exactly one" on every load, the same dev-mode-consistency reasoning as {@link
     * #requireOnlyDeclaredSlots}.
     */
    private FragmentInfo parseFragment(TemplateModel templateModel, int start, int end, IModelFactory modelFactory,
                                       ElementName slotElement, ElementName componentElement,
                                       ComponentDescriptor descriptor, int[] declarationCount) {
        List<IModel> segments = new ArrayList<>();
        List<String> names = new ArrayList<>();
        List<FragmentInfo> fallbacks = new ArrayList<>();

        IModel currentSegment = modelFactory.createModel();
        for (int i = start; i < end; i++) {
            ITemplateEvent event = templateModel.get(i);
            if (isComponentElement(event, componentElement)) {
                declarationCount[0]++;
                if (event instanceof IOpenElementTag) {
                    // The paired form: skip straight past its own close tag, its <c:prop> children never
                    // reaching the segment at all.
                    i = matchingCloseIndex(templateModel, i, end, descriptor);
                }
                // Swallow one immediately-following whitespace-only text run too, so a declaration
                // written on its own line — the documented, recommended style — leaves no stray leading
                // newline in the component's own markup. Without this, that newline becomes a literal
                // prefix of the fragment's first segment, and nesting the component inside itself (or
                // inside anything) turns it into a visible line break exactly where the caller's content
                // was spliced in.
                if (i + 1 < end && templateModel.get(i + 1) instanceof IText text && text.getText().isBlank()) {
                    i++;
                }
                continue;
            }
            if (!isSlotMarker(event, slotElement)) {
                currentSegment.add(event);
                continue;
            }
            requireOnlyNameOnMarker((IProcessableElementTag) event, descriptor);
            segments.add(currentSegment);
            names.add(markerSlotName(event));
            if (event instanceof IStandaloneElementTag) {
                fallbacks.add(null);
            } else {
                int closeAt = matchingCloseIndex(templateModel, i, end, descriptor);
                fallbacks.add(parseFragment(templateModel, i + 1, closeAt, modelFactory, slotElement,
                        componentElement, descriptor, declarationCount));
                i = closeAt;
            }
            currentSegment = modelFactory.createModel();
        }
        segments.add(currentSegment);

        return FragmentInfo.of(segments.toArray(new IModel[0]), names.toArray(new String[0]),
                fallbacks.toArray(new FragmentInfo[0]));
    }

    /**
     * Whether {@code event} is a {@code <c:component>} declaration's own opening event — standalone for
     * the self-closing form, open for the paired one. Never matches its close tag: the loop in {@link
     * #parseFragment} jumps straight past that via {@link #matchingCloseIndex} instead of evaluating it.
     */
    private boolean isComponentElement(ITemplateEvent event, ElementName componentElement) {
        return (event instanceof IStandaloneElementTag || event instanceof IOpenElementTag)
                && ((IElementTag) event).getElementDefinition().getElementName().equals(componentElement);
    }

    /**
     * Re-checked on every fragment load — mirrors what {@link ComponentRegistry} already verified once at
     * startup, so a dev-mode edit that removes or duplicates the declaration after the engine has already
     * registered this tag is caught on the next render rather than silently rendering stale behaviour.
     */
    private void requireExactlyOneDeclaration(int declarationCount, ComponentDescriptor descriptor) {
        if (declarationCount == 1) {
            return;
        }
        String what = declarationCount == 0 ? "no longer declares" : "declares more than one";
        throw new TemplateProcessingException(CompositionDialect.DIALECT_NAME + ": " + descriptor.templatePath()
                + ".html " + what + " <" + dialectPrefix + ":component> — exactly one is required.");
    }

    /**
     * Index of the close tag pairing with the open marker at {@code openAt}, found by tracking generic
     * open/close nesting depth from 1 — the same balanced-bracket technique as any other tag-matching,
     * regardless of what the nested markup is. This includes another {@code c:slot} marker: its open tag
     * increments depth and its own close tag decrements it, exactly like any other nested element, so a
     * marker nested inside this one is skipped over rather than treated specially here — {@link
     * #parseFragment} is what finds it, in its own recursive call over this marker's fallback range.
     * Standalone tags in between, including a self-closing nested marker, don't affect depth.
     */
    private int matchingCloseIndex(TemplateModel templateModel, int openAt, int end, ComponentDescriptor descriptor) {
        int depth = 1;
        for (int j = openAt + 1; j < end; j++) {
            ITemplateEvent event = templateModel.get(j);
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
                + ": an opened <" + dialectPrefix + ":slot> or <" + dialectPrefix + ":component> in component \""
                + descriptor.tagName() + "\" (" + descriptor.templatePath() + ".html) is never closed.");
    }

    /** Whether {@code event} is a {@code c:slot} marker's own open or standalone tag — never its close tag. */
    private boolean isSlotMarker(ITemplateEvent event, ElementName slotElement) {
        return (event instanceof IStandaloneElementTag || event instanceof IOpenElementTag)
                && ((IElementTag) event).getElementDefinition().getElementName().equals(slotElement);
    }

    private String markerSlotName(ITemplateEvent event) {
        String name = ((IProcessableElementTag) event).getAttributeValue(MARKER_NAME_ATTRIBUTE);
        return name != null ? name : CompositionComponent.DEFAULT_SLOT;
    }

    /**
     * Rejects every attribute on a marker but its {@code name}. A marker's own tag is stripped out here,
     * while the fragment is parsed, and never handed back to the engine — so anything else written on it
     * has no effect whatsoever, and silently so. That is worst for control flow: {@code th:if} on a marker
     * reads as if it gates the slot, and instead the content splices in unconditionally, which for the
     * usual branch-per-marker shape means the caller's content quietly rendered twice.
     *
     * <p>The marker's name under this dialect's own prefix ({@code c:name}) gets its own message instead of
     * the generic one, because it is the natural wrong guess: every other attribute in the dialect's
     * grammar — {@code c:slot} at a call site, {@code c:rest} — is prefixed, and those ride on the
     * caller's own elements where they have to be. Only on the dialect's own elements is there nothing to
     * collide with, which is why the marker's {@code name} and {@code <c:prop name>} are bare. Telling
     * someone who wrote {@code c:name} to put it on a wrapping element would be no help at all.
     */
    private void requireOnlyNameOnMarker(IProcessableElementTag marker, ComponentDescriptor descriptor) {
        for (var attr : marker.getAllAttributes()) {
            AttributeName attributeName = attr.getAttributeDefinition().getAttributeName();
            String plainName = attributeName.getAttributeName();
            if (attributeName.getPrefix() == null && MARKER_NAME_ATTRIBUTE.equalsIgnoreCase(plainName)) {
                continue;
            }
            String written = attr.getAttributeCompleteName();
            // Only under this dialect's prefix: th:name is someone reaching for a computed slot name,
            // which is a different mistake and not one "spell it unprefixed" would fix.
            boolean isPrefixedName = dialectPrefix.equals(attributeName.getPrefix())
                    && MARKER_NAME_ATTRIBUTE.equalsIgnoreCase(plainName);
            throw new TemplateProcessingException(CompositionDialect.DIALECT_NAME
                    + ": <" + dialectPrefix + ":slot> in component \"" + descriptor.tagName() + "\" ("
                    + descriptor.templatePath() + ".html) carries " + written + ". A slot marker takes "
                    + "only " + MARKER_NAME_ATTRIBUTE + ", and everything else on it would be dropped "
                    + "without a trace — the marker's own tag never reaches the rendered output. "
                    + (isPrefixedName
                            ? "Spell the slot name unprefixed: " + MARKER_NAME_ATTRIBUTE + "=\"…\"."
                            : "Put it on a wrapping element instead, or on a <th:block> around the "
                                    + "marker if there is no element to carry it."));
        }
    }

    /**
     * Appends the component's segments with the caller's slot content spliced in between them, each piece
     * of content wrapped in a marker that steps back out of this component — the content was written
     * outside it and must keep reading the {@code this} that was in effect there. A slot the caller left
     * empty falls back to its own fallback content, if it has any — rendered by recursing into it the same
     * way, since a fallback may itself contain another marker; unwrapped either way, since a fallback is
     * the component's own markup and must keep reading this component's {@code this}, not step back out.
     *
     * <p>Content goes to <em>every</em> marker of its name, not only to the first as native {@code <slot>}
     * resolves it. Native slots are matched against a live tree, where a slot either exists or does not;
     * markers here are found while the fragment is parsed, before any {@code th:if} on the markup around
     * them has run. Filling only the first would mean betting on a marker that may not render at all — and
     * it is the branch-per-marker shape, one {@code th:if} per heading level, that repeats a name in the
     * first place. Two markers that really do both render duplicate the content, which is visible in the
     * output; the bet lost silently.
     */
    private void renderFragmentInto(IModel target, FragmentInfo fragment, Map<String, IModel> slots,
                                    SyntheticTags synthetic) {
        IModel[] segments = fragment.segments();
        String[] slotNames = fragment.slotMarkerNames();
        FragmentInfo[] fallbacks = fragment.fallbacks();

        target.addModel(segments[0]);
        for (int i = 0; i < slotNames.length; i++) {
            IModel content = slots.get(slotNames[i]);
            if (content != null) {
                boolean wrap = !readsNothing(content);
                if (wrap) {
                    target.add(synthetic.callerOpen());
                }
                target.addModel(content);
                if (wrap) {
                    target.add(synthetic.callerClose());
                }
            } else if (fallbacks[i] != null) {
                renderFragmentInto(target, fallbacks[i], slots, synthetic);
            }
            target.addModel(segments[i + 1]);
        }
    }

    /**
     * Whether {@code content} cannot read {@code this} at all, in which case the {@code c:_caller} wrapper
     * around it has nothing to put back and is left off. That holds when every event is text carrying no
     * inlined expression: there is no element for a processor to claim, and with no {@code [[} or
     * {@code [(} in the text there is nothing for the standard dialect's inlining to evaluate either.
     *
     * <p>Checked because a component handed a plain string body — {@code <c:button>Save</c:button>} — is
     * the most ordinary call there is, and the wrapper costs it two synthetic tags plus a full processor
     * dispatch through {@link CompositionCallerProcessor} for a frame nothing goes on to read. Anything
     * this cannot prove inert (an element, a comment, a CDATA section) is wrapped exactly as before.
     */
    private static boolean readsNothing(IModel content) {
        for (int i = 0, size = content.size(); i < size; i++) {
            if (!(content.get(i) instanceof IText event)) {
                return false;
            }
            String text = event.getText();
            if (text.indexOf("[[") >= 0 || text.indexOf("[(") >= 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Rejects content the component has nowhere to put — a misspelled {@code c:slot} name at the call site,
     * or a body given to a component that declares no default slot. Such content is not rendered anywhere,
     * and silently dropping it makes the mistake indistinguishable from having passed nothing at all.
     *
     * <p>A marker under a {@code th:if} still counts as declared: markers are split out when the fragment
     * is parsed, before any conditional processing, so a component may still decide per render whether to
     * render the slot it declares — which is what {@code hasSlot} is for.
     */
    private void requireOnlyDeclaredSlots(Set<String> given, FragmentInfo fragment, ComponentDescriptor descriptor) {
        Set<String> declared = fragment.declaredSlotNames();
        if (declared.containsAll(given)) {
            return;
        }
        throw new TemplateProcessingException(CompositionDialect.DIALECT_NAME
                + ": <" + dialectPrefix + ":" + descriptor.tagName() + "> was given content it declares no slot for: "
                + describeSlots(given.stream().filter(name -> !declared.contains(name))) + ". "
                // No slots at all usually means the marker itself is wrong rather than the call site, and
                // the marker never reaches the processor that would say so — it is consumed while the
                // fragment loads, or not recognized as a marker in the first place.
                + (declared.isEmpty()
                        ? "It declares no slots at all — check that its own slot markers are spelled correctly"
                        : "It declares " + describeSlots(declared.stream()))
                + " (" + descriptor.templatePath() + ".html).");
    }

    private static String describeSlots(Stream<String> names) {
        return names.map(name -> name.isEmpty() ? "a default slot" : "slot \"" + name + "\"")
                .sorted()
                .collect(Collectors.joining(", "));
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
    private IModel textShorthandSlotContent(IProcessableElementTag rootElement, StandardAttributes textAttrs,
                                            SyntheticTags synthetic, IModelFactory modelFactory) {
        if (textAttrs == null) {
            return null;
        }
        // Read straight, without asking hasAttribute first: an absent attribute and one written with no
        // value both read as null, and both mean "no shorthand here" — so the second lookup only ever
        // confirmed what this one already says, at the price of a second search through the tag.
        String text = rootElement.getAttributeValue(textAttrs.text());
        String utext = rootElement.getAttributeValue(textAttrs.utext());
        if (text == null && utext == null) {
            return null;
        }
        // Both present at once: don't validate ourselves, copy both onto the synthetic tag and let the
        // standard dialect's own precedence resolve it, exactly as it would on a hand-written element.
        IOpenElementTag block = synthetic.blockOpen();
        if (text != null) {
            block = modelFactory.setAttribute(block, textAttrs.textCompleteName(), text);
        }
        if (utext != null) {
            block = modelFactory.setAttribute(block, textAttrs.utextCompleteName(), utext);
        }
        IModel content = modelFactory.createModel();
        content.add(block);
        content.add(synthetic.blockClose());
        return content;
    }

    /**
     * Rejects the {@code th:} attributes whose meaning a component tag cannot carry. Everything else under
     * the standard prefix is evaluated and handed over as the prop of that name — which is exactly what the
     * standard dialect's own default attribute processor would have done with it, so {@code th:title} on a
     * component means the same "evaluate this and call it title" it means anywhere else.
     *
     * <p>The ones listed here cannot: their value is not a single expression ({@code th:attr},
     * {@code th:with}, {@code th:remove}, {@code th:inline}, {@code th:fragment}), or it modifies rather
     * than sets and has no single fixed target attribute to modify ({@code th:attrappend},
     * {@code th:attrprepend} — unlike {@code th:classappend}/{@code th:styleappend}, which merge instead;
     * see {@link #APPEND_MERGE_TARGETS}), or it scopes a subtree that no longer exists by the time a
     * component has replaced its own tag ({@code th:object}), or it needs form binding this never sees
     * ({@code th:field}). Note that control flow is absent deliberately: {@code th:if}, {@code th:unless},
     * {@code th:each}, {@code th:switch} and {@code th:case} all run at lower precedences than
     * {@link #PRECEDENCE} and are consumed before this processor ever sees the tag, which is what
     * {@code StandardDialectContractTest} pins.
     */
    static final Set<String> UNSUPPORTED_STANDARD_ATTRIBUTES = Set.of(
            "attr", "attrappend", "attrprepend",
            "with", "object",
            "alt-title", "lang-xmllang",
            "fragment", "remove", "inline", "assert", "ref",
            "field", "errors", "errorclass");

    /**
     * {@code th:classappend}/{@code th:styleappend} on a component tag: unlike the attributes rejected
     * above, these have one single, well-known target attribute, so — unlike {@code th:attrappend}/
     * {@code th:attrprepend}, which could name any attribute and are rejected outright — they are captured
     * here instead, evaluated in the caller's own scope, and merged by {@link CompositionRestAttributesTagProcessor}
     * onto whichever element the component's template puts {@code c:rest} on.
     */
    static final Map<String, String> APPEND_MERGE_TARGETS = Map.of(
            "classappend", "class",
            "styleappend", "style");

    private void requireSupportedStandardAttribute(String plainAttributeName, StandardAttributes standard,
                                                    ComponentDescriptor descriptor) {
        if (!UNSUPPORTED_STANDARD_ATTRIBUTES.contains(plainAttributeName)) {
            return;
        }
        throw new TemplateProcessingException(CompositionDialect.DIALECT_NAME + ": "
                + standard.prefix() + ":" + plainAttributeName + " cannot be used on the <" + dialectPrefix
                + ":" + descriptor.tagName() + "> tag. A component tag is replaced by the component's own markup "
                + "before that attribute would run, so the only thing it could be is a prop — and its value "
                + "is not one. Put it on an element inside " + descriptor.templatePath() + ".html instead, or pass a "
                + "value with " + dialectPrefix + ": and let the component's template apply it.");
    }

    /**
     * Rejects a caller's {@code th:classappend}/{@code th:styleappend} when the component's template has no
     * {@code c:rest} anywhere for it to merge onto — rather than silently doing nothing, the same stance
     * {@code c:rest} itself takes on misplacement (see {@link CompositionRestAttributesTagProcessor}).
     */
    private void requireRestForAppends(Map<String, String> appends, StandardAttributes standard,
                                       ComponentDescriptor descriptor) {
        String prefix = standard != null ? standard.prefix() : "th";
        String names = APPEND_MERGE_TARGETS.entrySet().stream()
                .filter(entry -> appends.containsKey(entry.getValue()))
                .map(entry -> prefix + ":" + entry.getKey())
                .collect(Collectors.joining("/"));
        throw new TemplateProcessingException(CompositionDialect.DIALECT_NAME + ": "
                + names + " was passed to <" + dialectPrefix + ":" + descriptor.tagName() + ">, but "
                + descriptor.templatePath() + ".html has no " + dialectPrefix + ":rest for it to merge onto. "
                + "Add " + dialectPrefix + ":rest to the element it should land on, inside "
                + descriptor.templatePath() + ".html.");
    }

    /**
     * Rejects any {@code c:}-prefixed attribute on a component's call-site tag. {@code c:} is reserved for
     * the dialect's own grammar ({@code c:slot}, {@code c:rest}) — never a channel for
     * props, declared or not. A prop is set with a plain attribute, or {@code th:} for an expression.
     */
    private void rejectDialectPrefixedAttribute(String plainAttributeName, StandardAttributes textAttrs,
                                                ComponentDescriptor descriptor) {
        String standardPrefix = textAttrs != null ? textAttrs.prefix() : "th";
        throw new TemplateProcessingException(CompositionDialect.DIALECT_NAME + ": "
                + dialectPrefix + ":" + plainAttributeName + " was used on <" + dialectPrefix + ":"
                + descriptor.tagName() + ">. " + dialectPrefix + ": is reserved for the dialect's own grammar ("
                + dialectPrefix + ":slot, " + dialectPrefix + ":rest) — declare props "
                + "on the component and pass them with a plain attribute or " + standardPrefix + ":"
                + plainAttributeName + " instead.");
    }

    /** {@link #extractAttrs}'s two results: the ordinary prop/rest map, and the caller's evaluated
     * {@code th:classappend}/{@code th:styleappend} contributions, keyed by target attribute name. */
    private record ExtractedAttrs(Map<String, Object> attrs, Map<String, String> appends) {
    }

    private ExtractedAttrs extractAttrs(IProcessableElementTag rootElement, ITemplateContext context,
                                        StandardAttributes textAttrs, ComponentDescriptor descriptor) {
        var allAttributes = rootElement.getAllAttributes();
        Map<String, Object> attrs = HashMap.newHashMap(allAttributes.length);
        Map<String, String> appends = Map.of();
        // Resolved on the first standard-prefixed attribute rather than up front: most component tags carry
        // none at all, and then there is nothing here to parse an expression for.
        IStandardExpressionParser expressionParser = null;
        for (var attr : allAttributes) {
            AttributeName attributeName = attr.getAttributeDefinition().getAttributeName();
            // Already relocated onto the synthetic th:block by textShorthandSlotContent; skip so they
            // don't also leak into restAttributes/c:rest as unevaluated raw strings.
            if (textAttrs != null && (attributeName.equals(textAttrs.text()) || attributeName.equals(textAttrs.utext()))) {
                continue;
            }
            String plainAttributeName = attributeName.getAttributeName();
            String prefix = attributeName.getPrefix();
            if (dialectPrefix.equals(prefix)) {
                rejectDialectPrefixedAttribute(plainAttributeName, textAttrs, descriptor);
            } else if (textAttrs != null && textAttrs.prefix().equals(prefix)) {
                String mergeTarget = APPEND_MERGE_TARGETS.get(plainAttributeName);
                if (mergeTarget != null) {
                    if (attr.getValue() != null) {
                        if (expressionParser == null) {
                            expressionParser = StandardExpressions.getExpressionParser(context.getConfiguration());
                        }
                        Object result = expressionParser.parseExpression(context, attr.getValue()).execute(context);
                        if (result != null && !result.toString().isEmpty()) {
                            if (appends.isEmpty()) {
                                appends = new HashMap<>();
                            }
                            appends.put(mergeTarget, result.toString());
                        }
                    }
                    continue;
                }
                requireSupportedStandardAttribute(plainAttributeName, textAttrs, descriptor);
                if (attr.getValue() == null) {
                    attrs.put(plainAttributeName, null);
                    continue;
                }
                if (expressionParser == null) {
                    expressionParser = StandardExpressions.getExpressionParser(context.getConfiguration());
                }
                attrs.put(plainAttributeName,
                        expressionParser.parseExpression(context, attr.getValue()).execute(context));
            } else {
                attrs.put(plainAttributeName, attr.getValue());
            }
        }
        return new ExtractedAttrs(attrs, appends);
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

    private ElementName componentElementName(IEngineConfiguration configuration) {
        ElementName name = componentElementName;
        if (name == null) {
            // Benign race, as with slotAttributeName above: every thread resolves the same interned instance.
            name = configuration.getElementDefinitions()
                    .forName(getTemplateMode(), dialectPrefix, "component").getElementName();
            componentElementName = name;
        }
        return name;
    }

    private AttributeName restAttributeName(IEngineConfiguration configuration) {
        AttributeName name = restAttributeName;
        if (name == null) {
            // Benign race, as with slotAttributeName above: every thread resolves the same interned instance.
            name = configuration.getAttributeDefinitions()
                    .forName(getTemplateMode(), dialectPrefix, "rest").getAttributeName();
            restAttributeName = name;
        }
        return name;
    }

    /**
     * Whether {@code c:rest} appears anywhere in {@code [1, templateModel.size() - 1)} — the same range
     * {@link #parseFragment} works over. Computed once per template load so {@link #doProcess} can reject a
     * caller's {@code th:classappend}/{@code th:styleappend} immediately when there is nowhere for it to
     * land, rather than silently doing nothing: a flat scan is enough, since fallback content lives
     * contiguously in this same range even though {@code parseFragment} recurses into it separately.
     */
    private boolean hasRest(TemplateModel templateModel, IEngineConfiguration configuration) {
        AttributeName restName = restAttributeName(configuration);
        for (int i = 1; i < templateModel.size() - 1; i++) {
            if (templateModel.get(i) instanceof IProcessableElementTag tag && tag.hasAttribute(restName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Builds the synthetic tags for {@code configuration}, or returns the ones already built for it. See
     * {@link SyntheticTags} for why this is cached and why it is keyed on the configuration.
     */
    private SyntheticTags syntheticTags(IEngineConfiguration configuration, IModelFactory modelFactory,
                                        StandardAttributes textAttrs) {
        SyntheticTags cached = syntheticTags;
        if (cached != null && cached.configuration() == configuration) {
            return cached;
        }
        SyntheticTags resolved = new SyntheticTags(configuration,
                modelFactory.createOpenElementTag(callerTagName),
                modelFactory.createCloseElementTag(callerTagName),
                textAttrs == null ? null : modelFactory.createOpenElementTag(textAttrs.blockTagName()),
                textAttrs == null ? null : modelFactory.createCloseElementTag(textAttrs.blockTagName()));
        syntheticTags = resolved;
        return resolved;
    }

    /**
     * Resolves {@code th:text}/{@code th:utext} under whatever prefix the standard dialect is actually
     * configured with, rather than hardcoding {@code "th"}. {@code null} when the standard dialect isn't
     * present at all.
     */
    private StandardAttributes standardTextAttributes(IEngineConfiguration configuration) {
        StandardAttributes cached = standardTextAttributes;
        if (cached != null) {
            return cached;
        }
        String standardPrefix = configuration.getStandardDialectPrefix();
        if (standardPrefix == null) {
            return null;
        }
        // Benign race, as with slotAttributeName above: every thread resolves the same interned instances.
        var attributeDefinitions = configuration.getAttributeDefinitions();
        StandardAttributes resolved = new StandardAttributes(
                standardPrefix,
                attributeDefinitions.forName(getTemplateMode(), standardPrefix, "text").getAttributeName(),
                attributeDefinitions.forName(getTemplateMode(), standardPrefix, "utext").getAttributeName(),
                standardPrefix + ":text",
                standardPrefix + ":utext",
                standardPrefix + ":block");
        standardTextAttributes = resolved;
        return resolved;
    }

    /**
     * Dispatches on whether the descriptor bound a class: a class-bound component is built by reflection,
     * exactly as before; a classless one gets a built-in {@link AnonymousComponent} whose backing map holds
     * only the declared props, camel-keyed (see {@link ComponentRegistry#camelCase}) since the read site for
     * a prop is always camel regardless of which mode built {@code this}.
     */
    private static Function<CompositionComponentContext, CompositionComponent> createComponentFactory(
            ComponentDescriptor descriptor) {
        return descriptor.componentClass()
                .<Function<CompositionComponentContext, CompositionComponent>>map(
                        componentClass -> createClassComponentFactory(componentClass, descriptor.tagName()))
                .orElseGet(() -> createClasslessComponentFactory(descriptor.classlessPropSpecs(), descriptor.tagName()));
    }

    /**
     * Per prop: the caller's value if given, else the declared literal default if any, else {@code null}
     * unless {@code required} says otherwise — checked in that order, so a default makes {@code required}
     * unreachable rather than conflicting with it. Coerced through {@link PropCoercion} only when a
     * {@code type} was declared; with none, the value is whatever the caller's attribute evaluated to,
     * exactly like an attribute with no matching class field today.
     */
    private static Function<CompositionComponentContext, CompositionComponent> createClasslessComponentFactory(
            List<PropSpec> propSpecs, String tagName) {
        // Camel names resolved once here, for the same reason the record binder resolves its attribute
        // names once: a prop's read name is fixed by its declaration, and only its value changes between
        // occurrences.
        PropSpec[] specs = propSpecs.toArray(new PropSpec[0]);
        String[] camelNames = new String[specs.length];
        for (int i = 0; i < specs.length; i++) {
            camelNames[i] = ComponentRegistry.camelCase(specs[i].kebabName());
        }
        return componentContext -> {
            Map<String, Object> camelKeyed = HashMap.newHashMap(specs.length);
            for (int i = 0; i < specs.length; i++) {
                camelKeyed.put(camelNames[i], resolveClasslessProp(specs[i], componentContext, tagName));
            }
            return new AnonymousComponent(camelKeyed, componentContext);
        };
    }

    private static Object resolveClasslessProp(PropSpec spec, CompositionComponentContext componentContext, String tagName) {
        Object raw;
        if (componentContext.attributes().containsKey(spec.kebabName())) {
            raw = componentContext.attributes().get(spec.kebabName());
        } else if (spec.defaultValue() != null) {
            raw = spec.defaultValue();
        } else if (spec.required()) {
            throw new TemplateProcessingException(CompositionDialect.DIALECT_NAME + ": component \"" + tagName
                    + "\" requires \"" + spec.kebabName() + "\", which was not provided.");
        } else {
            raw = null;
        }
        return spec.type() != null ? PropCoercion.coerce(raw, spec.type(), tagName, spec.kebabName()) : raw;
    }

    /**
     * A component either declares a single-arg {@code (CompositionComponentContext)} constructor — every
     * class-based component, and a record whose only component is the context — or, for a record with
     * props of its own, is bound via its canonical constructor by {@link #createRecordPropsFactory}.
     */
    private static Function<CompositionComponentContext, CompositionComponent> createClassComponentFactory(
            Class<? extends CompositionComponent> componentClass, String elementName) {
        Constructor<? extends CompositionComponent> constructor;
        try {
            constructor = componentClass.getConstructor(CompositionComponentContext.class);
        } catch (NoSuchMethodException e) {
            if (componentClass.isRecord()) {
                return createRecordPropsFactory(componentClass, elementName);
            }
            throw new IllegalStateException(CompositionDialect.DIALECT_NAME + ": Component \"" + elementName + "\" ("
                    + componentClass.getName() + ") must declare a public constructor taking a single "
                    + CompositionComponentContext.class.getSimpleName() + " argument", e);
        }
        return createSingleArgFactory(constructor);
    }

    private static Function<CompositionComponentContext, CompositionComponent> createSingleArgFactory(
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

    /**
     * Binds a record's props straight off its canonical constructor: each record component names an
     * attribute — kebab-cased, or {@link Prop} to override — and is coerced to its declared type by
     * {@link PropCoercion}, except the one component (if any) typed {@link CompositionComponentContext},
     * which receives the context itself. Component order, attribute names and types are all resolved once
     * here rather than per render; only the coercion happens per render, since only attribute values change
     * between occurrences.
     */
    private static Function<CompositionComponentContext, CompositionComponent> createRecordPropsFactory(
            Class<? extends CompositionComponent> componentClass, String elementName) {
        RecordComponent[] components = componentClass.getRecordComponents();
        Class<?>[] types = new Class<?>[components.length];
        String[] attributeNames = new String[components.length];
        for (int i = 0; i < components.length; i++) {
            types[i] = components[i].getType();
            attributeNames[i] = CompositionComponentContext.class.isAssignableFrom(types[i])
                    ? null
                    : ComponentRegistry.propName(componentClass, components[i].getName());
        }

        Constructor<? extends CompositionComponent> canonical;
        try {
            canonical = componentClass.getDeclaredConstructor(types);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(CompositionDialect.DIALECT_NAME + ": Component \"" + elementName + "\" ("
                    + componentClass.getName() + ") has no canonical constructor matching its record components", e);
        }

        return componentContext -> {
            Object[] args = new Object[components.length];
            for (int i = 0; i < components.length; i++) {
                args[i] = attributeNames[i] == null
                        ? componentContext
                        : PropCoercion.coerce(componentContext.attributes().get(attributeNames[i]), types[i],
                                elementName, attributeNames[i]);
            }
            try {
                return canonical.newInstance(args);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        };
    }
}
