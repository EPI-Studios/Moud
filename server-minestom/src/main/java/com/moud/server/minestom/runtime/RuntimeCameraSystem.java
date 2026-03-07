package com.moud.server.minestom.runtime;


import com.moud.core.math.Quat;
import com.moud.core.math.Transform;
import com.moud.core.math.Vec3;
import com.moud.core.scene.Node;
import com.moud.core.util.MathUtils;
import com.moud.core.util.ParseUtils;
import com.moud.server.minestom.engine.ServerScene;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class RuntimeCameraSystem {
    private final float minPitch;
    private final float maxPitch;
    private final Map<String, CameraCache> cameraCacheByScene = new ConcurrentHashMap<>();

    RuntimeCameraSystem(float minPitch, float maxPitch) {
        this.minPitch = minPitch;
        this.maxPitch = maxPitch;
    }

    SelectedCamera selectSceneCamera(ServerScene scene) {
        if (scene == null) {
            return null;
        }
        long graphRev = scene.engine().sceneRevision();
        CameraCache cached = cameraCacheByScene.get(scene.sceneId());
        if (cached != null && cached.graphRevision == graphRev && cached.cameraNodeId > 0L) {
            Node node = scene.engine().sceneTree().getNode(cached.cameraNodeId);
            if (node != null && "Camera3D".equals(scene.engine().nodeTypes().typeIdFor(node)) && !RuntimeSceneQueries.isRuntimeSubtree(node)) {
                return new SelectedCamera(node, createSceneCameraFromNode(node));
            }
        }

        Node root = scene.engine().sceneTree().root();
        Node first = null;
        Node current = null;

        ArrayList<Node> stack = new ArrayList<>();
        stack.add(root);
        while (!stack.isEmpty()) {
            Node node = stack.remove(stack.size() - 1);
            if (node == null) {
                continue;
            }
            if (node != root && RuntimeSceneQueries.isRuntimeSubtree(node)) {
                continue;
            }

            if ("Camera3D".equals(scene.engine().nodeTypes().typeIdFor(node))) {
                if (first == null) {
                    first = node;
                }
                if (ParseUtils.parseBool(node.getProperty("current"))) {
                    current = node;
                    break;
                }
            }

            List<Node> children = node.children();
            for (int i = children.size() - 1; i >= 0; i--) {
                stack.add(children.get(i));
            }
        }

        Node chosen = current != null ? current : first;
        if (chosen == null) {
            return null;
        }

        cameraCacheByScene.put(scene.sceneId(), new CameraCache(graphRev, chosen.nodeId()));
        return new SelectedCamera(chosen, createSceneCameraFromNode(chosen));
    }

    SceneCamera createRuntimeCameraFromBody(Node body,
                                           Node cameraNode,
                                           float bodyWorldX,
                                           float bodyWorldY,
                                           float bodyWorldZ,
                                           float bodyYawDeg,
                                           float inputPitchDeg) {
        if (body == null || cameraNode == null) {
            return null;
        }

        Transform authoredBody = RuntimeTransforms.worldTransform(body);
        Transform authoredCam = RuntimeTransforms.worldTransform(cameraNode);
        Transform camLocalToBody = RuntimeTransforms.toLocal(authoredCam, authoredBody);

        Vec3 euler = camLocalToBody.rot().toEulerDeg();
        float basePitch = (float) euler.x();
        float baseYaw = (float) euler.y();
        float baseRoll = (float) euler.z();

        float localPitch = MathUtils.clampPitch(basePitch + inputPitchDeg, minPitch, maxPitch);
        Transform runtimeBody = new Transform(
                new Vec3(bodyWorldX, bodyWorldY, bodyWorldZ),
                Quat.fromEulerDeg(0.0f, bodyYawDeg, 0.0f),
                new Vec3(1.0, 1.0, 1.0));
        Transform runtimeCamLocal = new Transform(camLocalToBody.pos(), Quat.fromEulerDeg(localPitch, baseYaw, baseRoll), camLocalToBody.scale());
        Transform runtimeCam = runtimeBody.compose(runtimeCamLocal);
        return createSceneCameraFromTransform(runtimeCam);
    }

    SceneCamera createSceneCameraFromNode(Node node) {
        return createSceneCameraFromTransform(RuntimeTransforms.worldTransform(node));
    }

    SceneCamera createSceneCameraFromTransform(Transform t) {
        if (t == null) {
            t = Transform.IDENTITY;
        }
        Vec3 fwd = t.rot().rotate(new Vec3(0.0, 0.0, 1.0));
        float yaw = MathUtils.normalizeYaw((float) Math.toDegrees(Math.atan2(fwd.x(), fwd.z())));
        double fy = Math.max(-1.0, Math.min(1.0, -fwd.y()));
        float pitch = MathUtils.clampPitch((float) Math.toDegrees(Math.asin(fy)), minPitch, maxPitch);

        Vec3 up = t.rot().rotate(new Vec3(0.0, 1.0, 0.0));
        Quat yawPitch = Quat.fromEulerDeg(pitch, yaw, 0.0f);
        Vec3 up0 = yawPitch.rotate(new Vec3(0.0, 1.0, 0.0));
        double rollRad = Math.atan2(fwd.dot(up0.cross(up)), up0.dot(up));
        float roll = (float) Math.toDegrees(rollRad);
        if (!Float.isFinite(roll)) {
            roll = 0.0f;
        }

        return new SceneCamera((float) t.pos().x(), (float) t.pos().y(), (float) t.pos().z(), yaw, pitch, roll);
    }

    record SceneCamera(float x, float y, float z, float yawDeg, float pitchDeg, float rollDeg) {
    }

    record SelectedCamera(Node node, SceneCamera pose) {
    }

    private record CameraCache(long graphRevision, long cameraNodeId) {
    }
}

