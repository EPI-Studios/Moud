package com.meekdev.moud.mod.adapter.render.effect;

import com.meekdev.amnetic.client.instanced.internal.MainTargetFramebuffer;
import com.meekdev.amnetic.client.render.CameraSnapshot;
import com.meekdev.amnetic.client.render.GlState;
import com.meekdev.amnetic.client.render.ShaderProgram;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.mod.adapter.gl.Programs;
import com.meekdev.moud.mod.adapter.gl.RenderState;
import com.meekdev.moud.mod.adapter.gl.Textures;
import com.meekdev.moud.mod.adapter.gl.VertexArray;
import com.meekdev.moud.mod.adapter.render.Parts;
import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.resources.Identifier;
import org.lwjgl.system.MemoryUtil;

final class QuadBatch {

    enum Layer { SURFACE, WORLD, ON_TOP }

    private record Key(Layer layer, int texture, boolean nearest) {}

    private static final class Run {
        float[] data = new float[FLOATS * 6 * 64];
        int size;

        void put(float[] vertex) {
            if (size + FLOATS > data.length) data = Arrays.copyOf(data, data.length * 2);
            System.arraycopy(vertex, 0, data, size, FLOATS);
            size += FLOATS;
        }
    }

    private static final int FLOATS = 13;
    private static final Identifier VERTEX = Identifier.fromNamespaceAndPath("moud", "shaders/effect/quad.vsh");
    private static final Identifier FRAGMENT = Identifier.fromNamespaceAndPath("moud", "shaders/effect/quad.fsh");

    private final Map<Key, Run> runs = new LinkedHashMap<>();
    private final float[][] corners = new float[4][FLOATS];
    private int staged;
    private double originX;
    private double originY;
    private double originZ;
    private Key key;
    private float red = 1;
    private float green = 1;
    private float blue = 1;
    private float alpha = 1;
    private float blockLight = 240;
    private float skyLight = 240;
    private float influence;
    private float emission;

    private ShaderProgram program;
    private VertexArray vertices;
    private int nearestSampler;
    private int linearSampler;
    private int lightSampler;
    private FloatBuffer upload = MemoryUtil.memAllocFloat(FLOATS * 6 * 256);

    void begin(double eyeX, double eyeY, double eyeZ) {
        for (Run run : runs.values()) run.size = 0;
        originX = eyeX;
        originY = eyeY;
        originZ = eyeZ;
        staged = 0;
    }

    void use(Layer layer, EffectTextures.Sprite sprite) {
        key = new Key(layer, sprite.gl(), sprite.nearest());
    }

    void tint(Color color, double opacity, double brightness) {
        float gain = (float) brightness;
        red = color.r() * gain;
        green = color.g() * gain;
        blue = color.b() * gain;
        alpha = (float) Math.clamp(opacity, 0, 1) * color.a();
    }

    void light(int packed, double influence, double emission) {
        blockLight = packed & 0xFFFF;
        skyLight = (packed >> 16) & 0xFFFF;
        this.influence = (float) Math.clamp(influence, 0, 1);
        this.emission = (float) Math.clamp(emission, 0, 1);
    }

    void corner(Vector3 at, double u, double v) {
        corner(at.x(), at.y(), at.z(), u, v);
    }

    void corner(double x, double y, double z, double u, double v) {
        float[] c = corners[staged++];
        c[0] = (float) (x - originX);
        c[1] = (float) (y - originY);
        c[2] = (float) (z - originZ);
        c[3] = (float) u;
        c[4] = (float) v;
        c[5] = red;
        c[6] = green;
        c[7] = blue;
        c[8] = alpha;
        c[9] = blockLight;
        c[10] = skyLight;
        c[11] = influence;
        c[12] = emission;
        if (staged < 4) return;
        staged = 0;
        if (corners[0][8] <= 0 && corners[1][8] <= 0 && corners[2][8] <= 0 && corners[3][8] <= 0) return;
        Run run = runs.computeIfAbsent(key, k -> new Run());
        run.put(corners[0]);
        run.put(corners[1]);
        run.put(corners[2]);
        run.put(corners[0]);
        run.put(corners[2]);
        run.put(corners[3]);
    }

    void draw(CameraSnapshot camera) {
        int total = 0;
        for (Run run : runs.values()) total += run.size;
        if (total == 0) return;
        ensure();
        if (upload.capacity() < total) {
            MemoryUtil.memFree(upload);
            upload = MemoryUtil.memAllocFloat(Math.max(total, upload.capacity() * 2));
        }
        upload.clear();
        for (Run run : runs.values()) upload.put(run.data, 0, run.size);
        upload.flip();

        int previous = MainTargetFramebuffer.bind();
        try {
            program.begin();
            program.setMatrix4("ViewProj", camera.viewProj);
            program.setSampler("Sprite", 0);
            program.setSampler("LightMap", 1);
            vertices.stream(upload);

            GlState.bindTexture(1, Parts.lightMap());
            Textures.bindSampler(1, lightSampler);
            RenderState.blend(true);
            RenderState.premultipliedBlend();
            RenderState.cull(false);
            RenderState.depthMask(false);
            RenderState.depthLessOrEqual();

            for (Layer layer : Layer.values()) {
                RenderState.depthTest(layer != Layer.ON_TOP);
                if (layer == Layer.SURFACE) RenderState.polygonOffset();
                else RenderState.noPolygonOffset();
                int first = 0;
                for (Map.Entry<Key, Run> entry : runs.entrySet()) {
                    int count = entry.getValue().size / FLOATS;
                    Key k = entry.getKey();
                    if (k.layer() == layer && count > 0) {
                        GlState.bindTexture(0, k.texture());
                        Textures.bindSampler(0, k.nearest() ? nearestSampler : linearSampler);
                        vertices.draw(first, count);
                    }
                    first += count;
                }
            }
        } finally {
            RenderState.noPolygonOffset();
            Textures.bindSampler(0, 0);
            Textures.bindSampler(1, 0);
            VertexArray.unbind();
            Programs.use(0);
            RenderState.depthMask(true);
            RenderState.depthTest(true);
            RenderState.blend(false);
            MainTargetFramebuffer.restore(previous);
        }
        runs.values().removeIf(run -> run.size == 0);
    }

    private void ensure() {
        if (program != null) return;
        program = new ShaderProgram(VERTEX, FRAGMENT);
        vertices = VertexArray.interleaved(3, 2, 4, 4);
        nearestSampler = Textures.sampler(Textures.NEAREST, Textures.REPEAT);
        linearSampler = Textures.sampler(Textures.LINEAR, Textures.REPEAT);
        lightSampler = Textures.sampler(Textures.LINEAR, Textures.CLAMP);
    }
}
