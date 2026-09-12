package com.meekdev.moud.net.wire;

import com.meekdev.moud.core.math.Quat;

// a rotation in thirty two bits, which is what §9.1 asks for
//
// the trick, and it is the standard one: a unit quaternion has four components and only three degrees
// of freedom, so the largest is dropped and rebuilt from the other three. two bits say which one was
// dropped and each of the remaining three gets ten, scaled by root two -- because if one component is
// the largest, none of the others can exceed 1/sqrt(2)
//
// the sign of the dropped component is thrown away too, and that costs nothing: q and -q are the same
// rotation, so it is always rebuilt positive
public final class Quats {

    private static final double LIMIT = 1.0 / Math.sqrt(2.0);

    // ten bits is a thousand and twenty four levels, and they are used as 512 either side of a middle
    // that is exactly zero. spending them as 0..511 was half the range and, worse, put no level on
    // zero at all -- so an identity rotation came back as a small turn
    private static final int HALF = 512;
    private static final int STEPS = 511;

    private Quats() {}

    public static void write(Bytes out, Quat q) {
        double[] parts = {q.x(), q.y(), q.z(), q.w()};
        int largest = 0;
        for (int n = 1; n < 4; n++) {
            if (Math.abs(parts[n]) > Math.abs(parts[largest])) largest = n;
        }
        // flipped so the dropped one is positive, which is the same rotation and saves its sign
        double sign = parts[largest] < 0 ? -1 : 1;

        int packed = largest;
        int shift = 2;
        for (int n = 0; n < 4; n++) {
            if (n == largest) continue;
            packed |= quantise(parts[n] * sign) << shift;
            shift += 10;
        }
        out.u32(packed);
    }

    public static Quat read(Bytes in) {
        int packed = in.readU32();
        int largest = packed & 0x3;

        double[] parts = new double[4];
        double squared = 0;
        int shift = 2;
        for (int n = 0; n < 4; n++) {
            if (n == largest) continue;
            parts[n] = dequantise((packed >>> shift) & 0x3FF);
            squared += parts[n] * parts[n];
            shift += 10;
        }
        parts[largest] = Math.sqrt(Math.max(0, 1.0 - squared));
        return new Quat(parts[0], parts[1], parts[2], parts[3]);
    }

    private static int quantise(double value) {
        double clamped = Math.clamp(value, -LIMIT, LIMIT);
        return (int) (Math.round(clamped / LIMIT * STEPS) + HALF) & 0x3FF;
    }

    private static double dequantise(int stored) {
        return (stored - HALF) / (double) STEPS * LIMIT;
    }
}
