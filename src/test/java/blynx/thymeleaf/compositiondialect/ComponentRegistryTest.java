package blynx.thymeleaf.compositiondialect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import blynx.thymeleaf.compositiondialect.testcomponents.Card;
import blynx.thymeleaf.compositiondialect.testcomponents.MagicHeadings;

class ComponentRegistryTest {

    private static final String PACKAGE = "blynx.thymeleaf.compositiondialect.testcomponents";

    private ComponentRegistry scan(String prefix) {
        return ComponentRegistry.scan(PACKAGE, "components", prefix);
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
        assertEquals("components/card", card.templatePath());
        assertEquals("c:card", card.qualifiedName());
    }

    @Test
    void templatePathIncludesTagNameForEveryComponent() {
        for (ComponentDescriptor descriptor : scan("c").components()) {
            assertEquals("components/" + descriptor.tagName(), descriptor.templatePath());
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
}
