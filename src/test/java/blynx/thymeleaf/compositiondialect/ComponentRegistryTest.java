package blynx.thymeleaf.compositiondialect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import blynx.thymeleaf.compositiondialect.registryfixtures.abstractbase.BaseThing;
import blynx.thymeleaf.compositiondialect.registryfixtures.abstractbase.Widget;
import blynx.thymeleaf.compositiondialect.registryfixtures.composition.AnnotatedLeaf;
import blynx.thymeleaf.compositiondialect.registryfixtures.composition.BareAnnotatedLeaf;
import blynx.thymeleaf.compositiondialect.registryfixtures.composition.OverridingLeaf;
import blynx.thymeleaf.compositiondialect.registryfixtures.props.AnnotatedProps;
import blynx.thymeleaf.compositiondialect.registryfixtures.props.RecordProps;
import blynx.thymeleaf.compositiondialect.testcomponents.Card;
import blynx.thymeleaf.compositiondialect.testcomponents.MagicHeadings;

class ComponentRegistryTest {

    private static final String PACKAGE = "blynx.thymeleaf.compositiondialect.testcomponents";
    private static final String COLLISION_FIXTURES =
            "blynx.thymeleaf.compositiondialect.registryfixtures.collision";
    private static final String ABSTRACT_BASE_FIXTURES =
            "blynx.thymeleaf.compositiondialect.registryfixtures.abstractbase";
    private static final String COMPOSITION_FIXTURES =
            "blynx.thymeleaf.compositiondialect.registryfixtures.composition";
    private static final String PROPS_FIXTURES =
            "blynx.thymeleaf.compositiondialect.registryfixtures.props";
    private static final String RESERVED_FIXTURES =
            "blynx.thymeleaf.compositiondialect.registryfixtures.reserved";
    private static final String COLLISION_ALPHA = COLLISION_FIXTURES + ".alpha";
    private static final String COLLISION_BRAVO = COLLISION_FIXTURES + ".bravo";

    private ComponentRegistry scan(String prefix) {
        return ComponentRegistry.scan(PACKAGE, "testcomponents", prefix);
    }

    @Test
    void toTagNameConvertsPascalCaseToKebabCase() {
        assertEquals("card", ComponentRegistry.toTagName(Card.class));
        assertEquals("magic-headings", ComponentRegistry.toTagName(MagicHeadings.class));
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

        assertEquals(Card.class, card.componentClass());
        assertEquals("c", card.prefix());
        assertEquals("card", card.tagName());
        assertEquals("testcomponents/card", card.templatePath());
        assertEquals("c:card", card.qualifiedName());
        assertEquals(new ComponentSource(PACKAGE, "testcomponents"), card.source());
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

    @Test
    void aggregateMergesRegistriesAndExposesEveryClaimantOfATag() {
        ComponentRegistry merged = ComponentRegistry.aggregate(List.of(scan("c"), scan("x")));

        assertEquals(12, merged.components().size());
        assertEquals(List.of("c", "x"), List.copyOf(merged.byPrefix().keySet()));
        // "card" is now claimed twice; reporting that is requireNoCollisions', not the merge's.
        assertEquals(2, merged.byTagName().get("card").size());
    }

    @Test
    void scanningSeveralSourcesMergesThemIntoOneRegistry() {
        ComponentRegistry registry = ComponentRegistry.scan(List.of(
                new ComponentSource(PACKAGE, "testcomponents"),
                new ComponentSource(PROPS_FIXTURES)), "c");

        assertEquals(8, registry.components().size());
        assertTrue(registry.findByClass(Card.class).isPresent());
        assertTrue(registry.findByClass(RecordProps.class).isPresent());
        // One prefix over both, which is what makes the dialect's own grammar canonical across sources.
        assertEquals(List.of("c"), List.copyOf(registry.byPrefix().keySet()));
    }

    @Test
    void eachSourceKeepsItsOwnComponentsPath() {
        ComponentRegistry registry = ComponentRegistry.scan(List.of(
                new ComponentSource(PACKAGE, "testcomponents"),
                new ComponentSource(PROPS_FIXTURES, "vendor/lib")), "c");

        assertEquals("testcomponents/card", registry.findByClass(Card.class).orElseThrow().templatePath());
        assertEquals("vendor/lib/record-props",
                registry.findByClass(RecordProps.class).orElseThrow().templatePath());
    }

    @Test
    void bySourcePackageSeparatesOneModulesComponentsFromAnothers() {
        ComponentRegistry registry = ComponentRegistry.scan(List.of(
                new ComponentSource(PACKAGE, "testcomponents"),
                new ComponentSource(PROPS_FIXTURES)), "c");

        assertEquals(List.of(PACKAGE, PROPS_FIXTURES).stream().sorted().toList(),
                registry.bySourcePackage().keySet().stream().sorted().toList());
        assertEquals(6, registry.bySourcePackage().get(PACKAGE).size());
        assertEquals(2, registry.bySourcePackage().get(PROPS_FIXTURES).size());
    }

    @Test
    void requireNoCollisionsSeesAClashOnlyTheMergedRegistryCanSee() {
        ComponentRegistry alpha = ComponentRegistry.scan(new ComponentSource(COLLISION_ALPHA), "c");
        ComponentRegistry bravo = ComponentRegistry.scan(new ComponentSource(COLLISION_BRAVO), "c");

        // Neither source can see the clash on its own.
        alpha.requireNoCollisions();
        bravo.requireNoCollisions();

        IllegalStateException thrown = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> ComponentRegistry.aggregate(List.of(alpha, bravo)).requireNoCollisions());

        assertTrue(thrown.getMessage().contains("<c:twin> is claimed by"), thrown.getMessage());
        assertTrue(thrown.getMessage().contains("different component sources"), thrown.getMessage());
        assertTrue(thrown.getMessage().contains(COLLISION_ALPHA), thrown.getMessage());
        assertTrue(thrown.getMessage().contains(COLLISION_BRAVO), thrown.getMessage());
    }

