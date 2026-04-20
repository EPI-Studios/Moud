package com.moud.client.fabric.render.shadow;

import com.mojang.blaze3d.systems.RenderSystem;
import com.moud.client.fabric.render.scene.math.Pose;
import com.moud.client.fabric.render.scene.util.NodePropertyUtils;
import com.moud.client.fabric.scene.ClientPropertyOverrides;
import com.moud.net.protocol.SceneSnapshot;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

public final class ShadowPass {

    @FunctionalInterface
    public interface DepthDrawer {
        void draw(SceneSnapshot.NodeSnapshot node, Pose world, Matrix4f lightViewProj);
    }

    public record SpotCaster(
            int lightIndex,
            float x, float y, float z,
            float dx, float dy, float dz,
            float angleDeg,
            float distance,
            int slot) { }

    private ShadowPass() { }

    public static Matrix4f buildSpotViewProj(SpotCaster c) {
        Vector3f eye = new Vector3f(c.x, c.y, c.z);
        Vector3f dir = new Vector3f(c.dx, c.dy, c.dz);
        if (dir.lengthSquared() < 1e-6f) dir.set(0f, -1f, 0f);
        dir.normalize();
        Vector3f center = new Vector3f(eye).add(dir);

        Vector3f up = Math.abs(dir.y) > 0.9f
                ? new Vector3f(0f, 0f, 1f)
                : new Vector3f(0f, 1f, 0f);

        Matrix4f proj = new Matrix4f().perspective(
                (float) Math.toRadians(Math.max(c.angleDeg, 1f)),
                1f,
                0.1f,
                Math.max(c.distance, 1f)
        );
        Matrix4f view = new Matrix4f().lookAt(eye.x, eye.y, eye.z, center.x, center.y, center.z, up.x, up.y, up.z);
        return new Matrix4f(proj).mul(view);
    }

