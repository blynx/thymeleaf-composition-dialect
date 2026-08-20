package blynx.thymeleaf.compositiondialect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * A source's declared {@code tagNamespace} owns its tag space by construction: enforced at scan time
 * (every template must be named {@code <namespace>-*}), unique across sources (checked from the source
 * list alone, before anything is walked), and required to actually contribute something. See
 * {@code _local-notes/TEMPLATE-FIRST-COMPONENTS.md}, "Declare the namespace once, on the source".
 */
class ComponentSourceNamespaceTest {

    private static final String GOOD = "registryfixtures/namespace/good";
    private static final String BAD = "registryfixtures/namespace/bad";

    @Test
    void aTemplateNotNamedForItsNamespaceFailsAtScanTime() {
        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> ComponentRegistry.scan(ComponentSource.namespaced(BAD, "wa"), "c"));

        assertTrue(thrown.getMessage().contains("namespaced \"wa\""), thrown.getMessage());
        assertTrue(thrown.getMessage().contains(BAD + "/oops.html"), thrown.getMessage());
        assertTrue(thrown.getMessage().contains("wa-oops.html"), thrown.getMessage());
    }

    @Test
    void aCorrectlyNamedNamespacedTemplateRegisters() {
        ComponentRegistry registry = ComponentRegistry.scan(ComponentSource.namespaced(GOOD, "wa"), "c");

        assertTrue(registry.findByTagName("c", "wa-button").isPresent());
    }

    @Test
    void twoSourcesClaimingTheSameNamespaceFailAtConstruction() {
        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> ComponentRegistry.scan(List.of(
                        ComponentSource.namespaced(GOOD, "wa"),
                        ComponentSource.namespaced("registryfixtures/namespace/other", "wa")), "c"));

        assertTrue(thrown.getMessage().contains("\"wa\""), thrown.getMessage());
        assertTrue(thrown.getMessage().contains("more than one source"), thrown.getMessage());
    }

    @Test
    void aSourceThatContributesNothingFailsAtConstruction() {
        ComponentSource empty = ComponentSource.namespaced("registryfixtures/namespace/nowhere", "zz");

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> new CompositionDialect(List.of(empty)));

        assertTrue(thrown.getMessage().contains("contributed no components"), thrown.getMessage());
        assertTrue(thrown.getMessage().contains("registryfixtures/namespace/nowhere"), thrown.getMessage());
    }

    @Test
    void misspelledTagInAKnownNamespaceListsWhatItOffers() {
        ComponentRegistry registry = ComponentRegistry.scan(ComponentSource.namespaced(GOOD, "wa"), "c");

        String description = registry.describeUnresolvedTag("wa-buton");

        assertEquals("the \"wa\" namespace offers: wa-button", description);
    }

    @Test
    void unknownNamespaceSaysSoRatherThanNamingAFile() {
        ComponentRegistry registry = ComponentRegistry.scan(ComponentSource.namespaced(GOOD, "wa"), "c");

        String description = registry.describeUnresolvedTag("xy-something");

        assertEquals("nothing provides the \"xy\" namespace — is a dependency missing?", description);
    }

    @Test
    void aTagWithNoNamespaceLookingPrefixIsLookedForAmongOwnComponents() {
        ComponentRegistry registry = ComponentRegistry.scan(ComponentSource.namespaced(GOOD, "wa"), "c");

        assertEquals("not found among your own components", registry.describeUnresolvedTag("totallyunknown"));
    }
}
