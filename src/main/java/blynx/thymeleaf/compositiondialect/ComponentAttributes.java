package blynx.thymeleaf.compositiondialect;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Wraps the attribute map handed to a component. {@code rest()} excludes whatever the component
 * declares as {@code props} — not whatever happened to be read — so a component can read an attribute
 * (to validate, log, or derive from it) without that suppressing it from {@code c:rest}, and the rest
 * set no longer depends on which branch of the constructor ran.
 */
public class ComponentAttributes {

    private final Map<String, Object> raw;
    private final Set<String> props;

    public ComponentAttributes(Map<String, Object> raw, Set<String> props) {
        this.raw = raw;
        this.props = props;
    }

    public Object get(String name) {
        return raw.get(name);
    }

    public boolean containsKey(String name) {
        return raw.containsKey(name);
    }

    public Map<String, Object> rest() {
        var result = HashMap.<String, Object>newHashMap(raw.size());
        // Not Collectors.toMap: attribute values may be null, which that collector rejects.
        raw.forEach((key, value) -> {
            if (!props.contains(key)) {
                result.put(key, value);
            }
        });
        return result;
    }
}
