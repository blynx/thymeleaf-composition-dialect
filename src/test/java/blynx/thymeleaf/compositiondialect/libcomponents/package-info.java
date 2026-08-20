/**
 * Stands in for an imported component library — a second {@code ComponentSource}, scanned into the same
 * dialect as {@code casescomponents} and sharing its prefix. Its templates carry the library's own file
 * names ({@code ds-card.html}, giving {@code <c:ds-card>}), which is all that keeps its tags apart from the
 * application's own — the backing classes ({@code DsCard}) could be named anything at all.
 *
 * <p>Its templates live under their own path ({@code templates/libcomponents}) rather than the
 * application's, which is what a library shipping templates inside its jar looks like from here.
 */
package blynx.thymeleaf.compositiondialect.libcomponents;
