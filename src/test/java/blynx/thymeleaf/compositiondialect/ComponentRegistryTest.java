package blynx.thymeleaf.compositiondialect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import blynx.thymeleaf.compositiondialect.registryfixtures.props.AnnotatedProps;
import blynx.thymeleaf.compositiondialect.registryfixtures.props.RecordProps;
import blynx.thymeleaf.compositiondialect.testcomponents.Card;
import blynx.thymeleaf.compositiondialect.testcomponents.MagicHeadings;

class ComponentRegistryTest {

    private static final String PATH = "testcomponents";
    private static final String PACKAGE = "blynx.thymeleaf.compositiondialect.testcomponents";
    private static final String PROPS_PATH = "registryfixtures/props";
    private static final String PROPS_PACKAGE = "blynx.thymeleaf.compositiondialect.registryfixtures.props";
    private static final String COLLISION_ROOT = "registryfixtures/collision";
    private static final String RESERVED_PATH = "registryfixtures/reserved";
    private static final String BADIMPL_PATH = "registryfixtures/badimpl";
    private static final String CONFLICT_PATH = "registryfixtures/conflict";
    private static final String CLASSLESS_PATH = "registryfixtures/classless";

    private ComponentRegistry scan(String prefix) {
        return ComponentRegistry.scan(PATH, PACKAGE, prefix);
    }

    @Test
    void scanDiscoversEveryTestComponentSortedByTagName() {
        List<ComponentDescriptor> components = scan("c").components();

        List<String> tagNames = components.stream().map(ComponentDescriptor::tagName).toList();
        assertEquals(List.of("card", "heading", "label", "magic-headings", "plain", "wrapper"), tagNames);
    }

    @Test
    void descriptorCarriesPrefixTagNameAndTemplatePath() {
        ComponentDescriptor card = scan("c").findByTagName("c", "card").orElseThrow();

        assertEquals(Card.class, card.componentClass().orElseThrow());
        assertEquals("c", card.prefix());
        assertEquals("card", card.tagName());
        assertEquals("testcomponents/card", card.templatePath());
        assertEquals("c:card", card.qualifiedName());
        assertEquals(new ComponentSource(PATH, PACKAGE), card.source());
    }

    @Test
    void templatePathIncludesTagNameForEveryComponent() {
        for (ComponentDescriptor descriptor : scan("c").components()) {
            assertEquals("testcomponents/" + descriptor.tagName(), descriptor.templatePath());
        }
    }

    @Test
    void byPrefixGroupsAllComponentsUnderTheDialectPrefix() {
        ComponentRegistry registry = scan("c");

        assertEquals(List.of("c"), List.copyOf(registry.byPrefix().keySet()));
        assertEquals(6, registry.byPrefix().get("c").size());
    }

    @Test
    void byTagNameMapsEachTagToASingleComponentWithinOneDialect() {
        ComponentRegistry registry = scan("c");

        assertEquals(6, registry.byTagName().size());
        registry.byTagName().values().forEach(descriptors -> assertEquals(1, descriptors.size()));
    }

    @Test
    void findByClassResolvesTheDescriptor() {
        ComponentDescriptor descriptor = scan("c").findByClass(MagicHeadings.class).orElseThrow();
        assertEquals("magic-headings", descriptor.tagName());
    }

    @Test
    void findByTagNameIsPrefixQualified() {
        ComponentRegistry registry = scan("c");
        assertTrue(registry.findByTagName("c", "card").isPresent());
        assertFalse(registry.findByTagName("x", "card").isPresent());
    }

    /**
     * {@code <c:component>} is itself dialect grammar, as prefix-bound as {@code <c:slot>} — so the same
     * source scanned under two different prefixes does not double its components, the way it would have
     * under class-scanning: a template only ever registers under the one prefix its own declaration spells.
     * {@code prefixcomponents} already spells {@code x:component} for exactly this reason.
     */
    @Test
    void aggregateMergesRegistriesFromDifferentPrefixes() {
        ComponentRegistry c = scan("c");
        ComponentRegistry x = ComponentRegistry.scan(
                "prefixcomponents", "blynx.thymeleaf.compositiondialect.prefixcomponents", "x");
        ComponentRegistry merged = ComponentRegistry.aggregate(List.of(c, x));

        assertEquals(10, merged.components().size());
        assertEquals(List.of("c", "x"), List.copyOf(merged.byPrefix().keySet()));
    }

