package com.meekdev.moud.mod.adapter.render;

import com.meekdev.amnetic.client.framebuffer.ColorFormat;
import com.meekdev.amnetic.client.framebuffer.Framebuffer;
import com.meekdev.amnetic.client.framebuffer.FramebufferSpec;
import com.meekdev.amnetic.client.framebuffer.Framebuffers;
import com.meekdev.amnetic.client.model.Model;
import com.meekdev.amnetic.client.model.internal.OffscreenModelRenderer;
import com.meekdev.amnetic.client.pipeline.Pipeline;
import com.meekdev.amnetic.client.pipeline.RenderStage;
import com.meekdev.amnetic.client.render.GlState;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.adapter.gl.Textures;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;

public final class ModelSnapshots {

    public static final int SIZE = 128;
    private static final int GIVE_UP_AFTER = 240;

    private record Request(String res, Path out) {}

    private static final Deque<Request> QUEUE = new ArrayDeque<>();
    private static final Set<Path> ASKED = new HashSet<>();
    private static final Map<Path, Integer> WAITED = new HashMap<>();
    private static final OffscreenModelRenderer RENDERER = new OffscreenModelRenderer();
    private static Framebuffer target;

    private ModelSnapshots() {}

    public static void register() {
        Pipeline.add(RenderStage.SETUP, 6, "moud model thumbnails", ctx -> EditorView.filled(ModelSnapshots::drain));
    }

    public static void request(String res, Path out) {
        if (ASKED.add(out)) QUEUE.addLast(new Request(res, out));
    }

    private static void drain() {
        Request request = QUEUE.pollFirst();
        if (request == null) return;
        Model model = Meshes.modelFor(request.res());
        if (model == null || !model.isReady()) {
            int waited = WAITED.merge(request.out(), 1, Integer::sum);
            if (waited < GIVE_UP_AFTER && model != null) QUEUE.addLast(request);
            return;
        }
        WAITED.remove(request.out());
        try {
            snapshot(model, request.out());
        } catch (RuntimeException | IOException e) {
            MoudMod.LOG.warn("could not make a thumbnail of {}: {}", request.res(), e.getMessage());
        } finally {
            GlState.endFullscreen();
        }
    }

    private static void snapshot(Model model, Path out) throws IOException {
        if (target == null) target = Framebuffers.fixed(SIZE, SIZE, FramebufferSpec.builder().color(ColorFormat.RGBA8).depthTexture().build());
        Vector3f min = model.boundsMin();
        Vector3f max = model.boundsMax();
        Vector3 size = new Vector3(Math.max(1e-3, max.x - min.x), Math.max(1e-3, max.y - min.y), Math.max(1e-3, max.z - min.z));
        double radius = size.length() * 0.5;
        Matrix4f world = Meshes.placement(CFrame.IDENTITY, size, model, new Matrix4f());
        float fov = (float) Math.toRadians(35);
        double distance = radius / Math.sin(fov * 0.5) * 1.02;
        Vector3f eye = new Vector3f(1, 0.75f, 1).normalize().mul((float) distance);
        Matrix4f projView = new Matrix4f().perspective(fov, 1, 0.01f, (float) (distance + radius * 4), RenderSystem.getDevice().isZZeroToOne())
                .mul(new Matrix4f().lookAt(eye, new Vector3f(), new Vector3f(0, 1, 0)));
        target.begin();
        target.clear(0, 0, 0, 0);
        RENDERER.draw(model.internalGpu(), projView, world, null, 15, 15, 1);
        ByteBuffer pixels = BufferUtils.createByteBuffer(SIZE * SIZE * 4);
        Textures.readPixels(0, 0, SIZE, SIZE, pixels);
        target.end();
        try (NativeImage image = new NativeImage(SIZE, SIZE, false)) {
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    int at = (x + y * SIZE) * 4;
                    int r = pixels.get(at) & 0xFF;
                    int g = pixels.get(at + 1) & 0xFF;
                    int b = pixels.get(at + 2) & 0xFF;
                    int a = pixels.get(at + 3) & 0xFF;
                    image.setPixelABGR(x, SIZE - 1 - y, a << 24 | b << 16 | g << 8 | r);
                }
            }
            Files.createDirectories(out.getParent());
            image.writeToFile(out);
        }
    }
}
