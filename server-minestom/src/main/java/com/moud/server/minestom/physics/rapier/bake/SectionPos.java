package com.moud.server.minestom.physics.rapier.bake;

/**
 * Packs a {@code (chunkX, sectionY, chunkZ)} triple into a single {@code long}
 * for use as a map key.
 *
 * <p>Layout (high to low):
 * <ul>
 *     <li>26 bits - signed {@code chunkX} (range [-2^25, 2^25 - 1])</li>
 *     <li>12 bits - signed {@code sectionY} (range [-2048, 2047])</li>
 *     <li>26 bits - signed {@code chunkZ} (range [-2^25, 2^25 - 1])</li>
 * </ul>
 */
public final class SectionPos {

    private static final int X_SHIFT = 38;
    private static final int Y_SHIFT = 26;
    private static final long X_MASK = 0x3FFFFFFL; // 26 bits
    private static final long Y_MASK = 0xFFFL;     // 12 bits
    private static final long Z_MASK = 0x3FFFFFFL; // 26 bits

    private SectionPos() {
    }

    public static long pack(int chunkX, int sectionY, int chunkZ) {
        return ((long) (chunkX & X_MASK) << X_SHIFT)
                | ((long) (sectionY & Y_MASK) << Y_SHIFT)
                | ((long) (chunkZ & Z_MASK));
    }

    public static int chunkX(long packed) {
        // Extract 26 bits, sign-extend by left-shifting to MSB then arithmetic right-shift.
        int raw = (int) ((packed >>> X_SHIFT) & X_MASK);
        return (raw << 6) >> 6;
    }

    public static int sectionY(long packed) {
        int raw = (int) ((packed >>> Y_SHIFT) & Y_MASK);
        return (raw << 20) >> 20;
    }

    public static int chunkZ(long packed) {
        int raw = (int) (packed & Z_MASK);
        return (raw << 6) >> 6;
    }
}
