package com.moud.core.net;

/**
 * Smallest-3 quaternion compression to/from a 32-bit packed integer.
 *
 * <p>Layout:
 * <pre>
 *   bits  0..9   = signed component[0]   (10-bit signed, range -512..511)
 *   bits 10..19  = signed component[1]
 *   bits 20..29  = signed component[2]
 *   bits 30..31  = unsigned largest-component index (0..3)
 * </pre>
 *
 * <p>The dropped (largest) component is always non-negative when reconstructed:
 * if it would be negative, the whole quaternion is negated before encoding,
 * which is safe since {@code q} and {@code -q} represent the same rotation.
 */
public final class QuatSmallest3 {

    private static final float INV_SQRT2 = 0.70710677f;

    private QuatSmallest3() {}

    public static int encode(float x, float y, float z, float w) {
        int largest = 0;
        float maxAbs = Math.abs(x);
        if (Math.abs(y) > maxAbs) { largest = 1; maxAbs = Math.abs(y); }
        if (Math.abs(z) > maxAbs) { largest = 2; maxAbs = Math.abs(z); }
        if (Math.abs(w) > maxAbs) { largest = 3; }

        float lx = x, ly = y, lz = z, lw = w;
        float largestVal = switch (largest) { case 0 -> x; case 1 -> y; case 2 -> z; default -> w; };
        if (largestVal < 0f) { lx = -lx; ly = -ly; lz = -lz; lw = -lw; }

        float a, b, c;
        switch (largest) {
            case 0  -> { a = ly; b = lz; c = lw; }
            case 1  -> { a = lx; b = lz; c = lw; }
            case 2  -> { a = lx; b = ly; c = lw; }
            default -> { a = lx; b = ly; c = lz; }
        }
        int ai = quantize(a);
        int bi = quantize(b);
        int ci = quantize(c);
        return (ai & 0x3FF) | ((bi & 0x3FF) << 10) | ((ci & 0x3FF) << 20) | ((largest & 0x3) << 30);
    }

    public static void decode(int packed, float[] out4) {
        int ai = signExtend10(packed & 0x3FF);
        int bi = signExtend10((packed >>> 10) & 0x3FF);
        int ci = signExtend10((packed >>> 20) & 0x3FF);
        int largest = (packed >>> 30) & 0x3;
        float a = dequantize(ai);
        float b = dequantize(bi);
        float c = dequantize(ci);
        float d = (float) Math.sqrt(Math.max(0f, 1f - (a * a + b * b + c * c)));
        switch (largest) {
            case 0  -> { out4[0] = d; out4[1] = a; out4[2] = b; out4[3] = c; }
            case 1  -> { out4[0] = a; out4[1] = d; out4[2] = b; out4[3] = c; }
            case 2  -> { out4[0] = a; out4[1] = b; out4[2] = d; out4[3] = c; }
            default -> { out4[0] = a; out4[1] = b; out4[2] = c; out4[3] = d; }
        }
    }

    private static int quantize(float v) {
        float scaled = (v / INV_SQRT2) * 511f;
        return Math.max(-511, Math.min(511, Math.round(scaled)));
    }

    private static int signExtend10(int x) { return (x << 22) >> 22; }

    private static float dequantize(int q) { return (q / 511f) * INV_SQRT2; }
}
