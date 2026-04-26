package com.moud.server.minestom.scripting.api.modules.mesh;

import com.moud.core.mesh.noise.FractalNoise;
import com.moud.core.mesh.noise.FastNoiseLiteSampler;
import com.moud.core.mesh.noise.NoiseSampler;
import com.moud.core.scripts.luau.LuauExport;
import org.graalvm.polyglot.HostAccess;

@LuauExport(name = "NoiseHandle", doc = "Sampler for procedural noise (perlin, simplex, worley) with fractal octaves.")
public final class NoiseHandle {
    private final NoiseSampler sampler;

    NoiseHandle(NoiseSampler sampler) {
        this.sampler = sampler;
    }

    public static NoiseHandle perlin(long seed) {
        return new NoiseHandle(FastNoiseLiteSampler.perlin(seed));
    }

    public static NoiseHandle simplex(long seed) {
        return new NoiseHandle(FastNoiseLiteSampler.simplex(seed));
    }

    public static NoiseHandle worley(long seed) {
        return new NoiseHandle(FastNoiseLiteSampler.worley(seed));
    }

    public static NoiseHandle fractal(NoiseSampler base, int octaves, double lacunarity, double gain) {
        return new NoiseHandle(new FractalNoise(base, octaves, (float) lacunarity, (float) gain));
    }

    NoiseSampler sampler() {
        return sampler;
    }

    @HostAccess.Export
    @LuauExport
    public NoiseHandle fractalize(int octaves, double lacunarity, double gain) {
        return fractal(sampler, octaves, lacunarity, gain);
    }

    @HostAccess.Export
    @LuauExport
    public double sample2(double x, double y) {
        return sampler.sample2((float) x, (float) y);
    }

    @HostAccess.Export
    @LuauExport
    public double sample3(double x, double y, double z) {
        return sampler.sample3((float) x, (float) y, (float) z);
    }

    @HostAccess.Export
    @LuauExport
    public double[] heightmap(int cols, double spacing, double originX, double originZ,
                              double frequency, double heightScale, double heightBias) {
        if (cols <= 0) return new double[0];
        double[] out = new double[cols * cols];
        for (int z = 0; z < cols; z++) {
            for (int x = 0; x < cols; x++) {
                float wx = (float) ((originX + x * spacing) * frequency);
                float wz = (float) ((originZ + z * spacing) * frequency);
                float n = sampler.sample2(wx, wz);
                out[z * cols + x] = heightBias + n * heightScale;
            }
        }
        return out;
    }
}
