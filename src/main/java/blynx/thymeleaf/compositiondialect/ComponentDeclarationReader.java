package blynx.thymeleaf.compositiondialect;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
 * <p>Two shapes are read, saying the same thing two ways:
 * <ul>
 *   <li>self-closing, with a flat {@code props} list: {@code <c:component impl="…" props="a, b"/>}
 *   <li>paired, with one {@code <c:prop>} per prop: {@code <c:component impl="…"><c:prop name="a"/>...
 *       </c:component>} — whose children are declarations, never markup; the component's own markup is
 *       its sibling.
 * </ul>
 * Declaring props both ways on the same {@code <c:component>} is rejected, so there is exactly one place
 * to look for a component's props.
 */
final class ComponentDeclarationReader {

    /** A template's raw, unresolved declaration — {@code null} {@code impl} means none was given. */
    record Declaration(String impl, List<PropDeclaration> props) {
    }

    /** One {@code <c:prop>}'s raw, unresolved shape — {@code null} fields mean the attribute was absent. */
    record PropDeclaration(String name, boolean required, String defaultValue, String type) {
    }

    private final String componentTagName;
    private final String propTagName;

    ComponentDeclarationReader(String dialectPrefix) {
        this.componentTagName = dialectPrefix + ":component";
        this.propTagName = dialectPrefix + ":prop";
    }

    /** The template's declaration, or {@code null} if it declares none at all — just a plain template. */
    Declaration read(InputStream resource, String describeForErrors) {
        try (Reader reader = new InputStreamReader(resource, StandardCharsets.UTF_8)) {
            Handler handler = new Handler(describeForErrors);
            new SimpleMarkupParser(ParseConfiguration.htmlConfiguration()).parse(reader, handler);
            if (handler.insideComponent) {
                // Reached end of document still "inside" — only possible if the parser tolerated an
                // unbalanced <c:component>, which htmlConfiguration's auto-closing should not do; kept as
                // a defensive check rather than a silently half-read declaration.
                throw new IllegalStateException(CompositionDialect.DIALECT_NAME + ": <" + componentTagName
                        + "> in " + describeForErrors + " is never closed.");
            }
            return handler.found;
        } catch (ParseException e) {
            // AttoParser wraps whatever the handler throws in its own checked exception; a validation
            // failure the handler raised on purpose (an IllegalStateException with the real message —
            // unclosed <c:prop>, markup where only declarations belong, both prop forms at once) is more
            // useful surfaced directly than re-wrapped behind a generic "could not read" message.
            if (e.getCause() instanceof IllegalStateException illegalState) {
                throw illegalState;
            }
            throw new IllegalStateException(CompositionDialect.DIALECT_NAME
                    + ": could not read " + describeForErrors + " while looking for its "
                    + componentTagName + " declaration", e);
        } catch (IOException e) {
            throw new IllegalStateException(CompositionDialect.DIALECT_NAME
                    + ": could not read " + describeForErrors + " while looking for its "
                    + componentTagName + " declaration", e);
        }
    }

    private final class Handler extends AbstractSimpleMarkupHandler {

        private final String describeForErrors;
        private Declaration found;

        private boolean insideComponent = false;
        private String pendingImpl;
        /** Non-null only when {@code props=} was written on the paired form's own opening tag. */
        private List<PropDeclaration> pendingAttributeProps;
        private List<PropDeclaration> pendingChildProps;

        Handler(String describeForErrors) {
            this.describeForErrors = describeForErrors;
        }

        @Override
        public void handleStandaloneElement(String elementName, Map<String, String> attributes,
                                            boolean minimized, int line, int col) {
            if (insideComponent) {
                if (elementName.equalsIgnoreCase(propTagName)) {
                    pendingChildProps.add(readPropDeclaration(attributes));
                    return;
                }
                throw childMustBeADeclaration(elementName);
            }
            if (elementName.equalsIgnoreCase(componentTagName)) {
                requireNotAlreadyFound();
                Map<String, String> attrs = attributes == null ? Map.of() : attributes;
                found = new Declaration(attrs.get("impl"), parsePropsAttribute(attrs.get("props")));
            }
        }

        @Override
        public void handleOpenElement(String elementName, Map<String, String> attributes, int line, int col) {
            if (insideComponent) {
                if (elementName.equalsIgnoreCase(propTagName)) {
                    throw new IllegalStateException(CompositionDialect.DIALECT_NAME + ": <" + propTagName
                            + "> in " + describeForErrors + " must be self-closing.");
                }
                throw childMustBeADeclaration(elementName);
            }
            if (elementName.equalsIgnoreCase(componentTagName)) {
                requireNotAlreadyFound();
                insideComponent = true;
                Map<String, String> attrs = attributes == null ? Map.of() : attributes;
                pendingImpl = attrs.get("impl");
                pendingAttributeProps = attrs.containsKey("props") ? parsePropsAttribute(attrs.get("props")) : null;
                pendingChildProps = new ArrayList<>();
            }
        }

        @Override
        public void handleCloseElement(String elementName, int line, int col) {
            if (insideComponent && elementName.equalsIgnoreCase(componentTagName)) {
                if (pendingAttributeProps != null && !pendingChildProps.isEmpty()) {
                    throw new IllegalStateException(CompositionDialect.DIALECT_NAME + ": " + describeForErrors
                            + " declares props both as an attribute and as <" + propTagName + "> children — "
                            + "use one or the other.");
                }
                List<PropDeclaration> props = pendingAttributeProps != null ? pendingAttributeProps : pendingChildProps;
                found = new Declaration(pendingImpl, props);
                insideComponent = false;
                pendingImpl = null;
                pendingAttributeProps = null;
                pendingChildProps = null;
            }
        }

        @Override
        public void handleText(char[] buffer, int offset, int len, int line, int col) {
            if (insideComponent && !new String(buffer, offset, len).isBlank()) {
                throw childMustBeADeclaration(null);
            }
        }

        private IllegalStateException childMustBeADeclaration(String elementName) {
            return new IllegalStateException(CompositionDialect.DIALECT_NAME + ": " + describeForErrors
                    + " has " + (elementName != null ? "<" + elementName + ">" : "markup")
                    + " inside <" + componentTagName + "> — its children must be <" + propTagName
                    + "> declarations, never markup. The component's own markup is its sibling.");
        }

        private void requireNotAlreadyFound() {
            if (found != null) {
                throw new IllegalStateException(CompositionDialect.DIALECT_NAME + ": " + describeForErrors
                        + " declares <" + componentTagName + "> more than once — at most one is allowed.");
            }
        }

        private PropDeclaration readPropDeclaration(Map<String, String> attributes) {
            Map<String, String> attrs = attributes == null ? Map.of() : attributes;
            String name = attrs.get("name");
            if (name == null || name.isBlank()) {
                throw new IllegalStateException(CompositionDialect.DIALECT_NAME + ": " + describeForErrors
                        + " has a <" + propTagName + "> with no name.");
            }
            return new PropDeclaration(name, attrs.containsKey("required"), attrs.get("default"), attrs.get("type"));
        }

        private List<PropDeclaration> parsePropsAttribute(String props) {
            if (props == null || props.isBlank()) {
                return List.of();
            }
            return Arrays.stream(props.split(","))
                    .map(String::strip)
                    .filter(name -> !name.isEmpty())
                    .map(name -> new PropDeclaration(name, false, null, null))
                    .toList();
        }
    }
}
