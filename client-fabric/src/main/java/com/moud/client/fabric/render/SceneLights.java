package com.moud.client.fabric.render;

import com.moud.client.fabric.render.veil.GlUtil;
import com.moud.net.protocol.SceneSnapshot;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

final class SceneLights {

    static final int MAX_POINT_LIGHTS = 16;
    static final int MAX_DIR_LIGHTS = 4;
    static final int MAX_SPOT_LIGHTS = 8;

    final List<PointLight> pointLights = new ArrayList<>();
    final List<DirLight> dirLights = new ArrayList<>();
    final List<SpotLight> spotLights = new ArrayList<>();

    record PointLight(float x, float y, float z, float r, float g, float b, float brightness, float radius) {}
    record DirLight(float dx, float dy, float dz, float r, float g, float b, float brightness) {}
    record SpotLight(float x, float y, float z, float dx, float dy, float dz,
                     float r, float g, float b, float brightness, float angleDeg, float distance) {}

    void collect(List<SceneSnapshot.NodeSnapshot> nodes,
                 Function<Long, VeilSceneNodeRenderer.Pose> poseResolver) {
        pointLights.clear();
        dirLights.clear();
        spotLights.clear();
        collectAdd(nodes, poseResolver);
    }

    void collectAdd(List<SceneSnapshot.NodeSnapshot> nodes,
                    Function<Long, VeilSceneNodeRenderer.Pose> poseResolver) {
        for (SceneSnapshot.NodeSnapshot node : nodes) {
            if (node == null) continue;
            String type = node.type();
            if (!"OmniLight3D".equals(type) && !"DirectionalLight3D".equals(type) && !"SpotLight3D".equals(type))
                continue;

            if (!VeilSceneNodeRenderer.parseBool(VeilSceneNodeRenderer.stringProp(node, "visible"), true))
                continue;
            if (!VeilSceneNodeRenderer.parseBool(VeilSceneNodeRenderer.stringProp(node, "enabled"), true))
                continue;

            VeilSceneNodeRenderer.Pose world = poseResolver.apply(node.nodeId());
            if (world == null) continue;

            float cr = VeilSceneNodeRenderer.clamp01(VeilSceneNodeRenderer.parseFloat(VeilSceneNodeRenderer.stringProp(node, "color_r"), 1.0f));
            float cg = VeilSceneNodeRenderer.clamp01(VeilSceneNodeRenderer.parseFloat(VeilSceneNodeRenderer.stringProp(node, "color_g"), 1.0f));
            float cb = VeilSceneNodeRenderer.clamp01(VeilSceneNodeRenderer.parseFloat(VeilSceneNodeRenderer.stringProp(node, "color_b"), 1.0f));
            float brightness = Math.max(0, VeilSceneNodeRenderer.parseFloat(VeilSceneNodeRenderer.stringProp(node, "brightness"), 1.0f));

            switch (type) {
                case "OmniLight3D" -> {
                    if (pointLights.size() < MAX_POINT_LIGHTS) {
                        float radius = Math.max(0, VeilSceneNodeRenderer.parseFloat(VeilSceneNodeRenderer.stringProp(node, "radius"), 8.0f));
                        pointLights.add(new PointLight(world.pos.x, world.pos.y, world.pos.z, cr, cg, cb, brightness, radius));
                    }
                }
                case "DirectionalLight3D" -> {
                    if (dirLights.size() < MAX_DIR_LIGHTS) {
                        Vector3f dir = new Vector3f(0, 0, 1);
                        world.rot.transform(dir);
                        if (dir.lengthSquared() > 1e-12f) dir.normalize();
                        dirLights.add(new DirLight(dir.x, dir.y, dir.z, cr, cg, cb, brightness));
                    }
                }
                case "SpotLight3D" -> {
                    if (spotLights.size() < MAX_SPOT_LIGHTS) {
                        Vector3f dir = new Vector3f(0, 0, 1);
                        world.rot.transform(dir);
                        if (dir.lengthSquared() > 1e-12f) dir.normalize();
                        float angle = VeilSceneNodeRenderer.parseFloat(VeilSceneNodeRenderer.stringProp(node, "angle"), 45.0f);
                        float dist = Math.max(0, VeilSceneNodeRenderer.parseFloat(VeilSceneNodeRenderer.stringProp(node, "distance"), 10.0f));
                        spotLights.add(new SpotLight(world.pos.x, world.pos.y, world.pos.z, dir.x, dir.y, dir.z, cr, cg, cb, brightness, angle, dist));
                    }
                }
            }
        }
    }

    void applyUniforms(int pid) {
        GlUtil.uniform1i(pid, "NumPointLights", pointLights.size());
        for (int i = 0; i < pointLights.size(); i++) {
            String p = "PointLights[" + i + "].";
            PointLight l = pointLights.get(i);
            GlUtil.uniform3f(pid, p + "position", l.x, l.y, l.z);
            GlUtil.uniform3f(pid, p + "color", l.r, l.g, l.b);
            GlUtil.uniform1f(pid, p + "brightness", l.brightness);
            GlUtil.uniform1f(pid, p + "radius", l.radius);
        }

        GlUtil.uniform1i(pid, "NumDirLights", dirLights.size());
        for (int i = 0; i < dirLights.size(); i++) {
            String p = "DirLights[" + i + "].";
            DirLight l = dirLights.get(i);
            GlUtil.uniform3f(pid, p + "direction", l.dx, l.dy, l.dz);
            GlUtil.uniform3f(pid, p + "color", l.r, l.g, l.b);
            GlUtil.uniform1f(pid, p + "brightness", l.brightness);
        }

        GlUtil.uniform1i(pid, "NumSpotLights", spotLights.size());
        for (int i = 0; i < spotLights.size(); i++) {
            String p = "SpotLights[" + i + "].";
            SpotLight l = spotLights.get(i);
            GlUtil.uniform3f(pid, p + "position", l.x, l.y, l.z);
            GlUtil.uniform3f(pid, p + "direction", l.dx, l.dy, l.dz);
            GlUtil.uniform3f(pid, p + "color", l.r, l.g, l.b);
            GlUtil.uniform1f(pid, p + "brightness", l.brightness);
            GlUtil.uniform1f(pid, p + "angle", l.angleDeg);
            GlUtil.uniform1f(pid, p + "distance", l.distance);
        }
    }
}
