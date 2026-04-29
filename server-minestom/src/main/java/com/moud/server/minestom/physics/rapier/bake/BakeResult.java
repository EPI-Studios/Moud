package com.moud.server.minestom.physics.rapier.bake;

/**
 * Output of an off-thread {@link BakeJob}: the resulting {@link SectionTrimesh}
 * paired with the section key it belongs to. Drained on the server tick thread
 * by the chunk collider manager and applied as an atomic body swap.
 */
public record BakeResult(long sectionPos, SectionTrimesh trimesh) {
}
