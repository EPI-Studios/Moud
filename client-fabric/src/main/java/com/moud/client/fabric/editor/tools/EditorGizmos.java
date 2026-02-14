package com.moud.client.fabric.editor.tools;

import com.moud.client.fabric.editor.state.EditorRuntime;
import com.moud.client.fabric.editor.state.EditorState;
import com.moud.client.fabric.editor.ghost.EditorGhostBlocks;

import com.miry.ui.Ui;
import com.miry.ui.render.UiRenderer;
import com.miry.ui.gizmo.GizmoOverlay3D;
import com.miry.ui.gizmo.GizmoSpace;
import com.moud.core.NodeTypeDef;
import com.moud.core.PropertyDef;
import com.moud.net.protocol.SceneOp;
import com.moud.net.protocol.SceneSnapshot;
import com.moud.net.session.Session;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.BlockPos;
import org.joml.*;

import java.lang.Math;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class EditorGizmos implements AutoCloseable {
    private static final float DEFAULT_FOV_DEG = 70.0f;
    private static final float ROTATION_SNAP_DEG = 15.0f;
    private static final float DEG_EPS = 0.001f;

    private final EditorRuntime runtime;
    private GizmoOverlay3D overlay;
    private final Matrix3f localAxes = new Matrix3f();

    private final Vector3f basePos = new Vector3f();
    private final Vector3f size = new Vector3f(1, 1, 1);
    private final Vector3f centerPos = new Vector3f();
    private final Vector3f rotDeg = new Vector3f();

    public EditorGizmos(EditorRuntime runtime) {
        this.runtime = runtime;
    }

    public void render(Ui ui, UiRenderer r, int viewportX, int viewportY, int viewportW, int viewportH) {
        if (ui == null || r == null || viewportW <= 0 || viewportH <= 0) {
            return;
        }
        if (runtime.tool() == null || runtime.tool() == EditorTool.SELECT) {
            return;
        }

        EditorState state = runtime.state();
        SceneSnapshot.NodeSnapshot sel = state.scene.getNode(state.selectedId);
        if (sel == null) {
            return;
        }
        String typeId = sel.type();
        if (!"CSGBlock".equals(typeId)) {
            return;
        }

        Session session = runtime.session();
        if (session == null) {
            return;
        }

        if (overlay == null) {
            overlay = new GizmoOverlay3D();
        }

        overlay.setGizmoSpace(ui.input().altDown() ? GizmoSpace.LOCAL : GizmoSpace.WORLD);

        NodeTypeDef def = state.typesById.get(typeId);
        Map<String, String> props = toPropertyMap(sel.properties());

        float x = parseFloat(props.get("x"), defaultFor(def, "x", "0"));
        float y = parseFloat(props.get("y"), defaultFor(def, "y", "41"));
        float z = parseFloat(props.get("z"), defaultFor(def, "z", "0"));

        float sx = Math.max(1.0f, parseFloat(props.get("sx"), defaultFor(def, "sx", "1")));
        float sy = Math.max(1.0f, parseFloat(props.get("sy"), defaultFor(def, "sy", "1")));
        float sz = Math.max(1.0f, parseFloat(props.get("sz"), defaultFor(def, "sz", "1")));

        float rx = parseFloat(props.get("rx"), defaultFor(def, "rx", "0"));
        float ry = parseFloat(props.get("ry"), defaultFor(def, "ry", "0"));
        float rz = parseFloat(props.get("rz"), defaultFor(def, "rz", "0"));

        EditorGhostBlocks ghosts = EditorGhostBlocks.get();
        int startX = Math.round(x);
        int startY = Math.round(y);
        int startZ = Math.round(z);
        int startSx = Math.max(1, Math.round(sx));
        int startSy = Math.max(1, Math.round(sy));
        int startSz = Math.max(1, Math.round(sz));
        float startRx = rx;
        float startRy = ry;
        float startRz = rz;

        if (ghosts.isActive() && ghosts.nodeId() == sel.nodeId()) {
            Vec3d sb = ghosts.startBase();
            Vec3d ss = ghosts.startSize();
            Vec3d sr = ghosts.startRotDeg();
            startX = (int) Math.round(sb.x);
            startY = (int) Math.round(sb.y);
            startZ = (int) Math.round(sb.z);
            startSx = Math.max(1, (int) Math.round(ss.x));
            startSy = Math.max(1, (int) Math.round(ss.y));
            startSz = Math.max(1, (int) Math.round(ss.z));
            startRx = (float) sr.x;
            startRy = (float) sr.y;
            startRz = (float) sr.z;

            Vec3d gb = ghosts.renderBase();
            Vec3d gs = ghosts.renderSize();
            Vec3d gr = ghosts.renderRotDeg();
            x = (float) gb.x;
            y = (float) gb.y;
            z = (float) gb.z;
            sx = (float) gs.x;
            sy = (float) gs.y;
            sz = (float) gs.z;
            rx = (float) gr.x;
            ry = (float) gr.y;
            rz = (float) gr.z;
        }

        BlockPos snappedOrigin = new BlockPos(Math.round(x), Math.round(y), Math.round(z));
        int snappedSx = Math.max(1, Math.round(sx));
        int snappedSy = Math.max(1, Math.round(sy));
        int snappedSz = Math.max(1, Math.round(sz));

        BlockState csgDefaultState = resolveBlockState(props.get("block"), defaultFor(def, "block", "minecraft:stone"));

        basePos.set(x, y, z);
        size.set(sx, sy, sz);
        centerPos.set(basePos).fma(0.5f, size);
        rotDeg.set(rx, ry, rz);

        localAxes.identity().rotateXYZ(
                (float) Math.toRadians(rotDeg.x),
                (float) Math.toRadians(rotDeg.y),
                (float) Math.toRadians(rotDeg.z)
        );
        overlay.setLocalAxes(localAxes);

        overlay.setMode(switch (runtime.tool()) {
            case MOVE -> GizmoOverlay3D.Mode.TRANSLATE;
            case SCALE -> GizmoOverlay3D.Mode.SCALE;
            case ROTATE -> GizmoOverlay3D.Mode.ROTATE;
            default -> GizmoOverlay3D.Mode.NONE;
        });

        Matrix4f viewProj = minecraftViewProjection(viewportW / (float) Math.max(1, viewportH), centerPos);
        Vector3f cameraPos = minecraftCameraPos();
        if (viewProj == null || cameraPos == null) {
            return;
        }

        boolean wasDragging = overlay.dragging();
        overlay.updateInput(
                ui.input(),
                viewProj,
                cameraPos,
                viewportX,
                viewportY,
                viewportW,
                viewportH,
                runtime.framebufferScaleX(),
                runtime.framebufferScaleY(),
                centerPos,
                rotDeg,
                size
        );

        basePos.set(centerPos).fma(-0.5f, size);
        x = basePos.x;
        y = basePos.y;
        z = basePos.z;
        sx = size.x;
        sy = size.y;
        sz = size.z;

        if (ghosts.isActive() && ghosts.nodeId() != sel.nodeId()) {
            ghosts.cancel();
        }

        int pixelW = Math.max(1, Math.round(viewportW * runtime.framebufferScaleX()));
        int pixelH = Math.max(1, Math.round(viewportH * runtime.framebufferScaleY()));
        overlay.renderToTexture(pixelW, pixelH, viewProj, cameraPos, centerPos);

        r.drawTexturedRect(overlay.texture(), viewportX, viewportY, viewportW, viewportH, 0.0f, 1.0f, 1.0f, 0.0f, 0xFFFFFFFF);

        boolean dragging = overlay.dragging();
        boolean released = wasDragging && !dragging && ui.input().mouseReleased();
        boolean freeRotation = ui.input().shiftDown();

        if (!wasDragging && dragging) {
            ghosts.startCsgBlock(sel.nodeId(), snappedOrigin, snappedSx, snappedSy, snappedSz, new Vec3d(rx, ry, rz), csgDefaultState);
        }

        if (dragging) {
            int px = Math.round(x);
            int py = Math.round(y);
            int pz = Math.round(z);
            int psx = Math.max(1, Math.round(sx));
            int psy = Math.max(1, Math.round(sy));
            int psz = Math.max(1, Math.round(sz));
            float outRx = freeRotation ? rotDeg.x : snapDeg(rotDeg.x, ROTATION_SNAP_DEG);
            float outRy = freeRotation ? rotDeg.y : snapDeg(rotDeg.y, ROTATION_SNAP_DEG);
            float outRz = freeRotation ? rotDeg.z : snapDeg(rotDeg.z, ROTATION_SNAP_DEG);
            ghosts.setRenderTransform(new Vec3d(px, py, pz), new Vec3d(psx, psy, psz), new Vec3d(outRx, outRy, outRz));
            return;
        }

        if (released) {
            int tx = Math.round(x);
            int ty = Math.round(y);
            int tz = Math.round(z);
            int tsx = Math.max(1, Math.round(sx));
            int tsy = Math.max(1, Math.round(sy));
            int tsz = Math.max(1, Math.round(sz));

            float outRx = freeRotation ? rotDeg.x : snapDeg(rotDeg.x, ROTATION_SNAP_DEG);
            float outRy = freeRotation ? rotDeg.y : snapDeg(rotDeg.y, ROTATION_SNAP_DEG);
            float outRz = freeRotation ? rotDeg.z : snapDeg(rotDeg.z, ROTATION_SNAP_DEG);

            float startCmpRx = freeRotation ? startRx : snapDeg(startRx, ROTATION_SNAP_DEG);
            float startCmpRy = freeRotation ? startRy : snapDeg(startRy, ROTATION_SNAP_DEG);
            float startCmpRz = freeRotation ? startRz : snapDeg(startRz, ROTATION_SNAP_DEG);

            boolean unchanged = tx == startX
                    && ty == startY
                    && tz == startZ
                    && tsx == startSx
                    && tsy == startSy
                    && tsz == startSz
                    && Math.abs(outRx - startCmpRx) < DEG_EPS
                    && Math.abs(outRy - startCmpRy) < DEG_EPS
                    && Math.abs(outRz - startCmpRz) < DEG_EPS;
            if (unchanged) {
                ghosts.cancel();
                return;
            }

            ghosts.commitAndPredict(new Vec3d(tx, ty, tz), new Vec3d(tsx, tsy, tsz), new Vec3d(outRx, outRy, outRz));
            sendCsgTransform(sel.nodeId(), session, state, tx, ty, tz, tsx, tsy, tsz, outRx, outRy, outRz);
        }
    }

    private void sendCsgTransform(long nodeId,
                                  Session session,
                                  EditorState state,
                                  int x,
                                  int y,
                                  int z,
                                  int sx,
                                  int sy,
                                  int sz,
                                  float rxDeg,
                                  float ryDeg,
                                  float rzDeg) {
        ArrayList<SceneOp> ops = new ArrayList<>(9);
        ops.add(new SceneOp.SetProperty(nodeId, "x", Integer.toString(x)));
        ops.add(new SceneOp.SetProperty(nodeId, "y", Integer.toString(y)));
        ops.add(new SceneOp.SetProperty(nodeId, "z", Integer.toString(z)));
        ops.add(new SceneOp.SetProperty(nodeId, "sx", Integer.toString(sx)));
        ops.add(new SceneOp.SetProperty(nodeId, "sy", Integer.toString(sy)));
        ops.add(new SceneOp.SetProperty(nodeId, "sz", Integer.toString(sz)));
        ops.add(new SceneOp.SetProperty(nodeId, "rx", formatFloat(rxDeg)));
        ops.add(new SceneOp.SetProperty(nodeId, "ry", formatFloat(ryDeg)));
        ops.add(new SceneOp.SetProperty(nodeId, "rz", formatFloat(rzDeg)));
        runtime.net().sendOps(session, state, List.copyOf(ops));
    }

    private static float snapDeg(float value, float stepDeg) {
        if (!Float.isFinite(value) || !Float.isFinite(stepDeg) || stepDeg <= 0.0f) {
            return 0.0f;
        }
        return Math.round(value / stepDeg) * stepDeg;
    }

    private static String formatFloat(float value) {
        if (!Float.isFinite(value)) {
            return "0";
        }
        float v = Math.abs(value) < 1e-6f ? 0.0f : value;
        return Float.toString(v);
    }

    private static String defaultFor(NodeTypeDef def, String key, String fallback) {
        if (def == null || def.properties() == null) {
            return fallback;
        }
        PropertyDef prop = def.properties().get(key);
        if (prop == null) {
            return fallback;
        }
        String dv = prop.defaultValue();
        if (dv == null || dv.isBlank()) {
            return fallback;
        }
        return dv;
    }

    private static Map<String, String> toPropertyMap(List<SceneSnapshot.Property> props) {
        HashMap<String, String> map = new HashMap<>();
        if (props == null) {
            return map;
        }
        for (SceneSnapshot.Property prop : props) {
            if (prop == null || prop.key() == null) {
                continue;
            }
            map.put(prop.key(), prop.value());
        }
        return map;
    }

    private static float parseFloat(String s, String fallback) {
        String v = s;
        if (v == null || v.isBlank()) {
            v = fallback;
        }
        if (v == null || v.isBlank()) {
            return 0.0f;
        }
        try {
            return Float.parseFloat(v.trim());
        } catch (NumberFormatException ignored) {
            return 0.0f;
        }
    }

    private static Vector3f minecraftCameraPos() {
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

    private static Matrix4f minecraftViewProjection(float aspect, Vector3f focusWorld) {
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
                .perspective((float) Math.toRadians(DEFAULT_FOV_DEG), Math.max(0.01f, aspect), 0.05f, 512.0f);

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

    private static BlockState resolveBlockState(String value, String fallback) {
        String idString = value;
        if (idString == null || idString.isBlank()) {
            idString = fallback;
        }
        if (idString == null || idString.isBlank()) {
            return Blocks.STONE.getDefaultState();
        }
        Identifier id = Identifier.tryParse(idString.trim());
        if (id == null) {
            return Blocks.STONE.getDefaultState();
        }
        Block b = Registries.BLOCK.get(id);
        if (b == null || b == Blocks.AIR) {
            return Blocks.STONE.getDefaultState();
        }
        return b.getDefaultState();
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

    @Override
    public void close() {
        if (overlay != null) {
            overlay.close();
            overlay = null;
        }
    }
}