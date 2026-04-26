package com.moud.client.fabric.render;

import com.moud.client.fabric.env.WorldEnvironmentClient;
import com.moud.client.fabric.render.veil.GlUtil;
import com.moud.client.fabric.render.scene.math.Pose;
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

    private static final String[] POINT_POSITION = new String[MAX_POINT_LIGHTS];
    private static final String[] POINT_COLOR = new String[MAX_POINT_LIGHTS];
    private static final String[] POINT_BRIGHTNESS = new String[MAX_POINT_LIGHTS];
    private static final String[] POINT_RADIUS = new String[MAX_POINT_LIGHTS];
    private static final String[] DIR_DIRECTION = new String[MAX_DIR_LIGHTS];
    private static final String[] DIR_COLOR = new String[MAX_DIR_LIGHTS];
    private static final String[] DIR_BRIGHTNESS = new String[MAX_DIR_LIGHTS];
    private static final String[] SPOT_POSITION = new String[MAX_SPOT_LIGHTS];
    private static final String[] SPOT_DIRECTION = new String[MAX_SPOT_LIGHTS];
    private static final String[] SPOT_COLOR = new String[MAX_SPOT_LIGHTS];
    private static final String[] SPOT_BRIGHTNESS = new String[MAX_SPOT_LIGHTS];
    private static final String[] SPOT_ANGLE = new String[MAX_SPOT_LIGHTS];
    private static final String[] SPOT_DISTANCE = new String[MAX_SPOT_LIGHTS];

    static {
        for (int i = 0; i < MAX_POINT_LIGHTS; i++) {
            String p = "PointLights[" + i + "].";
            POINT_POSITION[i]   = p + "position";
            POINT_COLOR[i]      = p + "color";
            POINT_BRIGHTNESS[i] = p + "brightness";
            POINT_RADIUS[i]     = p + "radius";
        }
        for (int i = 0; i < MAX_DIR_LIGHTS; i++) {
            String p = "DirLights[" + i + "].";
            DIR_DIRECTION[i] = p + "direction";
            DIR_COLOR[i]     = p + "color";
            DIR_BRIGHTNESS[i] = p + "brightness";
        }
        for (int i = 0; i < MAX_SPOT_LIGHTS; i++) {
            String p = "SpotLights[" + i + "].";
            SPOT_POSITION[i]   = p + "position";
            SPOT_DIRECTION[i]  = p + "direction";
            SPOT_COLOR[i]      = p + "color";
            SPOT_BRIGHTNESS[i] = p + "brightness";
            SPOT_ANGLE[i]      = p + "angle";
            SPOT_DISTANCE[i]   = p + "distance";
        }
    }

    private final Vector3f scratchDir = new Vector3f();

    final List<PointLight> pointLights = new ArrayList<>();
    final List<DirLight> dirLights = new ArrayList<>();
    final List<SpotLight> spotLights = new ArrayList<>();

    public List<SpotLight> spotLights() { return spotLights; }

    record PointLight(float x, float y, float z, float r, float g, float b, float brightness, float radius) {}
    record DirLight(float dx, float dy, float dz, float r, float g, float b, float brightness) {}
    record SpotLight(float x, float y, float z, float dx, float dy, float dz,
                     float r, float g, float b, float brightness, float angleDeg, float distance,
                     boolean castShadows) {}

    void collect(List<SceneSnapshot.NodeSnapshot> nodes,
                 Function<Long, Pose> poseResolver) {
        pointLights.clear();
        dirLights.clear();
        spotLights.clear();
        collectAdd(nodes, poseResolver);
    }

    void collectAdd(List<SceneSnapshot.NodeSnapshot> nodes,
                    Function<Long, Pose> poseResolver) {
        for (SceneSnapshot.NodeSnapshot node : nodes) {
            if (node == null) continue;
            String type = node.type();
            if (!"OmniLight3D".equals(type) && !"DirectionalLight3D".equals(type) && !"SpotLight3D".equals(type))
                continue;

            if (!VeilSceneNodeRenderer.parseBool(VeilSceneNodeRenderer.stringProp(node, "visible"), true))
                continue;
            if (!VeilSceneNodeRenderer.parseBool(VeilSceneNodeRenderer.stringProp(node, "enabled"), true))
                continue;

            Pose world = poseResolver.apply(node.nodeId());
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
                        // godot/unity light3d convention, default rotation lights the camera-facing side
                        Vector3f dir = scratchDir.set(0, 0, -1);
                        world.rot.transform(dir);
                        if (dir.lengthSquared() > 1e-12f) dir.normalize();
                        dirLights.add(new DirLight(dir.x, dir.y, dir.z, cr, cg, cb, brightness));
                    }
                }
                case "SpotLight3D" -> {
                    if (spotLights.size() < MAX_SPOT_LIGHTS) {
                        // same -Z forward convention as DirectionalLight3D
                        Vector3f dir = scratchDir.set(0, 0, -1);
                        world.rot.transform(dir);
                        if (dir.lengthSquared() > 1e-12f) dir.normalize();
                        float angle = VeilSceneNodeRenderer.parseFloat(VeilSceneNodeRenderer.stringProp(node, "angle"), 45.0f);
                        float dist = Math.max(0, VeilSceneNodeRenderer.parseFloat(VeilSceneNodeRenderer.stringProp(node, "distance"), 10.0f));
                        boolean castShadows = VeilSceneNodeRenderer.parseBool(VeilSceneNodeRenderer.stringProp(node, "cast_shadows"), false);
                        spotLights.add(new SpotLight(world.pos.x, world.pos.y, world.pos.z, dir.x, dir.y, dir.z, cr, cg, cb, brightness, angle, dist, castShadows));
                    }
                }
            }
        }
    }

    void applyUniforms(int pid) {
        int pCount = pointLights.size();
        GlUtil.uniform1i(pid, "NumPointLights", pCount);
        for (int i = 0; i < pCount; i++) {
            PointLight l = pointLights.get(i);
            GlUtil.uniform3f(pid, POINT_POSITION[i],   l.x, l.y, l.z);
            GlUtil.uniform3f(pid, POINT_COLOR[i],      l.r, l.g, l.b);
            GlUtil.uniform1f(pid, POINT_BRIGHTNESS[i], l.brightness);
            GlUtil.uniform1f(pid, POINT_RADIUS[i],     l.radius);
        }

        int dCount = dirLights.size();
        GlUtil.uniform1i(pid, "NumDirLights", dCount);
        for (int i = 0; i < dCount; i++) {
            DirLight l = dirLights.get(i);
            GlUtil.uniform3f(pid, DIR_DIRECTION[i], l.dx, l.dy, l.dz);
            GlUtil.uniform3f(pid, DIR_COLOR[i],     l.r, l.g, l.b);
            GlUtil.uniform1f(pid, DIR_BRIGHTNESS[i], l.brightness);
        }

        int sCount = spotLights.size();
        GlUtil.uniform1i(pid, "NumSpotLights", sCount);
        for (int i = 0; i < sCount; i++) {
            SpotLight l = spotLights.get(i);
            GlUtil.uniform3f(pid, SPOT_POSITION[i],   l.x, l.y, l.z);
            GlUtil.uniform3f(pid, SPOT_DIRECTION[i],  l.dx, l.dy, l.dz);
            GlUtil.uniform3f(pid, SPOT_COLOR[i],      l.r, l.g, l.b);
            GlUtil.uniform1f(pid, SPOT_BRIGHTNESS[i], l.brightness);
            GlUtil.uniform1f(pid, SPOT_ANGLE[i],      l.angleDeg);
            GlUtil.uniform1f(pid, SPOT_DISTANCE[i],   l.distance);
        }

        GlUtil.uniform1f(pid, "ambient_light", WorldEnvironmentClient.current().ambientLight());
    }
}
