package blynx.thymeleaf.compositiondialect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/**
 * Renders the case templates under {@code src/test/resources/templates/cases}. Each case is a real
 * template file that documents one behaviour in its own header comment, so the file can be read on its
 * own and the test alongside it only has to state the outcome.
 *
 * <p>A case may also keep what it produces beside it as {@code <the-case>.expected.html}, and this class
 * compares against it on every render — documentation that cannot rot. See the cases' own README.
 *
 * <p>The components the cases use live in {@code casescomponents}.
 */
final class CaseRenderer {

    private static final String CASES = "cases";
    private static final String COMPONENT_PACKAGE = "blynx.thymeleaf.compositiondialect.casescomponents";
    private static final String COMPONENTS_PATH = "casescomponents";

    /** The imported component library the {@code modules/} cases use. See {@code libcomponents}. */
    private static final String LIBRARY_PACKAGE = "blynx.thymeleaf.compositiondialect.libcomponents";
    private static final String LIBRARY_PATH = "libcomponents";

    /** The sibling file holding what a case produces, when it has one. */
    static final String EXPECTED_SUFFIX = ".expected";

    private static final Pattern COMMENT = Pattern.compile("(?s)<!--.*?-->");

    private static final TemplateEngine ENGINE = buildEngine();

    private CaseRenderer() {
    }

    /** The case's rendered output, with its documentation comment stripped. */
    static String render(String casePath) {
        requireCaseExists(casePath);
        Context context = new Context();
        context.setVariable("caption", "evaluated");
        context.setVariable("items", List.of("one", "two"));
        String output = ENGINE.process(CASES + "/" + casePath, context);
        String stripped = COMMENT.matcher(output).replaceAll("").strip();
        assertMatchesExpected(casePath, stripped);
        return stripped;
    }

    /**
     * The message a case that documents an error fails with, having checked that it does fail. Such a case
     * renders nothing, so it keeps no expected output; the message is the outcome, and the test states it.
     */
    static String failureOf(String casePath) {
        Exception thrown = assertThrows(Exception.class, () -> render(casePath), casePath);
        Throwable root = thrown;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return String.valueOf(root.getMessage());
    }

    /**
     * Compares the rendered markup against {@code <the-case>.expected.html} when the case keeps one — an
     * empty file included, which says the case renders nothing. Indentation and blank lines are the
     * renderer's business rather than the spec's, so both sides are compared line by line with those
     * removed, which lets the expected file be indented for whoever reads it.
     */
    private static void assertMatchesExpected(String casePath, String rendered) {
        String expected = readIfPresent(casePath + EXPECTED_SUFFIX);
        if (expected == null) {
            return;
        }
        assertEquals(significantLines(expected), significantLines(rendered),
                casePath + " does not match " + casePath + EXPECTED_SUFFIX + ".html");
    }

    private static String significantLines(String text) {
        return text.lines().map(String::strip).filter(line -> !line.isEmpty())
                .collect(Collectors.joining("\n"));
    }

    /** The text content of {@code <tag id="…">text</tag>}, for comparing one position against another. */
    static String textOf(String html, String id) {
        Matcher matcher = Pattern.compile("<[a-z]+ id=\"" + id + "\"[^>]*>(.*?)</").matcher(html);
        assertTrue(matcher.find(), "no element with id \"" + id + "\" in:\n" + html);
        return matcher.group(1).strip();
    }

    /** Where {@code needle} appears, asserting that it does — for stating that one thing precedes another. */
    static int at(String html, String needle) {
        int index = html.indexOf(needle);
        assertTrue(index >= 0, "\"" + needle + "\" missing from:\n" + html);
        return index;
    }

    static int count(String html, String needle) {
        return html.split(Pattern.quote(needle), -1).length - 1;
    }

    private static void requireCaseExists(String casePath) {
        if (readIfPresent(casePath) == null) {
            throw new IllegalArgumentException("No such case: " + casePath);
        }
    }

    /** The file's contents, or {@code null} if there is no such file — an expected file is optional. */
    private static String readIfPresent(String casePath) {
        try (var stream = CaseRenderer.class.getClassLoader()
                .getResourceAsStream("templates/" + CASES + "/" + casePath + ".html")) {
            return stream == null ? null : new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read case " + casePath, e);
        }
    }

    private static TemplateEngine buildEngine() {
        ClassLoaderTemplateResolver templates = new ClassLoaderTemplateResolver();
        templates.setPrefix("templates/");
        templates.setSuffix(".html");
        templates.setResolvablePatterns(Set.of(CASES + "/**", COMPONENTS_PATH + "/**", LIBRARY_PATH + "/**"));
        templates.setCharacterEncoding("UTF-8");
        templates.setCacheable(false);

        // Two sources in one dialect, as an application importing a component library has: every case in
        // the suite renders through it, so the whole spec doubles as the evidence that a second source
        // changes nothing about the first.
        TemplateEngine engine = new TemplateEngine();
        engine.addTemplateResolver(templates);
        engine.addDialect(new CompositionDialect(
                new ComponentSource(COMPONENT_PACKAGE, COMPONENTS_PATH),
                new ComponentSource(LIBRARY_PACKAGE, LIBRARY_PATH)));
        return engine;
    }
}