    @Test
    void aClashWithinOneSourceIsItsAuthorsOwnToRename() {
        ComponentRegistry registry = ComponentRegistry.scan(COLLISION_FIXTURES, null, "c");

        IllegalStateException thrown = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class, registry::requireNoCollisions);

        assertTrue(thrown.getMessage().contains("Rename one of them."), thrown.getMessage());
        assertFalse(thrown.getMessage().contains("different component sources"), thrown.getMessage());
    }

    @Test
    void requireNoReservedTagNamesRejectsAComponentClaimingCSlot() {
        ComponentRegistry registry = ComponentRegistry.scan(new ComponentSource(RESERVED_FIXTURES), "c");

        IllegalStateException thrown = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class, registry::requireNoReservedTagNames);

        assertTrue(thrown.getMessage().contains("<c:slot> is claimed by"), thrown.getMessage());
        assertTrue(thrown.getMessage().contains(
                "blynx.thymeleaf.compositiondialect.registryfixtures.reserved.Slot"), thrown.getMessage());
    }

    @Test
    void requireNoReservedTagNamesPassesForOrdinaryComponents() {
        scan("c").requireNoReservedTagNames();
    }

    @Test
    void componentsListIsImmutable() {
        List<ComponentDescriptor> components = scan("c").components();
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                () -> components.add(null));
    }

    @Test
    void requireNoCollisionsThrowsWhenTwoClassesShareATagUnderTheSamePrefix() {
        ComponentRegistry registry = ComponentRegistry.scan(COLLISION_FIXTURES, null, "c");

        IllegalStateException thrown = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class, registry::requireNoCollisions);

        assertTrue(thrown.getMessage().contains("<c:twin> is claimed by"), thrown.getMessage());
        assertTrue(thrown.getMessage().contains(
                "blynx.thymeleaf.compositiondialect.registryfixtures.collision.alpha.Twin"), thrown.getMessage());
        assertTrue(thrown.getMessage().contains(
                "blynx.thymeleaf.compositiondialect.registryfixtures.collision.bravo.Twin"), thrown.getMessage());
    }

    @Test
    void collisionsIsKeyedByQualifiedNameAndListsBothDescriptors() {
        ComponentRegistry registry = ComponentRegistry.scan(COLLISION_FIXTURES, null, "c");

        assertEquals(List.of("c:twin"), List.copyOf(registry.collisions().keySet()));
        assertEquals(2, registry.collisions().get("c:twin").size());
    }

    @Test
    void scanSkipsAbstractIntermediateBase() {
        ComponentRegistry registry = ComponentRegistry.scan(ABSTRACT_BASE_FIXTURES, null, "c");

        assertTrue(registry.findByClass(BaseThing.class).isEmpty());
        assertEquals(1, registry.components().size());
    }

    @Test
    void scanStillDiscoversConcreteSubclassOfAbstractBase() {
        ComponentRegistry registry = ComponentRegistry.scan(ABSTRACT_BASE_FIXTURES, null, "c");

        ComponentDescriptor widget = registry.findByClass(Widget.class).orElseThrow();
        assertEquals("widget", widget.tagName());
    }

    @Test
    void aConcreteSubclassGetsItsOwnCompositionPathRegardlessOfItsAbstractParent() {
        ComponentRegistry registry = ComponentRegistry.scan(ABSTRACT_BASE_FIXTURES, null, "c");

        ComponentDescriptor widget = registry.findByClass(Widget.class).orElseThrow();
        assertEquals("sub-folder/widget", widget.templatePath());
    }

    @Test
    void compositionPathPrefixOnThePackageComposesWithATypesOwnCompositionPath() {
        ComponentRegistry registry = ComponentRegistry.scan(COMPOSITION_FIXTURES, null, "c");

        ComponentDescriptor leaf = registry.findByClass(AnnotatedLeaf.class).orElseThrow();
        assertEquals("shared/leaf-folder/annotated-leaf", leaf.templatePath());
    }

    @Test
    void compositionPathPrefixOnThePackageAppliesWithNoCompositionOnTheTypeAtAll() {
        ComponentRegistry registry = ComponentRegistry.scan(COMPOSITION_FIXTURES, null, "c");

        ComponentDescriptor bareLeaf = registry.findByClass(BareAnnotatedLeaf.class).orElseThrow();
        assertEquals("shared/bare-annotated-leaf", bareLeaf.templatePath());
    }

    @Test
    void compositionPathPrefixOnTheTypeWinsOverThePackages() {
        ComponentRegistry registry = ComponentRegistry.scan(COMPOSITION_FIXTURES, null, "c");

        ComponentDescriptor overriding = registry.findByClass(OverridingLeaf.class).orElseThrow();
        assertEquals("override/own-folder/overriding-leaf", overriding.templatePath());
    }

    @Test
    void classDeclaresPropsViaPropAnnotatedFields() {
        ComponentRegistry registry = ComponentRegistry.scan(PROPS_FIXTURES, null, "c");

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
        ComponentRegistry registry = ComponentRegistry.scan(PROPS_FIXTURES, null, "c");

        ComponentDescriptor descriptor = registry.findByClass(RecordProps.class).orElseThrow();
        assertEquals(Set.of("variant", "auto-hide-seconds"), descriptor.props());
    }
}
