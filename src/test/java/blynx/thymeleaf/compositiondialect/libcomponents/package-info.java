/**
 * Stands in for an imported component library — a second {@code ComponentSource}, scanned into the same
 * dialect as {@code casescomponents} and sharing its prefix. Its classes carry the library's own name
 * ({@code DsCard}, giving {@code <c:ds-card>}), which is all that keeps its tags apart from the
 * application's own.
 *
 * <p>Its templates live under their own path ({@code templates/libcomponents}) rather than the
 * application's, which is what a library shipping templates inside its jar looks like from here.
 */
package blynx.thymeleaf.compositiondialect.libcomponents;
