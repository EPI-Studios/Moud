package com.meekdev.moud.mod.client.editor.viewport;

import com.meekdev.amnetic.client.camera.AmneticCamera;
import com.meekdev.moud.core.math.Vector3;
import imgui.ImGui;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

final class SceneView {

    private static final float BEHIND = 1.0e-4f;

    final float[] view = new float[16];
    final float[] projection = new float[16];
    private final Matrix4f viewProjection = new Matrix4f();
    private final Matrix4f inverse = new Matrix4f();
    private double cameraX;
    private double cameraY;
    private double cameraZ;
    private float width;
    private float height;

    boolean capture() {
        if (!AmneticCamera.isReady()) return false;
        Vec3 camera = AmneticCamera.position();
        cameraX = camera.x;
        cameraY = camera.y;
        cameraZ = camera.z;
        Matrix4f rotation = AmneticCamera.view();
        Matrix4f projectionMatrix = AmneticCamera.projection();
        rotation.get(view);
        projectionMatrix.get(projection);
        viewProjection.set(projectionMatrix).mul(rotation);
        viewProjection.invert(inverse);
        width = ImGui.getIO().getDisplaySizeX();
        height = ImGui.getIO().getDisplaySizeY();
        return width > 0 && height > 0;
    }

    float width() {
        return width;
    }

    float height() {
        return height;
    }

    Vector3 cameraPosition() {
        return new Vector3(cameraX, cameraY, cameraZ);
    }

    float @Nullable [] toScreen(Vector3 world) {
        Vector4f clip = new Vector4f((float) (world.x() - cameraX), (float) (world.y() - cameraY), (float) (world.z() - cameraZ), 1.0f);
        viewProjection.transform(clip);
        if (clip.w <= BEHIND) return null;
        return new float[] {(clip.x / clip.w * 0.5f + 0.5f) * width, (1.0f - (clip.y / clip.w * 0.5f + 0.5f)) * height};
    }

    Vector3 rayDirection(float screenX, float screenY) {
        float ndcX = screenX / width * 2.0f - 1.0f;
        float ndcY = 1.0f - screenY / height * 2.0f;
        Vector4f near = inverse.transform(new Vector4f(ndcX, ndcY, -1.0f, 1.0f));
        Vector4f far = inverse.transform(new Vector4f(ndcX, ndcY, 1.0f, 1.0f));
        near.div(near.w);
        far.div(far.w);
        return new Vector3(far.x - near.x, far.y - near.y, far.z - near.z).normalize();
    }
}