    public static void renderSpotShadows(
            List<SpotCaster> casters,
            List<SceneSnapshot.NodeSnapshot> nodes,
            Function<Long, Pose> poseResolver,
            DepthDrawer drawer) {

        if (casters == null || casters.isEmpty() || nodes == null || drawer == null) return;
        if (!RenderSystem.isOnRenderThread()) return;

        Map<Long, SceneSnapshot.NodeSnapshot> nodeById = new HashMap<>(nodes.size() * 2);
        for (SceneSnapshot.NodeSnapshot n : nodes) {
            if (n != null && n.nodeId() > 0L) nodeById.put(n.nodeId(), n);
        }
        HashMap<Long, Boolean> dynamicCache = new HashMap<>();

        ShadowMaps.ensureAllocated();
        int atlasFbo = ShadowMaps.atlasFbo();
        int cacheFbo = ShadowMaps.cacheFbo();
        if (atlasFbo == 0 || cacheFbo == 0) return;

        int prevDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int prevRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int[] prevViewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, prevViewport);
        boolean prevDepthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean prevBlend = GL11.glIsEnabled(GL11.GL_BLEND);
        int prevScissorBox = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST) ? 1 : 0;

        try {
            RenderSystem.disableBlend();
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);

            for (SpotCaster caster : casters) {
                int slot = caster.slot;
                int tx = ShadowMaps.spotTileX(slot);
                int ty = ShadowMaps.spotTileY(slot);
                int ts = ShadowMaps.spotTileSize(slot);
                Matrix4f viewProj = ShadowMaps.spotViewProj(slot);

                boolean cacheHit = ShadowMaps.cacheValid(slot)
                        && ShadowMaps.cachedLightIndex(slot) == caster.lightIndex
                        && matricesEqual(ShadowMaps.cachedViewProj(slot), viewProj);

                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, atlasFbo);
                GL11.glViewport(tx, ty, ts, ts);

                if (cacheHit) {
                    GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, cacheFbo);
                    GL30.glBlitFramebuffer(
                            tx, ty, tx + ts, ty + ts,
                            tx, ty, tx + ts, ty + ts,
                            GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);
                    GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, 0);
                    GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, atlasFbo);
                    GL11.glViewport(tx, ty, ts, ts);

                    GL11.glEnable(GL11.GL_SCISSOR_TEST);
                    GL11.glScissor(tx, ty, ts, ts);
                    RenderSystem.enableCull();
                    for (SceneSnapshot.NodeSnapshot node : nodes) {
                        if (node == null || !isCaster(node)) continue;
                        if (!isDynamicWithParents(node, nodeById, dynamicCache)) continue;
                        Pose world = poseResolver.apply(node.nodeId());
                        if (world == null) continue;
                        drawer.draw(node, world, viewProj);
                    }
                    GL11.glDisable(GL11.GL_SCISSOR_TEST);
                } else {
                    GL11.glEnable(GL11.GL_SCISSOR_TEST);
                    GL11.glScissor(tx, ty, ts, ts);
                    GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
                    RenderSystem.enableCull();
                    for (SceneSnapshot.NodeSnapshot node : nodes) {
                        if (node == null || !isCaster(node)) continue;
                        Pose world = poseResolver.apply(node.nodeId());
                        if (world == null) continue;
                        drawer.draw(node, world, viewProj);
                    }
                    GL11.glDisable(GL11.GL_SCISSOR_TEST);

                    GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, atlasFbo);
                    GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, cacheFbo);
                    GL30.glBlitFramebuffer(
                            tx, ty, tx + ts, ty + ts,
                            tx, ty, tx + ts, ty + ts,
                            GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);

                    ShadowMaps.storeCacheMeta(slot, caster.lightIndex, viewProj);
                    ShadowMaps.markCacheValid(slot);

                    GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, 0);
                    GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, atlasFbo);
                    GL11.glViewport(tx, ty, ts, ts);
                }
            }
        } finally {
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, prevDraw);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevRead);
            GL11.glViewport(prevViewport[0], prevViewport[1], prevViewport[2], prevViewport[3]);
            if (prevDepthTest) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            if (prevBlend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            if (prevScissorBox == 1) GL11.glEnable(GL11.GL_SCISSOR_TEST); else GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }
    }

    private static boolean isCaster(SceneSnapshot.NodeSnapshot node) {
        String type = node.type();
        if (type == null) return false;
        if (!NodePropertyUtils.parseBool(NodePropertyUtils.stringProp(node, "visible"), true)) return false;
        if (NodePropertyUtils.parseBool(NodePropertyUtils.stringProp(node, "viewmodel"), false)) return false;
        return "MeshInstance3D".equals(type)
                || "Sprite3D".equals(type)
                || "CSGBox".equals(type)
                || "CSGBlock".equals(type);
    }

    private static boolean isDynamicWithParents(
            SceneSnapshot.NodeSnapshot node,
            Map<Long, SceneSnapshot.NodeSnapshot> nodeById,
            Map<Long, Boolean> cache) {
        if (node == null) return false;
        Long key = node.nodeId();
        Boolean cached = cache.get(key);
        if (cached != null) return cached;

        HashSet<Long> visited = new HashSet<>();
        SceneSnapshot.NodeSnapshot cur = node;
        int depth = 0;
        while (cur != null && depth < 64 && visited.add(cur.nodeId())) {
            if (isSelfDynamic(cur)) {
                cache.put(key, Boolean.TRUE);
                return true;
            }
            long pid = cur.parentId();
            if (pid <= 0L) break;
            cur = nodeById.get(pid);
            depth++;
        }
        cache.put(key, Boolean.FALSE);
        return false;
    }

    private static boolean isSelfDynamic(SceneSnapshot.NodeSnapshot node) {
        if (node == null) return false;
        String type = node.type();
        if (type != null) {
            if ("CharacterBody3D".equals(type)
                    || "RigidBody3D".equals(type)
                    || "KinematicBody3D".equals(type)
                    || "PlayerAttachment".equals(type)
                    || "Particle3D".equals(type)
                    || "AnimatedSprite3D".equals(type)) {
                return true;
            }
        }
        if (NodePropertyUtils.parseBool(NodePropertyUtils.stringProp(node, "@runtime"), false)) return true;
        if (NodePropertyUtils.parseBool(NodePropertyUtils.stringProp(node, "@transient"), false)) return true;
        String script = NodePropertyUtils.stringProp(node, "script");
        if (script != null && !script.isBlank()) return true;
        String clientScript = NodePropertyUtils.stringProp(node, "client_script");
        if (clientScript != null && !clientScript.isBlank()) return true;
        if (NodePropertyUtils.parseBool(NodePropertyUtils.stringProp(node, "player_controlled"), false)) return true;
        if (NodePropertyUtils.parseBool(NodePropertyUtils.stringProp(node, "script_controlled"), false)) return true;
        if (NodePropertyUtils.parseBool(NodePropertyUtils.stringProp(node, "physics_body"), false)) return true;
        String vx = NodePropertyUtils.stringProp(node, "velocity_x");
        if (vx != null && !vx.isBlank() && !"0".equals(vx.trim()) && !"0.0".equals(vx.trim())) return true;
        if (ClientPropertyOverrides.hasAny(node.nodeId())) return true;
        return false;
    }

    private static boolean matricesEqual(Matrix4f a, Matrix4f b) {
        if (a == null || b == null) return false;
        float[] aa = new float[16];
        float[] bb = new float[16];
        a.get(aa);
        b.get(bb);
        for (int i = 0; i < 16; i++) {
            if (Math.abs(aa[i] - bb[i]) > 1e-5f) return false;
        }
        return true;
    }
}
