package com.moud.client.fabric.scene;

import com.moud.core.math.Quat;
import com.moud.core.math.Transform;
import com.moud.core.math.Vec3;
import com.moud.core.math.YawConvention;
import com.moud.core.util.MathUtils;
import com.moud.core.util.ParseUtils;
import com.moud.net.protocol.SceneSnapshot;
import java.util.List;

public final class SceneCameraResolver {
    private SceneCameraResolver() {}

    public record ResolvedCamera(
            float worldX, float worldY, float worldZ,
            float yawDeg, float pitchDeg, float rollDeg,
            boolean orthographic,
            float orthoSize,
            float fov
    ) {}

    /**
     * Walk the client scene tree, pick the active Camera3D, and compose its world transform from
     * local properties + parent chain — same model as Godot's per-frame transform graph. No wire
     * fields, no server snapshot lerp.
     *
     * Selection rules mirror server's RuntimeCameraSystem.selectSceneCamera: prefer the Camera3D
     * with current=true; fall back to first Camera3D iff the scene contains a CharacterBody3D
     * (cinematic scenes without a body opt-in by setting current=true).
     */
    public static ResolvedCamera resolve() {
        List<SceneSnapshot.NodeSnapshot> nodes = ClientSceneBus.copyNodes();
        SceneSnapshot.NodeSnapshot current = null;
        SceneSnapshot.NodeSnapshot first = null;
        boolean hasCharacterBody = false;
        for (SceneSnapshot.NodeSnapshot node : nodes) {
            if (node == null) continue;
            String type = node.type();
            if ("Camera3D".equals(type)) {
                if (first == null) first = node;
                if (ParseUtils.parseBool(SceneTransforms.getProperty(node, "current"))) {
                    current = node;
                }
            } else if ("CharacterBody3D".equals(type)) {
                hasCharacterBody = true;
            }
        }
        SceneSnapshot.NodeSnapshot chosen = current != null ? current : (hasCharacterBody ? first : null);
        if (chosen == null) return null;

        Transform world = SceneTransforms.worldTransform(chosen);
        Vec3 fwd = world.rot().rotate(new Vec3(0.0, 0.0, 1.0));
        float moudYaw = (float) Math.toDegrees(Math.atan2(fwd.x(), fwd.z()));
        float mcYaw = YawConvention.mcFromMoud(moudYaw);
        double fyClamped = Math.max(-1.0, Math.min(1.0, -fwd.y()));
        float pitch = MathUtils.clampPitch((float) Math.toDegrees(Math.asin(fyClamped)), -89.0f, 89.0f);

        // Roll extraction: project body up against the camera's local up; angle around forward.
        Vec3 up = world.rot().rotate(new Vec3(0.0, 1.0, 0.0));
        Quat yawPitch = Quat.fromEulerDeg(pitch, moudYaw, 0.0f);
        Vec3 up0 = yawPitch.rotate(new Vec3(0.0, 1.0, 0.0));
        double rollRad = Math.atan2(fwd.dot(up0.cross(up)), up0.dot(up));
        float roll = (float) Math.toDegrees(rollRad);
        if (!Float.isFinite(roll)) roll = 0.0f;

        boolean ortho = "orthographic".equals(SceneTransforms.getProperty(chosen, "projection"));
        float orthoSize = ParseUtils.parseFloat(SceneTransforms.getProperty(chosen, "ortho_size"), 10.0f);
        float fov = ParseUtils.parseFloat(SceneTransforms.getProperty(chosen, "fov"), 0f);

        return new ResolvedCamera(
                (float) world.pos().x(), (float) world.pos().y(), (float) world.pos().z(),
                mcYaw, pitch, roll,
                ortho, orthoSize, fov
        );
    }
}
