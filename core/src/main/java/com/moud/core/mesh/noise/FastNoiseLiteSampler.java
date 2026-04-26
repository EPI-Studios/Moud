package com.moud.core.mesh.noise;

public final class FastNoiseLiteSampler implements NoiseSampler {
    private final FastNoiseLite noise;

    private FastNoiseLiteSampler(FastNoiseLite noise) {
        this.noise = noise;
    }

    public static FastNoiseLiteSampler simplex(long seed) {
        FastNoiseLite n = base(seed);
        n.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        return new FastNoiseLiteSampler(n);
    }

    public static FastNoiseLiteSampler perlin(long seed) {
        FastNoiseLite n = base(seed);
        n.SetNoiseType(FastNoiseLite.NoiseType.Perlin);
        return new FastNoiseLiteSampler(n);
    }

    public static FastNoiseLiteSampler worley(long seed) {
        FastNoiseLite n = base(seed);
        n.SetNoiseType(FastNoiseLite.NoiseType.Cellular);
        n.SetCellularDistanceFunction(FastNoiseLite.CellularDistanceFunction.Euclidean);
        n.SetCellularReturnType(FastNoiseLite.CellularReturnType.Distance);
        return new FastNoiseLiteSampler(n);
    }

    private static FastNoiseLite base(long seed) {
        FastNoiseLite n = new FastNoiseLite((int) seed);
        n.SetFrequency(1.0f);
        n.SetFractalType(FastNoiseLite.FractalType.None);
        return n;
    }

    @Override
    public float sample2(float x, float y) {
        return noise.GetNoise(x, y);
    }

    @Override
    public float sample3(float x, float y, float z) {
        return noise.GetNoise(x, y, z);
    }
}
