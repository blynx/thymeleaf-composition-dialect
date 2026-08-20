package blynx.thymeleaf.compositiondialect;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.attoparser.ParseException;
import org.attoparser.config.ParseConfiguration;
import org.attoparser.simple.AbstractSimpleMarkupHandler;
import org.attoparser.simple.SimpleMarkupParser;

/**
 * Reads a template's {@code <c:component>} declaration with no Thymeleaf engine or context involved, for
 * use at startup enumeration. {@code parseStandalone} needs an {@code ITemplateContext}, which does not
 * exist outside a render — so a declaration cannot be read that way before the engine exists. AttoParser
 * needs neither: it is already on the classpath as a Thymeleaf transitive dependency, and it is the very
 * same parser the engine uses later, so nothing about what counts as well-formed markup can diverge between
 * this read and the real one.
 *
 * <p>Only the self-closing form is read — {@code <c:component impl="…" props="…"/>}. The paired form (with
 * nested {@code <c:prop>} declarations) ships later; an open {@code <c:component>} tag is reported the same
 * as a duplicate, since at most one declaration of either shape is ever legal per template.
 */
final class ComponentDeclarationReader {

    /** A template's raw, unresolved declaration — {@code null} fields mean the attribute was absent. */
    record Declaration(String impl, String props) {
    }

    private final String componentTagName;

    ComponentDeclarationReader(String dialectPrefix) {
        this.componentTagName = dialectPrefix + ":component";
    }

    /** The template's declaration, or {@code null} if it declares none at all — just a plain template. */
    Declaration read(InputStream resource, String describeForErrors) {
        try (Reader reader = new InputStreamReader(resource, StandardCharsets.UTF_8)) {
            Handler handler = new Handler(describeForErrors);
            new SimpleMarkupParser(ParseConfiguration.htmlConfiguration()).parse(reader, handler);
            return handler.found;
        } catch (ParseException | IOException e) {
            throw new IllegalStateException(CompositionDialect.DIALECT_NAME
                    + ": could not read " + describeForErrors + " while looking for its "
                    + componentTagName + " declaration", e);
        }
    }

    private final class Handler extends AbstractSimpleMarkupHandler {

        private final String describeForErrors;
        private Declaration found;

        Handler(String describeForErrors) {
            this.describeForErrors = describeForErrors;
        }

        @Override
        public void handleStandaloneElement(String elementName, Map<String, String> attributes,
                                            boolean minimized, int line, int col) {
            record(elementName, attributes);
        }

        @Override
        public void handleOpenElement(String elementName, Map<String, String> attributes, int line, int col) {
            if (elementName.equalsIgnoreCase(componentTagName)) {
                throw new IllegalStateException(CompositionDialect.DIALECT_NAME + ": <" + componentTagName
                        + "> in " + describeForErrors + " must be self-closing — the paired form (with nested "
                        + "<" + componentTagName.substring(0, componentTagName.indexOf(':') + 1) + "prop>) is not "
                        + "supported yet.");
            }
        }

        private void record(String elementName, Map<String, String> attributes) {
            if (!elementName.equalsIgnoreCase(componentTagName)) {
                return;
            }
            if (found != null) {
                throw new IllegalStateException(CompositionDialect.DIALECT_NAME + ": " + describeForErrors
                        + " declares <" + componentTagName + "> more than once — at most one is allowed.");
            }
            Map<String, String> attrs = attributes == null ? Map.of() : attributes;
            found = new Declaration(attrs.get("impl"), attrs.get("props"));
        }
    }
}
