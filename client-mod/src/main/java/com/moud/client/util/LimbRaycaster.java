package com.moud.client.util;

import com.moud.client.animation.AnimatedPlayerModel;
import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranimcore.bones.PlayerAnimBone;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.Map;

public final class LimbRaycaster {

    public static final Map<String, float[]> LIMB_BOUNDS = Map.of(
            "head",      new float[]{-4, -8, -4, 4, 0, 4},
            "body",      new float[]{-4, -12, -2, 4, 0, 2},
            "right_arm", new float[]{-3, -12, -2, 1, 0, 2},
            "left_arm",  new float[]{-1, -12, -2, 3, 0, 2},
            "right_leg", new float[]{-2, -12, -2, 2, 0, 2},
            "left_leg",  new float[]{-2, -12, -2, 2, 0, 2}
    );

    public record LimbHit(String boneName, float distance) {}

    private LimbRaycaster() {}

    public static LimbHit raycast(AnimatedPlayerModel model, Vec3d rayOrigin, Vec3d rayDir, float tickDelta) {
        PlayerAnimationController controller = model.getAnimationController();

        double lerpX = model.getInterpolatedX(tickDelta);
        double lerpY = model.getInterpolatedY(tickDelta);
        double lerpZ = model.getInterpolatedZ(tickDelta);
        float bodyYaw = model.getInterpolatedYaw(tickDelta);

        PlayerAnimBone bodyBone = controller.get3DTransform(new PlayerAnimBone("body"));

        String closestBone = null;
        float closestDist = Float.MAX_VALUE;

        for (String boneName : LIMB_BOUNDS.keySet()) {
            Matrix4f boneMatrix = buildBoneWorldMatrix(controller, boneName, lerpX, lerpY, lerpZ, bodyYaw, bodyBone);

            Matrix4f invMatrix = new Matrix4f(boneMatrix).invert();

            Vector4f localOrigin4 = new Vector4f((float) rayOrigin.x, (float) rayOrigin.y, (float) rayOrigin.z, 1.0f).mul(invMatrix);
            Vector3f localOrigin = new Vector3f(localOrigin4.x, localOrigin4.y, localOrigin4.z);

            Vector4f localDir4 = new Vector4f((float) rayDir.x, (float) rayDir.y, (float) rayDir.z, 0.0f).mul(invMatrix);
            Vector3f localDir = new Vector3f(localDir4.x, localDir4.y, localDir4.z).normalize();

            float[] b = LIMB_BOUNDS.get(boneName);
            float dist = intersectAABB(localOrigin, localDir,
                    b[0] / 16f, b[1] / 16f, b[2] / 16f,
                    b[3] / 16f, b[4] / 16f, b[5] / 16f
            );

            if (dist >= 0 && dist < closestDist) {
                closestDist = dist;
                closestBone = boneName;
            }
        }

        return closestBone != null ? new LimbHit(closestBone, closestDist) : null;
    }

    public static Matrix4f buildBoneWorldMatrix(PlayerAnimationController controller, String boneName, double x, double y, double z, float bodyYaw, PlayerAnimBone bodyBone) {
        Matrix4f mat = getBoneParentMatrix(controller, boneName, x, y, z, bodyYaw, bodyBone);
        PlayerAnimBone bone = controller.get3DTransform(new PlayerAnimBone(boneName));
        mat.translate(bone.getPosX() / 16f, bone.getPosY() / 16f, bone.getPosZ() / 16f);

        mat.rotateZ(bone.getRotZ());
        mat.rotateY(bone.getRotY());
        mat.rotateX(bone.getRotX());
        mat.scale(bone.getScaleX(), bone.getScaleY(), bone.getScaleZ());
        return mat;
    }

