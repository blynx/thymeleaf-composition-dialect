package blynx.thymeleaf.compositiondialect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import blynx.thymeleaf.compositiondialect.registryfixtures.abstractbase.BaseThing;
import blynx.thymeleaf.compositiondialect.registryfixtures.abstractbase.Widget;
import blynx.thymeleaf.compositiondialect.registryfixtures.pathprefix.BareLeaf;
import blynx.thymeleaf.compositiondialect.registryfixtures.pathprefix.Leaf;
import blynx.thymeleaf.compositiondialect.testcomponents.Card;
import blynx.thymeleaf.compositiondialect.testcomponents.MagicHeadings;

class ComponentRegistryTest {

    private static final String PACKAGE = "blynx.thymeleaf.compositiondialect.testcomponents";
    private static final String COLLISION_FIXTURES =
            "blynx.thymeleaf.compositiondialect.registryfixtures.collision";
    private static final String ABSTRACT_BASE_FIXTURES =
            "blynx.thymeleaf.compositiondialect.registryfixtures.abstractbase";
    private static final String PATH_PREFIX_FIXTURES =
            "blynx.thymeleaf.compositiondialect.registryfixtures.pathprefix";

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
    void aggregateMergesRegistriesAndExposesCrossPrefixCollisions() {
        ComponentRegistry merged = ComponentRegistry.aggregate(List.of(scan("c"), scan("x")));

        assertEquals(12, merged.components().size());
        assertEquals(List.of("c", "x"), List.copyOf(merged.byPrefix().keySet()));
        // "card" now exists under both prefixes — the substrate Module Organization reads for collisions.
        assertEquals(2, merged.byTagName().get("card").size());
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
    void aConcreteSubclassGetsItsOwnPathNotItsAbstractParents() {
        ComponentRegistry registry = ComponentRegistry.scan(ABSTRACT_BASE_FIXTURES, null, "c");

        ComponentDescriptor widget = registry.findByClass(Widget.class).orElseThrow();
        assertEquals("sub-folder/widget", widget.templatePath());
    }

    @Test
    void pathPrefixComposesWithASubclassesOwnPath() {
        ComponentRegistry registry = ComponentRegistry.scan(PATH_PREFIX_FIXTURES, null, "c");

        ComponentDescriptor leaf = registry.findByClass(Leaf.class).orElseThrow();
        assertEquals("shared/leaf-folder/leaf", leaf.templatePath());
    }

    @Test
    void pathPrefixComposesEvenWithoutAnOwnPath() {
        ComponentRegistry registry = ComponentRegistry.scan(PATH_PREFIX_FIXTURES, null, "c");

        ComponentDescriptor bareLeaf = registry.findByClass(BareLeaf.class).orElseThrow();
        assertEquals("shared/bare-leaf", bareLeaf.templatePath());
    }
}
