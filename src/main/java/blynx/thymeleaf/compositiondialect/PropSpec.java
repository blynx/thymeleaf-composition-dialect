package blynx.thymeleaf.compositiondialect;

/**
 * One classless prop's declared shape — name, whether it is required, its literal default (if any), and
 * the type its value is coerced to or checked against (if any, resolved to a {@link Class} at scan time).
 * Only ever populated for a classless component (no {@code impl} bound); a class-bound component's props
 * come from its own field or record-component types instead, resolved by reflection, not from here.
 */
record PropSpec(String kebabName, boolean required, String defaultValue, Class<?> type) {
}
