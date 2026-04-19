package com.moud.client.fabric.render;

import com.moud.client.fabric.render.scene.math.Pose;
import com.moud.net.protocol.CollisionGeometrySnapshot;
import com.moud.net.protocol.SceneSnapshot;

public final class VeilSceneNodeRenderer {
    private VeilSceneNodeRenderer() {
    }

    public static void init() {
        VeilSceneRenderer.init();
    }

    public static void toggleCollisionDebug() {
        VeilSceneRenderer.toggleCollisionDebug();
    }

    public static void onCollisionGeometry(CollisionGeometrySnapshot snapshot) {
        VeilSceneRenderer.onCollisionGeometry(snapshot);
    }

    public static void clearCollisionGeometryCache() {
        VeilSceneRenderer.clearCollisionGeometryCache();
    }

    public static void setRuntimeBodyOverride(long nodeId, float x, float y, float z, float yawDeg) {
        VeilSceneRenderer.setRuntimeBodyOverride(nodeId, x, y, z, yawDeg);
    }

    public static void clearRuntimeBodyOverride() {
        VeilSceneRenderer.clearRuntimeBodyOverride();
    }

    public static void clearMaterialTextureCache() {
        VeilSceneRenderer.clearMaterialTextureCache();
    }

    public static void clearLights() {
        VeilSceneRenderer.clearLights();
    }

    public static float parseFloat(String value, float fallback) {
        return VeilSceneRenderer.parseFloat(value, fallback);
    }

    public static boolean parseBool(String value, boolean fallback) {
        return VeilSceneRenderer.parseBool(value, fallback);
    }

    public static float clamp01(float value) {
        return VeilSceneRenderer.clamp01(value);
    }

    public static String stringProp(SceneSnapshot.NodeSnapshot node, String key) {
        return VeilSceneRenderer.stringProp(node, key);
    }

    public static Pose worldPose(long nodeId) {
        return VeilSceneRenderer.worldPose(nodeId);
    }
}
