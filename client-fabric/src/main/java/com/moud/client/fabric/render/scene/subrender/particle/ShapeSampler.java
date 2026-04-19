package com.moud.client.fabric.render.scene.subrender.particle;

public final class ShapeSampler {
    private ShapeSampler() {
    }

    public static void sample(EmitterConfig c, SeededRng rng, float[] out) {
        switch (c.shapeType) {
            case "sphere" -> sphere(c, rng, out);
            case "box" -> box(c, rng, out);
            case "disc" -> disc(c, rng, out);
            case "cone" -> cone(c, rng, out);
            case "ring" -> ring(c, rng, out);
            default -> { out[0] = 0f; out[1] = 0f; out[2] = 0f; }
        }
        out[0] += c.jitterX * rng.nextSymmetric();
        out[1] += c.jitterY * rng.nextSymmetric();
        out[2] += c.jitterZ * rng.nextSymmetric();
    }

    private static void sphere(EmitterConfig c, SeededRng rng, float[] out) {
        float u = rng.nextFloat() * 2f - 1f;
        float phi = (float) (rng.nextFloat() * Math.PI * 2.0);
        float s = (float) Math.sqrt(Math.max(0f, 1f - u * u));
        float r = c.surfaceEmit ? 1f : (float) Math.cbrt(rng.nextFloat());
        out[0] = c.shapeX * r * s * (float) Math.cos(phi);
        out[1] = c.shapeY * r * u;
        out[2] = c.shapeZ * r * s * (float) Math.sin(phi);
    }

    private static void box(EmitterConfig c, SeededRng rng, float[] out) {
        if (c.surfaceEmit) {
            int face = rng.nextInt(6);
            float a = rng.nextSymmetric();
            float b = rng.nextSymmetric();
            float sign = (face & 1) == 0 ? 1f : -1f;
            switch (face >> 1) {
                case 0 -> { out[0] = c.shapeX * sign; out[1] = c.shapeY * a; out[2] = c.shapeZ * b; }
                case 1 -> { out[0] = c.shapeX * a; out[1] = c.shapeY * sign; out[2] = c.shapeZ * b; }
                default -> { out[0] = c.shapeX * a; out[1] = c.shapeY * b; out[2] = c.shapeZ * sign; }
            }
        } else {
            out[0] = c.shapeX * rng.nextSymmetric();
            out[1] = c.shapeY * rng.nextSymmetric();
            out[2] = c.shapeZ * rng.nextSymmetric();
        }
    }

    private static void disc(EmitterConfig c, SeededRng rng, float[] out) {
        float r = c.surfaceEmit ? 1f : (float) Math.sqrt(rng.nextFloat());
        float a = (float) (rng.nextFloat() * Math.PI * 2.0);
        out[0] = c.shapeX * r * (float) Math.cos(a);
        out[1] = 0f;
        out[2] = c.shapeZ * r * (float) Math.sin(a);
    }

    private static void ring(EmitterConfig c, SeededRng rng, float[] out) {
        float a = (float) (rng.nextFloat() * Math.PI * 2.0);
        out[0] = c.shapeX * (float) Math.cos(a);
        out[1] = 0f;
        out[2] = c.shapeZ * (float) Math.sin(a);
    }

    private static void cone(EmitterConfig c, SeededRng rng, float[] out) {
        float h = rng.nextFloat();
        float r = (float) Math.sqrt(rng.nextFloat()) * h;
        float a = (float) (rng.nextFloat() * Math.PI * 2.0);
        out[0] = c.shapeX * r * (float) Math.cos(a);
        out[1] = c.shapeY * h;
        out[2] = c.shapeZ * r * (float) Math.sin(a);
    }
}
