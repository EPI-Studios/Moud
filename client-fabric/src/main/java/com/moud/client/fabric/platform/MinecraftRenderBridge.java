package com.moud.client.fabric.platform;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

public final class MinecraftRenderBridge {
    private MinecraftRenderBridge() {
    }

    public static Vector3f cameraPos() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.gameRenderer == null) {
            return null;
        }
        Camera camera = client.gameRenderer.getCamera();
        if (camera == null) {
            return null;
        }
        Vec3d pos = camera.getPos();
        return new Vector3f((float) pos.x, (float) pos.y, (float) pos.z);
    }

    public static Matrix4f viewProjection(float fovDeg, float aspect, Vector3f focusWorld) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.gameRenderer == null) {
            return null;
        }
        Camera camera = client.gameRenderer.getCamera();
        if (camera == null) {
            return null;
        }

        Vec3d pos = camera.getPos();
        Matrix4f proj = new Matrix4f()
                .perspective((float) Math.toRadians(fovDeg), Math.max(0.01f, aspect), 0.05f, 512.0f);

        Quaternionf q = new Quaternionf(camera.getRotation());
        Matrix4f vpA = new Matrix4f(proj).mul(new Matrix4f()
                .rotate(q)
                .translate((float) -pos.x, (float) -pos.y, (float) -pos.z));
        Matrix4f vpB = new Matrix4f(proj).mul(new Matrix4f()
                .rotate(new Quaternionf(q).conjugate())
                .translate((float) -pos.x, (float) -pos.y, (float) -pos.z));

        float yawRad = (float) Math.toRadians(camera.getYaw() + 180.0f);
        float pitchRad = (float) Math.toRadians(camera.getPitch());
        Matrix4f vpC = new Matrix4f(proj).mul(new Matrix4f()
                .rotateY(yawRad)
                .rotateX(pitchRad)
                .translate((float) -pos.x, (float) -pos.y, (float) -pos.z));
        Matrix4f vpD = new Matrix4f(proj).mul(new Matrix4f()
                .rotateX(pitchRad)
                .rotateY(yawRad)
                .translate((float) -pos.x, (float) -pos.y, (float) -pos.z));

        if (focusWorld == null) {
            return vpB;
        }

        Matrix4f best = vpB;
        float bestScore = projectionScore(vpB, focusWorld);

        float scoreA = projectionScore(vpA, focusWorld);
        if (scoreA > bestScore) {
            bestScore = scoreA;
            best = vpA;
        }

        float scoreC = projectionScore(vpC, focusWorld);
        if (scoreC > bestScore) {
            bestScore = scoreC;
            best = vpC;
        }

        float scoreD = projectionScore(vpD, focusWorld);
        if (scoreD > bestScore) {
            best = vpD;
        }

        return best;
    }

    private static float projectionScore(Matrix4f viewProj, Vector3f p) {
        if (viewProj == null || p == null) {
            return Float.NEGATIVE_INFINITY;
        }
        Vector4f clip = new Vector4f(p.x, p.y, p.z, 1.0f).mul(viewProj);
        if (!(clip.w > 1e-6f)) {
            return Float.NEGATIVE_INFINITY;
        }
        float ndcX = clip.x / clip.w;
        float ndcY = clip.y / clip.w;
        float ndcZ = clip.z / clip.w;
        float score = clip.w;
        if (ndcZ >= -1.25f && ndcZ <= 1.25f) score += 10.0f;
        if (Math.abs(ndcX) <= 1.25f) score += 5.0f;
        if (Math.abs(ndcY) <= 1.25f) score += 5.0f;
        return score;
    }
}

