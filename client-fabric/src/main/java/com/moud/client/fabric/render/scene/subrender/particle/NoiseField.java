package com.moud.client.fabric.render.scene.subrender.particle;

public final class NoiseField {
    private NoiseField() {
    }

    public static void sampleCurl(float x, float y, float z, float[] out) {
        float eps = 0.15f;
        float n1 = noise3(x, y + eps, z) - noise3(x, y - eps, z);
        float n2 = noise3(x, y, z + eps) - noise3(x, y, z - eps);
        float n3 = noise3(x + eps, y, z) - noise3(x - eps, y, z);
        out[0] = n2 - n1;
        out[1] = n3 - n2;
        out[2] = n1 - n3;
    }

    public static float noise3(float x, float y, float z) {
        int xi = floor(x), yi = floor(y), zi = floor(z);
        float xf = x - xi, yf = y - yi, zf = z - zi;
        float u = fade(xf), v = fade(yf), w = fade(zf);
        float c000 = hash(xi, yi, zi);
        float c100 = hash(xi + 1, yi, zi);
        float c010 = hash(xi, yi + 1, zi);
        float c110 = hash(xi + 1, yi + 1, zi);
        float c001 = hash(xi, yi, zi + 1);
        float c101 = hash(xi + 1, yi, zi + 1);
        float c011 = hash(xi, yi + 1, zi + 1);
        float c111 = hash(xi + 1, yi + 1, zi + 1);
        float x00 = lerp(c000, c100, u);
        float x10 = lerp(c010, c110, u);
        float x01 = lerp(c001, c101, u);
        float x11 = lerp(c011, c111, u);
        float y0 = lerp(x00, x10, v);
        float y1 = lerp(x01, x11, v);
        return lerp(y0, y1, w) * 2.0f - 1.0f;
    }

    private static int floor(float v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }

    private static float fade(float t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float hash(int x, int y, int z) {
        int h = x * 374761393 + y * 668265263 + z * 2147483647;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xFFFF) / 65535.0f;
    }
}
