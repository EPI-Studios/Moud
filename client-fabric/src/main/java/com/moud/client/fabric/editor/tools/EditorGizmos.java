package com.moud.client.fabric.editor.tools;

import com.moud.client.fabric.editor.state.EditorRuntime;
import com.moud.client.fabric.editor.state.EditorState;
import com.moud.client.fabric.platform.MinecraftGhostBlocks;
import com.moud.client.fabric.platform.MinecraftRenderBridge;

import com.miry.ui.Ui;
import com.miry.ui.render.UiRenderer;
import com.miry.ui.gizmo.GizmoOverlay3D;
import com.miry.ui.gizmo.GizmoSpace;
import com.moud.core.NodeTypeDef;
import com.moud.core.PropertyDef;
import com.moud.net.protocol.SceneOp;
import com.moud.net.protocol.SceneSnapshot;
import com.moud.net.session.Session;
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

        MinecraftGhostBlocks ghosts = MinecraftGhostBlocks.get();
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
            Vector3d sb = ghosts.startBase();
            Vector3d ss = ghosts.startSize();
            Vector3d sr = ghosts.startRotDeg();
            startX = (int) Math.round(sb.x);
            startY = (int) Math.round(sb.y);
            startZ = (int) Math.round(sb.z);
            startSx = Math.max(1, (int) Math.round(ss.x));
            startSy = Math.max(1, (int) Math.round(ss.y));
            startSz = Math.max(1, (int) Math.round(ss.z));
            startRx = (float) sr.x;
            startRy = (float) sr.y;
            startRz = (float) sr.z;

            Vector3d gb = ghosts.renderBase();
            Vector3d gs = ghosts.renderSize();
            Vector3d gr = ghosts.renderRotDeg();
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

        int snappedX = Math.round(x);
        int snappedY = Math.round(y);
        int snappedZ = Math.round(z);
        int snappedSx = Math.max(1, Math.round(sx));
        int snappedSy = Math.max(1, Math.round(sy));
        int snappedSz = Math.max(1, Math.round(sz));

        String csgBlockId = props.get("block");
        if (csgBlockId == null || csgBlockId.isBlank()) {
            csgBlockId = defaultFor(def, "block", "minecraft:stone");
        }

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

        Matrix4f viewProj = MinecraftRenderBridge.viewProjection(DEFAULT_FOV_DEG, viewportW / (float) Math.max(1, viewportH), centerPos);
        Vector3f cameraPos = MinecraftRenderBridge.cameraPos();
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
            ghosts.startCsgBlock(sel.nodeId(), snappedX, snappedY, snappedZ, snappedSx, snappedSy, snappedSz, new Vector3d(rx, ry, rz), csgBlockId);
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
            ghosts.setRenderTransform(new Vector3d(px, py, pz), new Vector3d(psx, psy, psz), new Vector3d(outRx, outRy, outRz));
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

            ghosts.commitAndPredict(new Vector3d(tx, ty, tz), new Vector3d(tsx, tsy, tsz), new Vector3d(outRx, outRy, outRz));
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

    @Override
    public void close() {
        if (overlay != null) {
            overlay.close();
            overlay = null;
        }
    }
}
