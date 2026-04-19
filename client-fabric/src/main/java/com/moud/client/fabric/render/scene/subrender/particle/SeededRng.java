package com.moud.client.fabric.render.scene.subrender.particle;

import java.util.Random;

public final class SeededRng {
    private final Random random;
    private final long seed;

    public SeededRng(long seed) {
        this.seed = seed;
        this.random = new Random(seed);
    }

    public long seed() {
        return seed;
    }

    public float nextFloat() {
        return random.nextFloat();
    }

    public float nextSymmetric() {
        return random.nextFloat() * 2.0f - 1.0f;
    }

    public float nextRange(float min, float max) {
        return min + random.nextFloat() * (max - min);
    }

    public int nextInt(int bound) {
        if (bound <= 0) return 0;
        return random.nextInt(bound);
    }
}
