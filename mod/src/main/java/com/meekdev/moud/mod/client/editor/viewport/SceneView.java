package com.meekdev.moud.mod.client.editor.viewport;

import com.meekdev.amnetic.client.camera.AmneticCamera;
import com.meekdev.moud.core.math.Vector3;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

public final class SceneView {

    private static final float BEHIND = 1.0e-4f;

    final float[] view = new float[16];
    final float[] projection = new float[16];
    private final Matrix4f viewProjection = new Matrix4f();
    private final Matrix4f inverse = new Matrix4f();
    private double cameraX;
    private double cameraY;
    private double cameraZ;
    private float originX;
    private float originY;
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
        return true;
    }

    void frame(float x, float y, float w, float h) {
        originX = x;
        originY = y;
        width = w;
        height = h;
    }

    public float[] viewMatrix() {
        return view;
    }

    public float[] projectionMatrix() {
        return projection;
    }

    public float originX() {
        return originX;
    }

    public float originY() {
        return originY;
    }

    public float width() {
        return width;
    }

    public float height() {
        return height;
    }

    public Vector3 cameraPosition() {
        return new Vector3(cameraX, cameraY, cameraZ);
    }

    public float @Nullable [] toScreen(Vector3 world) {
        Vector4f clip = new Vector4f((float) (world.x() - cameraX), (float) (world.y() - cameraY), (float) (world.z() - cameraZ), 1.0f);
        viewProjection.transform(clip);
        if (clip.w <= BEHIND) return null;
        return new float[] {originX + (clip.x / clip.w * 0.5f + 0.5f) * width, originY + (1.0f - (clip.y / clip.w * 0.5f + 0.5f)) * height};
    }

    public Vector3 rayOrigin(float screenX, float screenY) {
        float ndcX = (screenX - originX) / width * 2.0f - 1.0f;
        float ndcY = 1.0f - (screenY - originY) / height * 2.0f;
        Vector4f near = inverse.transform(new Vector4f(ndcX, ndcY, AmneticCamera.isOrthographic() ? -1.0f : 0.0f, 1.0f));
        near.div(near.w);
        if (!AmneticCamera.isOrthographic()) return cameraPosition();
        return new Vector3(near.x + cameraX, near.y + cameraY, near.z + cameraZ);
    }

    public Vector3 rayDirection(float screenX, float screenY) {
        float ndcX = (screenX - originX) / width * 2.0f - 1.0f;
        float ndcY = 1.0f - (screenY - originY) / height * 2.0f;
        Vector4f near = inverse.transform(new Vector4f(ndcX, ndcY, -1.0f, 1.0f));
        Vector4f far = inverse.transform(new Vector4f(ndcX, ndcY, 1.0f, 1.0f));
        near.div(near.w);
        far.div(far.w);
        return new Vector3(far.x - near.x, far.y - near.y, far.z - near.z).normalize();
    }
}
