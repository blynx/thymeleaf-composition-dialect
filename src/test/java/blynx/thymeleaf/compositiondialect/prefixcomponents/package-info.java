/**
 * Components registered under a <em>second</em> dialect, at the prefix {@code x} — the multi-prefix
 * configuration, which is not the recommended way to keep two modules' components apart (that is the tag
 * name; see {@code libcomponents}) but which works, and whose boundary {@code MultiPrefixTest} pins.
 *
 * <p>Their templates spell the dialect's grammar with {@code x:} rather than {@code c:}, because slot
 * markers resolve under the prefix of the dialect that registered the component. That is the one thing
 * genuinely bound to a prefix, and it is why these live in their own package with their own templates path
 * instead of being {@code casescomponents} registered twice.
 *
 * <p>{@link blynx.thymeleaf.compositiondialect.prefixcomponents.Mismatched} is the deliberate exception:
 * it spells its marker {@code c:slot} and exists only to be rendered and fail.
 */
package blynx.thymeleaf.compositiondialect.prefixcomponents;
