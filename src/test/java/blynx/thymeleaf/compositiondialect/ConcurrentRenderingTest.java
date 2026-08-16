package blynx.thymeleaf.compositiondialect;

import static blynx.thymeleaf.compositiondialect.CaseRenderer.count;
import static blynx.thymeleaf.compositiondialect.CaseRenderer.textOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.templateresolver.StringTemplateResolver;

/**
 * One engine, many threads: nothing on the render path is shared between concurrent renders.
 *
 * <p>The design says so by construction — {@link ComponentFrame} is an immutable record held in a
 * Thymeleaf local variable, component instances are per occurrence, and the only mutable fields in the
 * dialect are the {@code volatile} lazy caches on {@link CompositionElementModelProcessor}
 * ({@code cachedFragment} and the three interned-name fields), each of which resolves to the same value on
 * every thread. This is the guard on that staying true, not evidence of a doubt about it: the render path
 * having no shared mutable state is the property the whole {@code ${this}}-via-frame design rests on, and
 * a future change reintroducing some would otherwise be caught by nothing.
 *
 * <p>Each render carries a marker of its own <em>and</em> a nesting depth of its own, so a leak between
 * threads shows up as a wrong value rather than only as a crash: {@code data-title} and the echoed span
 * come from the render's own context variable, and the heading's level is computed by walking however many
 * {@code c:outline}s that render wrapped it in — a value one component publishes and another reads back,
 * which is the most leakable thing here.
 *
 * <p>A green run does not prove the absence of a race; it is a regression net. The cold-engine case is the
 * one that matters most, because every thread reaches the lazy caches unpopulated and races to fill them.
 */
class ConcurrentRenderingTest {

    private static final String COMPONENT_PACKAGE = "blynx.thymeleaf.compositiondialect.casescomponents";
    private static final String COMPONENTS_PATH = "casescomponents";

    private static final int RENDERS = 200;
    /** Distinct {@code c:outline} nesting depths, so the level a heading reports differs by render. */
    private static final int DEPTHS = 4;
    private static final String MARKER_PREFIX = "m-";

    /** Every lazy cache unpopulated, so the threads race to fill them rather than finding them warm. */
    @Test
    @Timeout(value = 2, unit = TimeUnit.MINUTES)
    void concurrentRendersOnAColdEngineDoNotInterfere() {
        assertEveryRenderIsItsOwn(buildEngine());
    }

    /** The steady state an application actually runs in, after the first request has warmed everything. */
    @Test
    @Timeout(value = 2, unit = TimeUnit.MINUTES)
    void concurrentRendersOnAWarmEngineDoNotInterfere() {
        TemplateEngine engine = buildEngine();
        for (int depth = 1; depth <= DEPTHS; depth++) {
            render(engine, depth - 1);
        }

        assertEveryRenderIsItsOwn(engine);
    }

    /**
     * Fires {@link #RENDERS} renders across a fixed pool held at a start gate, so they genuinely overlap.
     * Deliberately not {@code IntStream.parallel()}, which runs on the common pool and degenerates to
     * sequential on a single-core runner — where this test would then prove nothing while still passing.
     */
    private static void assertEveryRenderIsItsOwn(TemplateEngine engine) {
        int threads = Math.max(4, Runtime.getRuntime().availableProcessors());
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch gate = new CountDownLatch(1);
        List<Future<String>> pending = new ArrayList<>(RENDERS);
        try {
            for (int index = 0; index < RENDERS; index++) {
                int render = index;
                pending.add(pool.submit(() -> {
                    gate.await();
                    return render(engine, render);
                }));
            }
            gate.countDown();

            // Collected rather than thrown on first sight: which renders failed is the interesting part.
            List<String> results = new ArrayList<>(RENDERS);
            List<String> failures = new ArrayList<>();
            for (int index = 0; index < RENDERS; index++) {
                try {
                    results.add(pending.get(index).get());
                } catch (Exception e) {
                    results.add(null);
                    failures.add("render " + index + " threw " + rootOf(e));
                }
            }
            assertTrue(failures.isEmpty(), () -> failures.size() + " of " + RENDERS + " renders threw:\n"
                    + String.join("\n", failures));
            for (int index = 0; index < RENDERS; index++) {
                assertRenderIsItsOwn(index, results.get(index));
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private static void assertRenderIsItsOwn(int index, String result) {
        String marker = MARKER_PREFIX + index;
        int depth = depthOf(index);

        assertTrue(result.contains("data-title=\"" + marker + "\""), () -> describe(index, result));
        assertEquals(marker, textOf(result, "echo"), () -> describe(index, result));
        assertTrue(result.contains("id=\"heading-" + depth + "\""), () -> describe(index, result));
        // Its own marker in exactly the two places it belongs, and no other render's anywhere.
        assertEquals(2, count(result, MARKER_PREFIX), () -> describe(index, result));
        assertEquals(1, count(result, "id=\"heading-"), () -> describe(index, result));
    }

    private static String render(TemplateEngine engine, int index) {
        Context context = new Context();
        context.setVariable("marker", MARKER_PREFIX + index);
        return engine.process(page(index), context);
    }

    /**
     * Two probes in one page: a marker that has to survive the trip out to a component's attribute and
     * back into its slot content, and a heading whose level only comes out right if the {@code outline}
     * this render published is the one it reads back.
     */
    private static String page(int index) {
        int depth = depthOf(index);
        return "<c:panel c:title=\"${marker}\"><span id=\"echo\" th:text=\"${marker}\">?</span></c:panel>"
                + "<c:outline>".repeat(depth) + "<c:heading />" + "</c:outline>".repeat(depth);
    }

    private static int depthOf(int index) {
        return 1 + index % DEPTHS;
    }

    private static String describe(int index, String result) {
        return "render " + index + " (depth " + depthOf(index) + "):\n" + result;
    }

    private static String rootOf(Throwable thrown) {
        Throwable root = thrown;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root + "";
    }

    private static TemplateEngine buildEngine() {
        // Cacheable, as an application runs: it is the fragment cache being populated under contention
        // that the cold case is there to exercise.
        ClassLoaderTemplateResolver components = new ClassLoaderTemplateResolver();
        components.setOrder(1);
        components.setPrefix("templates/");
        components.setSuffix(".html");
        components.setResolvablePatterns(Set.of(COMPONENTS_PATH + "/**"));
        components.setCharacterEncoding("UTF-8");

        StringTemplateResolver pages = new StringTemplateResolver();
        pages.setOrder(2);

        TemplateEngine engine = new TemplateEngine();
        engine.addTemplateResolver(components);
        engine.addTemplateResolver(pages);
        engine.addDialect(new CompositionDialect(COMPONENT_PACKAGE, COMPONENTS_PATH));
        return engine;
    }
}
