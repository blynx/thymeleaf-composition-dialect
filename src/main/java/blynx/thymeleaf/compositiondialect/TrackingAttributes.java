package blynx.thymeleaf.compositiondialect;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Wraps the attribute map handed to a component and records which keys were read, so the
 * attributes that were never consumed can later be emitted as "rest" attributes via {@code c:rest}.
 */
public class TrackingAttributes {

    private final Map<String, Object> raw;
    private final Set<String> accessed = new HashSet<>();

    public TrackingAttributes(Map<String, Object> raw) {
        this.raw = raw;
    }

    public Object get(String name) {
        accessed.add(name);
        return raw.get(name);
    }

    public boolean containsKey(String name) {
        accessed.add(name);
        return raw.containsKey(name);
    }

    public Map<String, Object> rest() {
        var result = new HashMap<String, Object>();
        // Not Collectors.toMap: attribute values may be null, which that collector rejects.
        raw.forEach((key, value) -> {
            if (!accessed.contains(key)) {
                result.put(key, value);
            }
        });
        return result;
    }
}
