package com.meekdev.moud.mod.adapter.render;

import com.meekdev.amnetic.client.ibl.Ibl;
import com.meekdev.moud.core.image.Radiance;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.render.Sky;
import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.adapter.gl.Textures;
import com.meekdev.moud.mod.client.PlaceFiles;
import java.nio.FloatBuffer;
import java.util.concurrent.CompletableFuture;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

// the Sky's hdri: read and decoded off the render thread, then uploaded once, drawn by SkyBox and
// handed to the renderer's sky probe so models and pbr parts are lit by it
public final class SkyPanorama {

    private static String wanted = "";
    private static @Nullable CompletableFuture<Radiance.@Nullable Image> loading;
    private static int texture;
    private static int width;
    private static final Matrix4f TURN = new Matrix4f();
    private static final Matrix4f INVERSE = new Matrix4f();

    private SkyPanorama() {}

    public static int texture() {
        return texture;
    }

    // world direction to panorama direction
    public static Matrix4f inverseTurn() {
        return INVERSE;
    }

    public static void frame(@Nullable Sky sky) {
        String hdri = sky == null ? "" : sky.hdri;
        if (!hdri.equals(wanted)) load(hdri);
        if (loading != null && loading.isDone()) {
            Radiance.Image image = loading.join();
            loading = null;
            if (image != null) upload(image);
        }
        if (texture == 0 || sky == null) {
            Ibl.proceduralSky();
            return;
        }
        Vector3 degrees = sky.skyboxOrientation;
        TURN.identity().rotateXYZ((float) Math.toRadians(degrees.x()), (float) Math.toRadians(degrees.y()),
                (float) Math.toRadians(degrees.z()));
        TURN.invert(INVERSE);
        Ibl.panorama(texture, width, INVERSE);
    }

    private static void load(String hdri) {
        wanted = hdri;
        Textures.delete(texture);
        texture = 0;
        width = 0;
        loading = null;
        if (hdri.isEmpty()) return;
        loading = CompletableFuture.supplyAsync(() -> {
            byte[] bytes = PlaceFiles.read(hdri);
            if (bytes == null) {
                MoudMod.LOG.warn("the hdri {} is not in the place", hdri);
                return null;
            }
            try {
                return Radiance.decode(bytes);
            } catch (IllegalArgumentException e) {
                MoudMod.LOG.warn("could not read the hdri {}: {}", hdri, e.getMessage());
                return null;
            }
        });
    }

    private static void upload(Radiance.Image image) {
        FloatBuffer rgb = MemoryUtil.memAllocFloat(image.rgb().length);
        try {
            rgb.put(image.rgb()).flip();
            texture = Textures.panorama(image.width(), image.height(), rgb);
            width = image.width();
        } finally {
            MemoryUtil.memFree(rgb);
        }
    }
}
