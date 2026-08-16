package blynx.thymeleaf.compositiondialect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.thymeleaf.processor.IProcessor;

import blynx.thymeleaf.compositiondialect.casescomponents.Panel;
import blynx.thymeleaf.compositiondialect.libcomponents.DsCard;

/** What building one dialect out of several {@link ComponentSource}s settles at startup. */
class CompositionDialectTest {

    private static final ComponentSource APP =
            new ComponentSource("blynx.thymeleaf.compositiondialect.casescomponents", "casescomponents");
    private static final ComponentSource LIBRARY =
            new ComponentSource("blynx.thymeleaf.compositiondialect.libcomponents", "libcomponents");
    private static final String COLLISION_FIXTURES =
            "blynx.thymeleaf.compositiondialect.registryfixtures.collision";

    @Test
    void oneDialectCoversEverySource() {
        ComponentRegistry registry = new CompositionDialect(APP, LIBRARY).getRegistry();

        assertTrue(registry.findByClass(Panel.class).isPresent());
        assertTrue(registry.findByClass(DsCard.class).isPresent());
        assertEquals(List.of("c"), List.copyOf(registry.byPrefix().keySet()));
    }

    /**
     * The reason libraries contribute sources rather than dialects. Two dialects sharing a prefix would
     * each register their own copy of these, and a doubled {@code c:_caller} unwinds two frames per
     * hand-off instead of one — silently handing slot content the wrong {@code this}.
     */
    @Test
    void theGrammarIsRegisteredOnceNoMatterHowManySourcesThereAre() {
        List<IProcessor> processors = List.copyOf(new CompositionDialect(APP, LIBRARY).getProcessors("c"));

        assertEquals(1, countOf(processors, CompositionCallerProcessor.class));
        assertEquals(1, countOf(processors, CompositionRestAttributesTagProcessor.class));
        // One per template mode, HTML and XML.
        assertEquals(2, countOf(processors, CompositionSlotPlacementProcessor.class));
        assertEquals(2, countOf(processors, CompositionUnresolvedTagProcessor.class));
    }

    @Test
    void aTagClaimedByTwoSourcesIsRejectedAtStartup() {
        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> new CompositionDialect(
                        new ComponentSource(COLLISION_FIXTURES + ".alpha"),
                        new ComponentSource(COLLISION_FIXTURES + ".bravo")));

        assertTrue(thrown.getMessage().contains("<c:twin>"), thrown.getMessage());
    }

    @Test
    void aComponentClaimingTheDialectsOwnGrammarIsRejectedAtStartup() {
        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> new CompositionDialect(
                        new ComponentSource("blynx.thymeleaf.compositiondialect.registryfixtures.reserved")));

        assertTrue(thrown.getMessage().contains("reserved by the dialect itself"), thrown.getMessage());
    }

    @Test
    void aSourceWithoutAPackageIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ComponentSource("  "));
    }

    private static long countOf(List<IProcessor> processors, Class<? extends IProcessor> type) {
        return processors.stream().filter(type::isInstance).count();
    }
}