    public static Vec3d[] getLimbWorldCorners(AnimatedPlayerModel model, String boneName, float tickDelta) {
        PlayerAnimationController controller = model.getAnimationController();

        double lerpX = model.getInterpolatedX(tickDelta);
        double lerpY = model.getInterpolatedY(tickDelta);
        double lerpZ = model.getInterpolatedZ(tickDelta);
        float bodyYaw = model.getInterpolatedYaw(tickDelta);
        PlayerAnimBone bodyBone = controller.get3DTransform(new PlayerAnimBone("body"));
        Matrix4f mat = buildBoneWorldMatrix(controller, boneName, lerpX, lerpY, lerpZ, bodyYaw, bodyBone);

        float[] b = LIMB_BOUNDS.get(boneName);
        if (b == null) {
            return new Vec3d[0];
        }

        float minX = b[0] / 16f, minY = b[1] / 16f, minZ = b[2] / 16f;
        float maxX = b[3] / 16f, maxY = b[4] / 16f, maxZ = b[5] / 16f;

        Vec3d[] corners = new Vec3d[8];
        corners[0] = transformPoint(mat, minX, minY, minZ);
        corners[1] = transformPoint(mat, maxX, minY, minZ);
        corners[2] = transformPoint(mat, maxX, maxY, minZ);
        corners[3] = transformPoint(mat, minX, maxY, minZ);
        corners[4] = transformPoint(mat, minX, minY, maxZ);
        corners[5] = transformPoint(mat, maxX, minY, maxZ);
        corners[6] = transformPoint(mat, maxX, maxY, maxZ);
        corners[7] = transformPoint(mat, minX, maxY, maxZ);
        return corners;
    }

    public static Matrix4f getBoneParentMatrix(PlayerAnimationController controller, String boneName,
                                               double x, double y, double z, float bodyYaw, PlayerAnimBone bodyBone) {
        Matrix4f mat = new Matrix4f().identity();
        mat.translate((float) x, (float) y, (float) z);
        mat.rotate((float) Math.toRadians(180.0f - bodyYaw), 0, 1, 0);

        com.zigythebird.playeranimcore.math.Vec3f pivot = controller.getBonePosition(boneName);
        float px, py, pz;
        if (pivot != null && (pivot.x() != 0 || pivot.y() != 0 || pivot.z() != 0)) {
            px = pivot.x();
            py = pivot.y();
            pz = pivot.z();
        } else {
            px = switch (boneName) {
                case "right_arm" -> 5;
                case "left_arm" -> -5;
                case "right_leg" -> 2;
                case "left_leg" -> -2;
                default -> 0;
            };
            py = switch (boneName) {
                case "head" -> 24;
                case "right_arm", "left_arm" -> 22;
                case "body" -> 12;
                case "right_leg", "left_leg" -> 12;
                default -> 12;
            };
            pz = 0;
        }
        mat.translate(px / 16f, py / 16f, pz / 16f);
        return mat;
    }

    private static Vec3d transformPoint(Matrix4f mat, float x, float y, float z) {
        Vector4f v = new Vector4f(x, y, z, 1.0f).mul(mat);
        return new Vec3d(v.x, v.y, v.z);
    }

    private static float intersectAABB(Vector3f pos, Vector3f dir, float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        float tMin = Float.NEGATIVE_INFINITY;
        float tMax = Float.POSITIVE_INFINITY;

        // X axis
        if (Math.abs(dir.x) < 1e-6f) {
            // ray is parallel to X slabs
            if (pos.x < minX || pos.x > maxX) return -1;
        } else {
            float t1 = (minX - pos.x) / dir.x;
            float t2 = (maxX - pos.x) / dir.x;
            if (t1 > t2) { float t = t1; t1 = t2; t2 = t; }
            tMin = Math.max(tMin, t1);
            tMax = Math.min(tMax, t2);
            if (tMin > tMax) return -1;
        }

        // Y axis
        if (Math.abs(dir.y) < 1e-6f) {
            if (pos.y < minY || pos.y > maxY) return -1;
        } else {
            float t1 = (minY - pos.y) / dir.y;
            float t2 = (maxY - pos.y) / dir.y;
            if (t1 > t2) { float t = t1; t1 = t2; t2 = t; }
            tMin = Math.max(tMin, t1);
            tMax = Math.min(tMax, t2);
            if (tMin > tMax) return -1;
        }

        // Z axis
        if (Math.abs(dir.z) < 1e-6f) {
            if (pos.z < minZ || pos.z > maxZ) return -1;
        } else {
            float t1 = (minZ - pos.z) / dir.z;
            float t2 = (maxZ - pos.z) / dir.z;
            if (t1 > t2) { float t = t1; t1 = t2; t2 = t; }
            tMin = Math.max(tMin, t1);
            tMax = Math.min(tMax, t2);
            if (tMin > tMax) return -1;
        }


        if (tMin > 0) return tMin;
        return -1;
    }
}