    @Test
    void scanningSeveralSourcesMergesThemIntoOneRegistry() {
        ComponentRegistry registry = ComponentRegistry.scan(List.of(
                new ComponentSource(PATH, PACKAGE),
                new ComponentSource(PROPS_PATH, PROPS_PACKAGE)), "c");

        assertEquals(8, registry.components().size());
        assertTrue(registry.findByClass(Card.class).isPresent());
        assertTrue(registry.findByClass(RecordProps.class).isPresent());
        // One prefix over both, which is what makes the dialect's own grammar canonical across sources.
        assertEquals(List.of("c"), List.copyOf(registry.byPrefix().keySet()));
    }

    @Test
    void eachSourceKeepsItsOwnComponentsPath() {
        ComponentRegistry registry = ComponentRegistry.scan(List.of(
                new ComponentSource(PATH, PACKAGE),
                new ComponentSource(PROPS_PATH, PROPS_PACKAGE)), "c");

        assertEquals("testcomponents/card", registry.findByClass(Card.class).orElseThrow().templatePath());
        assertEquals("registryfixtures/props/record-props",
                registry.findByClass(RecordProps.class).orElseThrow().templatePath());
    }

    @Test
    void bySourceSeparatesOneModulesComponentsFromAnothers() {
        ComponentSource app = new ComponentSource(PATH, PACKAGE);
        ComponentSource props = new ComponentSource(PROPS_PATH, PROPS_PACKAGE);
        ComponentRegistry registry = ComponentRegistry.scan(List.of(app, props), "c");

        assertEquals(Set.of(app, props), registry.bySource().keySet());
        assertEquals(6, registry.bySource().get(app).size());
        assertEquals(2, registry.bySource().get(props).size());
    }

    @Test
    void requireNoCollisionsSeesAClashOnlyTheMergedRegistryCanSee() {
        ComponentRegistry alpha = ComponentRegistry.scan(new ComponentSource(COLLISION_ROOT + "/alpha"), "c");
        ComponentRegistry bravo = ComponentRegistry.scan(new ComponentSource(COLLISION_ROOT + "/bravo"), "c");

        // Neither source can see the clash on its own.
        alpha.requireNoCollisions();
        bravo.requireNoCollisions();

        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> ComponentRegistry.aggregate(List.of(alpha, bravo)).requireNoCollisions());

