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

    /**
     * Computed on first ask and kept: both inputs are fixed for this occurrence, so every later ask —
     * {@code c:rest} on one element, {@code ${this.restAttributes}} on another — has the same answer.
     * Not synchronized, and it needs not be: one of these belongs to one component occurrence, which one
     * thread renders.
     */
    private Map<String, Object> rest;

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
        Map<String, Object> computed = rest;
        if (computed != null) {
            return computed;
        }
        var result = HashMap.<String, Object>newHashMap(raw.size());
        // Not Collectors.toMap: attribute values may be null, which that collector rejects.
        raw.forEach((key, value) -> {
            if (!props.contains(key)) {
                result.put(key, value);
            }
        });
        rest = result;
        return result;
    }
}
