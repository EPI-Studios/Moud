package com.moud.core.mesh.noise;

public final class FractalNoise implements NoiseSampler {
    private final NoiseSampler base;
    private final int octaves;
    private final float lacunarity;
    private final float gain;

    public FractalNoise(NoiseSampler base, int octaves, float lacunarity, float gain) {
        if (base == null) throw new IllegalArgumentException("base noise required");
        this.base = base;
        this.octaves = Math.max(1, octaves);
        this.lacunarity = lacunarity;
        this.gain = gain;
    }

    @Override
    public float sample2(float x, float y) {
        float total = 0f, amp = 1f, freq = 1f, norm = 0f;
        for (int i = 0; i < octaves; i++) {
            total += amp * base.sample2(x * freq, y * freq);
            norm += amp;
            freq *= lacunarity;
            amp *= gain;
        }
        return norm > 0f ? total / norm : 0f;
    }

    @Override
    public float sample3(float x, float y, float z) {
        float total = 0f, amp = 1f, freq = 1f, norm = 0f;
        for (int i = 0; i < octaves; i++) {
            total += amp * base.sample3(x * freq, y * freq, z * freq);
            norm += amp;
            freq *= lacunarity;
            amp *= gain;
        }
        return norm > 0f ? total / norm : 0f;
    }
}