        assertTrue(thrown.getMessage().contains("<c:twin> is claimed by"), thrown.getMessage());
        assertTrue(thrown.getMessage().contains("different component sources"), thrown.getMessage());
        assertTrue(thrown.getMessage().contains(COLLISION_ROOT + "/alpha"), thrown.getMessage());
        assertTrue(thrown.getMessage().contains(COLLISION_ROOT + "/bravo"), thrown.getMessage());
    }

    /** One source whose {@code templatesPath} covers both subdirectories — a same-source clash. */
    @Test
    void aClashWithinOneSourceIsItsAuthorsOwnToRename() {
        ComponentRegistry registry = ComponentRegistry.scan(new ComponentSource(COLLISION_ROOT), "c");

        IllegalStateException thrown = assertThrows(
                IllegalStateException.class, registry::requireNoCollisions);

        assertTrue(thrown.getMessage().contains("Rename one of them."), thrown.getMessage());
        assertFalse(thrown.getMessage().contains("different component sources"), thrown.getMessage());
    }

    @Test
    void requireNoCollisionsThrowsNamingBothTemplates() {
        ComponentRegistry registry = ComponentRegistry.scan(new ComponentSource(COLLISION_ROOT), "c");

        IllegalStateException thrown = assertThrows(
                IllegalStateException.class, registry::requireNoCollisions);

        assertTrue(thrown.getMessage().contains("<c:twin> is claimed by"), thrown.getMessage());
        assertTrue(thrown.getMessage().contains("alpha/twin.html"), thrown.getMessage());
        assertTrue(thrown.getMessage().contains("bravo/twin.html"), thrown.getMessage());
    }

    @Test
    void collisionsIsKeyedByQualifiedNameAndListsBothDescriptors() {
        ComponentRegistry registry = ComponentRegistry.scan(new ComponentSource(COLLISION_ROOT), "c");

        assertEquals(List.of("c:twin"), List.copyOf(registry.collisions().keySet()));
        assertEquals(2, registry.collisions().get("c:twin").size());
    }

    @Test
    void requireNoReservedTagNamesRejectsAComponentClaimingCSlot() {
        ComponentRegistry registry = ComponentRegistry.scan(new ComponentSource(RESERVED_PATH), "c");

        IllegalStateException thrown = assertThrows(
                IllegalStateException.class, registry::requireNoReservedTagNames);

        assertTrue(thrown.getMessage().contains("<c:slot> is claimed by"), thrown.getMessage());
        assertTrue(thrown.getMessage().contains(RESERVED_PATH + "/slot.html"), thrown.getMessage());
    }

    @Test
    void requireNoReservedTagNamesPassesForOrdinaryComponents() {
        scan("c").requireNoReservedTagNames();
    }

    @Test
    void componentsListIsImmutable() {
        List<ComponentDescriptor> components = scan("c").components();
        assertThrows(UnsupportedOperationException.class, () -> components.add(null));
    }

    @Test
    void classDeclaresPropsViaPropAnnotatedFields() {
        ComponentRegistry registry = ComponentRegistry.scan(new ComponentSource(PROPS_PATH, PROPS_PACKAGE), "c");

        ComponentDescriptor descriptor = registry.findByClass(AnnotatedProps.class).orElseThrow();
        assertEquals(Set.of("variant", "data-size"), descriptor.props());
    }

    @Test
    void classWithNoPropAnnotatedFieldsHasNoProps() {
        ComponentDescriptor descriptor = scan("c").findByClass(Card.class).orElseThrow();
        assertEquals(Set.of(), descriptor.props());
    }

    @Test
    void aRecordsComponentsAreItsPropsWithNoDeclarationAtAll() {
        ComponentRegistry registry = ComponentRegistry.scan(new ComponentSource(PROPS_PATH, PROPS_PACKAGE), "c");

        ComponentDescriptor descriptor = registry.findByClass(RecordProps.class).orElseThrow();
        assertEquals(Set.of("variant", "auto-hide-seconds"), descriptor.props());
    }

    /**
     * {@code inserter-body.html} declares no {@code <c:component>} at all — a plain Thymeleaf fragment
     * sharing the {@code casescomponents} tree, per the mandatory-declaration design: membership is
     * declared, not inferred from location.
     */
    @Test
    void aTemplateWithNoDeclarationIsNotRegistered() {
        ComponentRegistry registry = ComponentRegistry.scan(
                new ComponentSource("casescomponents", "blynx.thymeleaf.compositiondialect.casescomponents"), "c");

        assertTrue(registry.findByTagName("c", "inserter").isPresent());
        assertTrue(registry.findByTagName("c", "inserter-body").isEmpty());
    }

    @Test
    void anUnresolvableImplFailsAtScanTime() {
        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> ComponentRegistry.scan(new ComponentSource(BADIMPL_PATH), "c"));

        assertTrue(thrown.getMessage().contains("Ghost"), thrown.getMessage());
        assertTrue(thrown.getMessage().contains("could not be resolved"), thrown.getMessage());
    }

    @Test
    void implAndPropsTogetherIsRejected() {
        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> ComponentRegistry.scan(new ComponentSource(CONFLICT_PATH), "c"));

        assertTrue(thrown.getMessage().contains("declares both impl and props"), thrown.getMessage());
    }

    @Test
    void aClasslessComponentHasNoComponentClassButDeclaresItsProps() {
        ComponentRegistry registry = ComponentRegistry.scan(new ComponentSource(CLASSLESS_PATH), "c");

        ComponentDescriptor descriptor = registry.findByTagName("c", "stack").orElseThrow();
        assertTrue(descriptor.componentClass().isEmpty());
        assertEquals(Set.of("class"), descriptor.props());
    }
}
