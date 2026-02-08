package com.moud.client.editor.ui;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

public final class WorldViewCapture {
    private static final Matrix4f VIEW = new Matrix4f();
    private static final Matrix4f PROJECTION = new Matrix4f();
    private static volatile boolean valid;

    private WorldViewCapture() {}

    public static void initialize() {
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            Camera camera = context.camera();
            if (camera == null) {
                valid = false;
                return;
            }

            Quaternionf rotation = new Quaternionf(camera.getRotation());
            rotation.conjugate();
            Vec3d pos = camera.getPos();

            synchronized (VIEW) {
                VIEW.identity().rotate(rotation).translate((float) -pos.x, (float) -pos.y, (float) -pos.z);
                PROJECTION.set(context.projectionMatrix());
                valid = true;
            }
        });
    }

    public static boolean copyMatrices(Matrix4f viewOut, Matrix4f projectionOut) {
        if (!valid) {
            return false;
        }
        synchronized (VIEW) {
            viewOut.set(VIEW);
            projectionOut.set(PROJECTION);
        }
        return true;
    }
}
