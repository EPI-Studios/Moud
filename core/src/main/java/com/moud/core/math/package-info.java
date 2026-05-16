/**
 * Moud core math types and conventions.
 *
 * <h2>Yaw convention</h2>
 *
 * Moud uses a right-handed Y-up coordinate system; all internal yaw values are in
 * Moud convention. Minecraft uses a left-handed yaw (opposite sign). Crossing the
 * boundary in either direction MUST go through {@link YawConvention#mcFromMoud}
 * or {@link YawConvention#moudFromMc}; never write an inline {@code -yaw} negate.
 *
 * Two boundary points exist:
 * <ul>
 *   <li>Camera composition (Moud world rotation → MC camera yaw).</li>
 *   <li>PlayerInput packet build (whatever side reads the value at boundary).</li>
 * </ul>
 *
 * Any new boundary point must add a named call here, not duplicate the negate.
 * Reviews should reject raw {@code -yaw} arithmetic.
 */
package com.moud.core.math;
