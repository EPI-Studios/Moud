package com.moud.client.fabric.editor.tools;


import com.miry.ui.gizmo.GizmoOverlay3D;
import com.miry.ui.gizmo.GizmoSpace;
import com.miry.ui.Ui;
import com.miry.ui.render.UiRenderer;
import com.miry.ui.theme.Theme;
import com.moud.client.fabric.editor.state.EditorRuntime;
import com.moud.client.fabric.editor.state.EditorState;
import com.moud.client.fabric.platform.MinecraftGhostBlocks;
import com.moud.client.fabric.platform.MinecraftRenderBridge;
import com.moud.client.fabric.render.DebugRenderer;
import com.moud.client.fabric.render.VeilDebugRenderer;
import com.moud.client.fabric.scene.ClientSceneBus;
import com.moud.client.fabric.model.BoneNode;
import com.moud.client.fabric.model.ModelAsset;
import com.moud.client.fabric.model.ModelCache;
import com.moud.core.NodeTypeDef;
import com.moud.core.PropertyDef;
import com.moud.net.protocol.SceneOp;
import com.moud.net.protocol.SceneOpBatch;
import com.moud.net.protocol.SceneSnapshot;
import com.moud.net.session.Session;
import com.moud.net.transport.Lane;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.joml.Matrix4f;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.joml.Vector3d;

public final class EditorGizmos implements AutoCloseable {
    private static final float DEFAULT_FOV_DEG = 70.0f;
    private static final float ROTATION_SNAP_DEG = 15.0f;
    private static final float DEG_EPS = 0.001f;
    private static final float SCALE_EPS = 1e-6f;
    private static final long DRAG_SEND_INTERVAL_MS = 50L;

    private final EditorRuntime runtime;
    private GizmoOverlay3D overlay;

    public boolean isDragging() {
        return overlay != null && overlay.dragging();
    }

    private final Matrix3f localAxes = new Matrix3f();
    private final Matrix3f cameraRot = new Matrix3f();
    private final Vector4f clip = new Vector4f();
    private final Vector3f tmpWorld = new Vector3f();
    private final Vector3f focusWorld = new Vector3f();
    private final Vector3f[] frustumWorld = new Vector3f[8];
    private final boolean[] frustumOk = new boolean[8];
    private final int[] frustumX = new int[8];
    private final int[] frustumY = new int[8];

    private final Vector3f basePos = new Vector3f();
    private final Vector3f size = new Vector3f(1, 1, 1);
    private final Vector3f centerPos = new Vector3f();
    private final Vector3f rotDeg = new Vector3f();
    private final Vector3f tmpEulerDeg = new Vector3f();
    private final Vector3f tmpLocal = new Vector3f();
    private long lastDragSendAtMs;
    private long lastDragSendNodeId;
    private boolean dragHasUnsentOps;
    private Map<String, String> dragStartProps;
    private long dragStartNodeId;

    public EditorGizmos(EditorRuntime runtime) {
        this.runtime = runtime;
        for (int i = 0; i < frustumWorld.length; i++) {
            frustumWorld[i] = new Vector3f();
        }
    }

    public boolean tryGetWorldPos(EditorState state, long nodeId, Vector3f out) {
        if (state == null || out == null || nodeId <= 0L) {
            return false;
        }
        Pose world = worldPose(state, nodeId, new HashMap<>());
        out.set(world.pos);
        return true;
    }

    public void update3DGizmos(EditorState state, float aspect) {
        if (state == null || state.scene == null) {
            return;
        }

        DebugRenderer debug = VeilDebugRenderer.instance();
        debug.clear();

        HashMap<Long, Pose> poseCache = new HashMap<>();

        // Render camera frustums in 3D
        for (SceneSnapshot.NodeSnapshot node : state.scene.nodes()) {
            if (node == null || !"Camera3D".equals(node.type())) {
                continue;
            }

            if (!buildCameraFrustumWorld(state, node, aspect, poseCache)) {
                continue;
            }

            boolean active = node.nodeId() == state.selectedId;
            int color = active ? 0xFFFFFF00 : 0xFF00FFFF; // Yellow if selected, cyan otherwise

            debug.frustum(frustumWorld, color, active ? 3f : 2f);

            if (tryReadNodePos(state, node, tmpWorld, poseCache)) {
                float axisLen = active ? 0.35f : 0.25f;
                focusWorld.set(tmpWorld).add(axisLen, 0.0f, 0.0f);
                debug.line(tmpWorld, focusWorld, 0xFFFF4444, 1.0f);
                focusWorld.set(tmpWorld).add(0.0f, axisLen, 0.0f);
                debug.line(tmpWorld, focusWorld, 0xFF44FF44, 1.0f);
                focusWorld.set(tmpWorld).add(0.0f, 0.0f, axisLen);
                debug.line(tmpWorld, focusWorld, 0xFF4444FF, 1.0f);
            }
        }

        for (SceneSnapshot.NodeSnapshot node : state.scene.nodes()) {
            if (node == null || !"PlayerStart".equals(node.type())) {
                continue;
            }
            if (!tryReadNodePos(state, node, tmpWorld, poseCache)) {
                continue;
            }
            boolean active = node.nodeId() == state.selectedId;
            int color = active ? 0xFFFFFF00 : 0xFFFF8800;
            float r = active ? 2.0f : 1.5f;

            focusWorld.set(tmpWorld).add(0, 0.6f, 0);
            debug.line(tmpWorld, focusWorld, color, r);

            Pose psWorld = worldPose(state, node.nodeId(), poseCache);
            Vector3f fwd = new Vector3f(0, 0, 1);
            psWorld.rot.transform(fwd);
            float arrowLen = active ? 1.2f : 0.8f;
            focusWorld.set(tmpWorld).add(fwd.x * arrowLen, fwd.y * arrowLen, fwd.z * arrowLen);
            debug.line(tmpWorld, focusWorld, color, r);
        }

        for (SceneSnapshot.NodeSnapshot node : state.scene.nodes()) {
            if (node == null) continue;
            String type = node.type();
            boolean isLight = "OmniLight3D".equals(type) || "DirectionalLight3D".equals(type) || "SpotLight3D".equals(type);
            if (!isLight) continue;

            Pose lp = worldPose(state, node.nodeId(), poseCache);
            if (lp == null) continue;
            tmpWorld.set(lp.pos);

            boolean active = node.nodeId() == state.selectedId;
            Map<String, String> lProps = toPropertyMap(node.properties());
            float cr = parseFloat(lProps.get("color_r"), "1");
            float cg = parseFloat(lProps.get("color_g"), "1");
            float cb = parseFloat(lProps.get("color_b"), "1");
            int lightColor = 0xFF000000
                    | (Math.min(255, (int) (cr * 255)) << 16)
                    | (Math.min(255, (int) (cg * 255)) << 8)
                    | Math.min(255, (int) (cb * 255));
            if (!active) lightColor = (lightColor & 0x00FFFFFF) | 0x99000000;

            if ("OmniLight3D".equals(type)) {
                float radius = parseFloat(lProps.get("radius"), "8");
                int segs = active ? 48 : 24;
                debug.sphere(tmpWorld, 0.1f, lightColor, 8);
                debug.circle(tmpWorld, radius, new Vector3f(1, 0, 0), lightColor, segs);
                debug.circle(tmpWorld, radius, new Vector3f(0, 1, 0), lightColor, segs);
                debug.circle(tmpWorld, radius, new Vector3f(0, 0, 1), lightColor, segs);
            } else if ("DirectionalLight3D".equals(type)) {
                Vector3f dir = new Vector3f(0, 0, 1);
                lp.rot.transform(dir);
                if (dir.lengthSquared() > 1e-12f) dir.normalize();

                float diskR = 0.25f;
                debug.circle(tmpWorld, diskR, dir, lightColor, 16);

                Vector3f up = new Vector3f(0, 1, 0);
                if (Math.abs(dir.dot(up)) > 0.99f) up.set(1, 0, 0);
                Vector3f right = new Vector3f(dir).cross(up).normalize();
                Vector3f upVec = new Vector3f(right).cross(dir).normalize();

                float arrowLen = active ? 2.5f : 1.5f;
                focusWorld.set(tmpWorld).add(dir.x * arrowLen, dir.y * arrowLen, dir.z * arrowLen);
                debug.line(tmpWorld, focusWorld, lightColor, active ? 2f : 1.2f);
                for (int i = 0; i < 4; i++) {
                    float a = (float) (i * Math.PI * 2.0 / 4.0);
                    float ox = (float) Math.cos(a) * diskR;
                    float oy = (float) Math.sin(a) * diskR;
                    Vector3f rayStart = new Vector3f(tmpWorld).add(right.x * ox + upVec.x * oy, right.y * ox + upVec.y * oy, right.z * ox + upVec.z * oy);
                    Vector3f rayEnd = new Vector3f(rayStart).add(dir.x * arrowLen, dir.y * arrowLen, dir.z * arrowLen);
                    debug.line(rayStart, rayEnd, lightColor, active ? 1.5f : 1.0f);
                }
                float headLen = 0.3f;
                float headR = 0.12f;
                Vector3f headBase = new Vector3f(focusWorld).sub(dir.x * headLen, dir.y * headLen, dir.z * headLen);
                for (int i = 0; i < 4; i++) {
                    float a = (float) (i * Math.PI * 2.0 / 4.0);
                    float ox = (float) Math.cos(a) * headR;
                    float oy = (float) Math.sin(a) * headR;
                    Vector3f headEdge = new Vector3f(headBase).add(right.x * ox + upVec.x * oy, right.y * ox + upVec.y * oy, right.z * ox + upVec.z * oy);
                    debug.line(focusWorld, headEdge, lightColor, active ? 1.5f : 1.0f);
                }
            } else {
                // SpotLight3D
                float angleDeg = parseFloat(lProps.get("angle"), "45");
                float distance = parseFloat(lProps.get("distance"), "10");
                Vector3f dir = new Vector3f(0, 0, 1);
                lp.rot.transform(dir);
                if (dir.lengthSquared() > 1e-12f) dir.normalize();

                debug.sphere(tmpWorld, 0.08f, lightColor, 8);

                float halfAngleRad = (float) Math.toRadians(angleDeg * 0.5f);
                float coneRadius = distance * (float) Math.tan(halfAngleRad);
                focusWorld.set(tmpWorld).add(dir.x * distance, dir.y * distance, dir.z * distance);

                Vector3f up = new Vector3f(0, 1, 0);
                if (Math.abs(dir.dot(up)) > 0.99f) up.set(1, 0, 0);
                Vector3f right = new Vector3f(dir).cross(up).normalize();
                Vector3f upVec = new Vector3f(right).cross(dir).normalize();

                for (int i = 0; i < 4; i++) {
                    float a = (float) (i * Math.PI * 2.0 / 4.0);
                    float ox = (float) Math.cos(a) * coneRadius;
                    float oy = (float) Math.sin(a) * coneRadius;
                    Vector3f edge = new Vector3f(focusWorld)
                            .add(right.x * ox + upVec.x * oy, right.y * ox + upVec.y * oy, right.z * ox + upVec.z * oy);
                    debug.line(tmpWorld, edge, lightColor, active ? 1.5f : 1.0f);
                }

                debug.circle(focusWorld, coneRadius, dir, lightColor, active ? 48 : 24);

                if (active) {
                    float midDist = distance * 0.5f;
                    float midRadius = midDist * (float) Math.tan(halfAngleRad);
                    Vector3f midCenter = new Vector3f(tmpWorld).add(dir.x * midDist, dir.y * midDist, dir.z * midDist);
                    int dimColor = (lightColor & 0x00FFFFFF) | 0x66000000;
                    debug.circle(midCenter, midRadius, dir, dimColor, 24);
                }

                debug.line(tmpWorld, focusWorld, lightColor, active ? 1.2f : 0.8f);
            }
        }

        for (SceneSnapshot.NodeSnapshot node : state.scene.nodes()) {
            if (node == null) continue;
            String type = node.type();
            boolean isPhys = "StaticBody3D".equals(type) || "RigidBody3D".equals(type)
                    || "CharacterBody3D".equals(type) || "Area3D".equals(type);
            boolean isRay = "Raycast3D".equals(type);
            boolean isMarker = "Marker3D".equals(type);
            if (!isPhys && !isRay && !isMarker) continue;

            Pose pp = worldPose(state, node.nodeId(), poseCache);
            if (pp == null) continue;
            tmpWorld.set(pp.pos);
            boolean active = node.nodeId() == state.selectedId;
            Map<String, String> pProps = toPropertyMap(node.properties());

            if (isMarker) {
                float gs = parseFloat(pProps.get("gizmo_size"), "0.5");
                int mc = active ? 0xFFFF44FF : 0xFF9944CC;
                focusWorld.set(tmpWorld).add(gs, 0, 0); debug.line(tmpWorld, focusWorld, 0xFFFF4444, 1.5f);
                focusWorld.set(tmpWorld).add(0, gs, 0); debug.line(tmpWorld, focusWorld, 0xFF44FF44, 1.5f);
                focusWorld.set(tmpWorld).add(0, 0, gs); debug.line(tmpWorld, focusWorld, 0xFF4444FF, 1.5f);
                debug.sphere(tmpWorld, 0.08f, mc, 6);
            } else if (isRay) {
                float tx = parseFloat(pProps.get("target_x"), "0");
                float ty = parseFloat(pProps.get("target_y"), "-1");
                float tz = parseFloat(pProps.get("target_z"), "0");
                float maxDist = parseFloat(pProps.get("max_distance"), "100");
                Vector3f dir = new Vector3f(tx, ty, tz);
                pp.rot.transform(dir);
                if (dir.lengthSquared() > 1e-12f) dir.normalize();
                focusWorld.set(tmpWorld).add(dir.x * maxDist, dir.y * maxDist, dir.z * maxDist);
                int rc = active ? 0xFFFF4444 : 0xFFAA2222;
                debug.line(tmpWorld, focusWorld, rc, active ? 2f : 1f);
            } else {
                String shape = pProps.getOrDefault("shape", "box");
                int pc = active ? 0xFF44FF44 : 0xFF228822;
                if ("sphere".equals(shape)) {
                    float r = parseFloat(pProps.get("radius"), "0.5");
                    debug.sphere(tmpWorld, r, pc, active ? 16 : 8);
                } else if ("capsule".equals(shape)) {
                    float r = parseFloat(pProps.get("radius"), "0.3");
                    float h = parseFloat(pProps.get("height"), "1.8");
                    debug.sphere(tmpWorld, r, pc, active ? 12 : 6);
                    focusWorld.set(tmpWorld).add(0, h, 0);
                    debug.sphere(focusWorld, r, pc, active ? 12 : 6);
                    Vector3f top = new Vector3f(tmpWorld).add(0, h, 0);
                    debug.line(new Vector3f(tmpWorld.x + r, tmpWorld.y, tmpWorld.z), new Vector3f(top.x + r, top.y, top.z), pc, 1f);
                    debug.line(new Vector3f(tmpWorld.x - r, tmpWorld.y, tmpWorld.z), new Vector3f(top.x - r, top.y, top.z), pc, 1f);
                    debug.line(new Vector3f(tmpWorld.x, tmpWorld.y, tmpWorld.z + r), new Vector3f(top.x, top.y, top.z + r), pc, 1f);
                    debug.line(new Vector3f(tmpWorld.x, tmpWorld.y, tmpWorld.z - r), new Vector3f(top.x, top.y, top.z - r), pc, 1f);
                } else {
                    float hx = parseFloat(pProps.get("sx"), "1") * 0.5f;
                    float hy = parseFloat(pProps.get("sy"), "1") * 0.5f;
                    float hz = parseFloat(pProps.get("sz"), "1") * 0.5f;
                    Vector3f mn = new Vector3f(tmpWorld.x - hx, tmpWorld.y - hy, tmpWorld.z - hz);
                    Vector3f mx = new Vector3f(tmpWorld.x + hx, tmpWorld.y + hy, tmpWorld.z + hz);
                    debug.box(mn, mx, pc, active ? 2f : 1f);
                }
            }
        }

        SceneSnapshot.NodeSnapshot selected = state.scene.getNode(state.selectedId);
        if (selected != null && "Model3D".equals(selected.type())) {
            Map<String, String> props = toPropertyMap(selected.properties());
            String modelPath = props.get("model_path");
            if (modelPath != null) {
                modelPath = modelPath.trim();
            }
            if (modelPath != null && !modelPath.isBlank()) {
                ModelAsset asset = ModelCache.get(modelPath);
                if (asset != null) {
                    Pose world = worldPose(state, selected.nodeId(), poseCache);
                    int color = 0xFFFFFF00;
                    renderModelBones(debug, world, asset, 0.07f, color);
                }
            }
        }
    }

    private void renderModelBones(DebugRenderer debug, Pose world, ModelAsset asset, float radius, int color) {
        if (debug == null || world == null || asset == null) {
            return;
        }
        for (var root : asset.rootBones()) {
            renderBoneRecursive(debug, world, root, null, radius, color);
        }
    }

    private void renderBoneRecursive(DebugRenderer debug,
                                     Pose world,
                                     BoneNode bone,
                                     Vector3f parentWorld,
                                     float radius,
                                     int color) {
        if (bone == null) {
            return;
        }
        Vector3f pivotLocal = new Vector3f(bone.pivotX() / 16f, bone.pivotY() / 16f, bone.pivotZ() / 16f);
        pivotLocal.mul(world.scale);
        world.rot.transform(pivotLocal);
        pivotLocal.add(world.pos);
        debug.sphere(pivotLocal, radius, color, 10);
        if (parentWorld != null) {
            debug.line(parentWorld, pivotLocal, color, 1.5f);
        }
        if (bone.children() != null) {
            for (var child : bone.children()) {
                renderBoneRecursive(debug, world, child, pivotLocal, radius, color);
            }
        }
    }

    public void render(Ui ui, UiRenderer r, int viewportX, int viewportY, int viewportW, int viewportH) {
        if (ui == null || r == null || viewportW <= 0 || viewportH <= 0) {
            return;
        }
        if (runtime != null && runtime.uiBlocked()) {
            return;
        }
        var input = ui.input();
        if (input == null) {
            return;
        }

        EditorState state = runtime.state();
        if (state == null) {
            return;
        }
        // Camera frustums now rendered in 3D via DebugRenderer (see update3DGizmos)
        // renderCameraFrustums(ui, r, state, viewportX, viewportY, viewportW, viewportH);

        if (runtime.tool() == null || runtime.tool() == EditorTool.SELECT) {
            return;
        }

        SceneSnapshot.NodeSnapshot sel = state.scene.getNode(state.selectedId);
        if (sel == null) {
            return;
        }
        String typeId = sel.type();
        boolean isCsgBlock = "CSGBlock".equals(typeId);
        boolean isCsgBox = "CSGBox".equals(typeId);
        boolean pivotIsMinCorner = isCsgBlock || isCsgBox;

        Session session = runtime.session();
        if (session == null) {
            return;
        }

        if (overlay == null) {
            overlay = new GizmoOverlay3D();
        }

        boolean localPref = runtime != null && runtime.gizmoLocalSpace();
        overlay.setGizmoSpace((localPref ^ input.altDown()) ? GizmoSpace.LOCAL : GizmoSpace.WORLD);

        NodeTypeDef def = state.typesById.get(typeId);
        Map<String, String> props = toPropertyMap(sel.properties());
        if (parseBool(props.get("editor_locked"), false) || parseBool(props.get("@locked"), false)) {
            return;
        }

        boolean canMove = hasProp(def, props, "x") || hasProp(def, props, "y") || hasProp(def, props, "z");
        boolean canRotate = hasProp(def, props, "rx") || hasProp(def, props, "ry") || hasProp(def, props, "rz");
        boolean canScale = hasProp(def, props, "sx") || hasProp(def, props, "sy") || hasProp(def, props, "sz");

        if (runtime.tool() == EditorTool.MOVE && !canMove) {
            return;
        }
        if (runtime.tool() == EditorTool.ROTATE && !canRotate) {
            return;
        }
        if (runtime.tool() == EditorTool.SCALE && !canScale) {
            return;
        }

        float x = parseFloat(props.get("x"), defaultFor(def, "x", "0"));
        float y = parseFloat(props.get("y"), defaultFor(def, "y", isCsgBlock ? "41" : "0"));
        float z = parseFloat(props.get("z"), defaultFor(def, "z", "0"));

        float scaleMin = pivotIsMinCorner ? 1.0f : SCALE_EPS;
        float sx = canScale ? Math.max(scaleMin, parseFloat(props.get("sx"), defaultFor(def, "sx", "1"))) : 1.0f;
        float sy = canScale ? Math.max(scaleMin, parseFloat(props.get("sy"), defaultFor(def, "sy", "1"))) : 1.0f;
        float sz = canScale ? Math.max(scaleMin, parseFloat(props.get("sz"), defaultFor(def, "sz", "1"))) : 1.0f;

        float rx = parseFloat(props.get("rx"), defaultFor(def, "rx", "0"));
        float ry = parseFloat(props.get("ry"), defaultFor(def, "ry", "0"));
        float rz = parseFloat(props.get("rz"), defaultFor(def, "rz", "0"));

        MinecraftGhostBlocks ghosts = MinecraftGhostBlocks.get();
        float startX = x;
        float startY = y;
        float startZ = z;
        float startSx = sx;
        float startSy = sy;
        float startSz = sz;
        float startRx = rx;
        float startRy = ry;
        float startRz = rz;

        if (isCsgBlock && ghosts.isActive() && ghosts.nodeId() == sel.nodeId()) {
            Vector3d sb = ghosts.startBase();
            Vector3d ss = ghosts.startSize();
            Vector3d sr = ghosts.startRotDeg();
            startX = (float) sb.x;
            startY = (float) sb.y;
            startZ = (float) sb.z;
            startSx = (float) ss.x;
            startSy = (float) ss.y;
            startSz = (float) ss.z;
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

        String csgBlockId = null;
        if (isCsgBlock) {
            csgBlockId = props.get("block");
            if (csgBlockId == null || csgBlockId.isBlank()) {
                csgBlockId = defaultFor(def, "block", "minecraft:stone");
            }
        }

        Quaternionf worldRot = new Quaternionf();
        HashMap<Long, Pose> poseCache = new HashMap<>();

        if (isCsgBlock) {
            basePos.set(x, y, z);
            size.set(sx, sy, sz);
            centerPos.set(basePos).fma(0.5f, size);
            worldRot.set(quatFromEulerDeg(rx, ry, rz));
            rotDeg.set(rx, ry, rz);
        } else {
            Pose world = worldPose(state, sel.nodeId(), poseCache);
            centerPos.set(world.pos);
            worldRot.set(world.rot);
            tmpEulerDeg.set(eulerDeg(worldRot));
            rotDeg.set(tmpEulerDeg);
            if (pivotIsMinCorner) {
                size.set(world.scale);
            } else {
                size.set(1.0f, 1.0f, 1.0f);
            }
        }

        localAxes.identity().set(worldRot);
        overlay.setLocalAxes(localAxes);

        overlay.setMode(switch (runtime.tool()) {
            case MOVE -> GizmoOverlay3D.Mode.TRANSLATE;
            case SCALE -> GizmoOverlay3D.Mode.SCALE;
            case ROTATE -> GizmoOverlay3D.Mode.ROTATE;
            default -> GizmoOverlay3D.Mode.NONE;
        });
        overlay.setFaceHandlesEnabled(canScale);

        Matrix4f viewProj = MinecraftRenderBridge.viewProjection(DEFAULT_FOV_DEG, viewportW / (float) Math.max(1, viewportH), centerPos);
        Vector3f cameraPos = MinecraftRenderBridge.cameraPos();
        if (viewProj == null || cameraPos == null) {
            return;
        }

        boolean wasDragging = overlay.dragging();
        overlay.updateInput(
                input,
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

        localAxes.identity().set(quatFromEulerDeg(rotDeg.x, rotDeg.y, rotDeg.z));
        overlay.setLocalAxes(localAxes);

        if (pivotIsMinCorner) {
            basePos.set(centerPos).fma(-0.5f, size);
            x = basePos.x;
            y = basePos.y;
            z = basePos.z;
            sx = size.x;
            sy = size.y;
            sz = size.z;
        } else {
            x = centerPos.x;
            y = centerPos.y;
            z = centerPos.z;
        }

        if (ghosts.isActive() && ghosts.nodeId() != sel.nodeId()) {
            ghosts.cancel();
        }

        int pixelW = Math.max(1, Math.round(viewportW * runtime.framebufferScaleX()));
        int pixelH = Math.max(1, Math.round(viewportH * runtime.framebufferScaleY()));
        overlay.renderToTexture(pixelW, pixelH, viewProj, cameraPos, centerPos);

        r.drawTexturedRect(overlay.texture(), viewportX, viewportY, viewportW, viewportH, 0.0f, 1.0f, 1.0f, 0.0f, 0xFFFFFFFF);

        boolean dragging = overlay.dragging();
        boolean released = wasDragging && !dragging && input.mouseReleased();
        boolean freeRotation = input.shiftDown() || (runtime != null && !runtime.rotationSnapEnabled());
        boolean snapMove = runtime != null
                && runtime.gridSnapEnabled()
                && runtime.tool() == EditorTool.MOVE
                && !input.shiftDown();
        float snapStep = runtime == null ? 1.0f : runtime.gridSnapStep();
        float rotSnapDeg = runtime == null ? ROTATION_SNAP_DEG : runtime.rotationSnapDeg();

        if (isCsgBlock && !wasDragging && dragging) {
            int snappedX = Math.round(x);
            int snappedY = Math.round(y);
            int snappedZ = Math.round(z);
            int snappedSx = Math.max(1, Math.round(sx));
            int snappedSy = Math.max(1, Math.round(sy));
            int snappedSz = Math.max(1, Math.round(sz));
            ghosts.startCsgBlock(sel.nodeId(), snappedX, snappedY, snappedZ, snappedSx, snappedSy, snappedSz, new Vector3d(rx, ry, rz), csgBlockId);
        }

        if (dragging) {
            if (!wasDragging) {
                lastDragSendNodeId = sel.nodeId();
                lastDragSendAtMs = 0L;
                dragHasUnsentOps = false;
                dragStartNodeId = sel.nodeId();
                dragStartProps = new HashMap<>(props);
            }

            if (isCsgBlock) {
                int px = Math.round(x);
                int py = Math.round(y);
                int pz = Math.round(z);
                int psx = Math.max(1, Math.round(sx));
                int psy = Math.max(1, Math.round(sy));
                int psz = Math.max(1, Math.round(sz));
                float outRx = freeRotation ? rotDeg.x : snapDeg(rotDeg.x, rotSnapDeg);
                float outRy = freeRotation ? rotDeg.y : snapDeg(rotDeg.y, rotSnapDeg);
                float outRz = freeRotation ? rotDeg.z : snapDeg(rotDeg.z, rotSnapDeg);
                ghosts.setRenderTransform(new Vector3d(px, py, pz), new Vector3d(psx, psy, psz), new Vector3d(outRx, outRy, outRz));
                return;
            }

            float outRx = canRotate ? (freeRotation ? rotDeg.x : snapDeg(rotDeg.x, rotSnapDeg)) : 0.0f;
            float outRy = canRotate ? (freeRotation ? rotDeg.y : snapDeg(rotDeg.y, rotSnapDeg)) : 0.0f;
            float outRz = canRotate ? (freeRotation ? rotDeg.z : snapDeg(rotDeg.z, rotSnapDeg)) : 0.0f;

            float refRx = canRotate ? parseFloat(props.get("rx"), defaultFor(def, "rx", "0")) : 0.0f;
            float refRy = canRotate ? parseFloat(props.get("ry"), defaultFor(def, "ry", "0")) : 0.0f;
            float refRz = canRotate ? parseFloat(props.get("rz"), defaultFor(def, "rz", "0")) : 0.0f;
            if (!freeRotation) {
                refRx = snapDeg(refRx, rotSnapDeg);
                refRy = snapDeg(refRy, rotSnapDeg);
                refRz = snapDeg(refRz, rotSnapDeg);
            }

            ArrayList<SceneOp> ops;
            if (pivotIsMinCorner) {
                float outWorldSx = size.x;
                float outWorldSy = size.y;
                float outWorldSz = size.z;

                float outWorldX = x;
                float outWorldY = y;
                float outWorldZ = z;
                if (snapMove) {
                    outWorldX = snapToStep(outWorldX, snapStep);
                    outWorldY = snapToStep(outWorldY, snapStep);
                    outWorldZ = snapToStep(outWorldZ, snapStep);
                }

                float outWorldPivotX = outWorldX + outWorldSx * 0.5f;
                float outWorldPivotY = outWorldY + outWorldSy * 0.5f;
                float outWorldPivotZ = outWorldZ + outWorldSz * 0.5f;

                float outLocalPivotX = outWorldPivotX;
                float outLocalPivotY = outWorldPivotY;
                float outLocalPivotZ = outWorldPivotZ;
                float outLocalSx = outWorldSx;
                float outLocalSy = outWorldSy;
                float outLocalSz = outWorldSz;

                float outLocalRx = unwrapDeg(outRx, refRx);
                float outLocalRy = unwrapDeg(outRy, refRy);
                float outLocalRz = unwrapDeg(outRz, refRz);

                boolean inherit = shouldInheritTransform(props.get("@inherit_transform"));
                long parentId = sel.parentId();
                if (inherit && parentId > 0L) {
                    Pose parent = worldPose(state, parentId, poseCache);

                    tmpLocal.set(outWorldPivotX - parent.pos.x, outWorldPivotY - parent.pos.y, outWorldPivotZ - parent.pos.z);
                    Quaternionf inv = new Quaternionf(parent.rot).conjugate();
                    inv.transform(tmpLocal);
                    tmpLocal.div(
                            parent.scale.x == 0.0f ? 1.0f : parent.scale.x,
                            parent.scale.y == 0.0f ? 1.0f : parent.scale.y,
                            parent.scale.z == 0.0f ? 1.0f : parent.scale.z
                    );
                    outLocalPivotX = tmpLocal.x;
                    outLocalPivotY = tmpLocal.y;
                    outLocalPivotZ = tmpLocal.z;

                    outLocalSx = outWorldSx / (parent.scale.x == 0.0f ? 1.0f : parent.scale.x);
                    outLocalSy = outWorldSy / (parent.scale.y == 0.0f ? 1.0f : parent.scale.y);
                    outLocalSz = outWorldSz / (parent.scale.z == 0.0f ? 1.0f : parent.scale.z);

                    Quaternionf worldRote = quatFromEulerDeg(outRx, outRy, outRz);
                    Quaternionf localRot = new Quaternionf(inv).mul(worldRote).normalize();
                    Vector3f euler = eulerDeg(localRot);
                    outLocalRx = unwrapDeg(euler.x, refRx);
                    outLocalRy = unwrapDeg(euler.y, refRy);
                    outLocalRz = unwrapDeg(euler.z, refRz);
                }

                outLocalSx = Math.max(1.0f, outLocalSx);
                outLocalSy = Math.max(1.0f, outLocalSy);
                outLocalSz = Math.max(1.0f, outLocalSz);

                float outLocalX = outLocalPivotX - outLocalSx * 0.5f;
                float outLocalY = outLocalPivotY - outLocalSy * 0.5f;
                float outLocalZ = outLocalPivotZ - outLocalSz * 0.5f;

                ops = buildBoxTransformOps(sel.nodeId(), def, props, outLocalX, outLocalY, outLocalZ, outLocalSx, outLocalSy, outLocalSz, outLocalRx, outLocalRy, outLocalRz);
            } else {
                float outWorldX = x;
                float outWorldY = y;
                float outWorldZ = z;
                if (snapMove) {
                    outWorldX = snapToStep(outWorldX, snapStep);
                    outWorldY = snapToStep(outWorldY, snapStep);
                    outWorldZ = snapToStep(outWorldZ, snapStep);
                }
                if (input.shiftDown() && runtime != null && runtime.tool() == EditorTool.MOVE) {
                    float bestY = Float.NEGATIVE_INFINITY;
                    boolean foundSurface = false;
                    for (SceneSnapshot.NodeSnapshot other : state.scene.nodes()) {
                        if (other == null || other.nodeId() == sel.nodeId()) continue;
                        Map<String, String> op = toPropertyMap(other.properties());
                        float ox = parseFloat(op.get("x"), "0");
                        float oz = parseFloat(op.get("z"), "0");
                        if (Math.abs(ox - outWorldX) <= 2.0f && Math.abs(oz - outWorldZ) <= 2.0f) {
                            float oy = parseFloat(op.get("y"), "0");
                            if (!foundSurface || oy > bestY) {
                                bestY = oy;
                                foundSurface = true;
                            }
                        }
                    }
                    if (foundSurface) {
                        outWorldY = bestY;
                    }
                }

                float outLocalX = outWorldX;
                float outLocalY = outWorldY;
                float outLocalZ = outWorldZ;

                float outLocalRx = unwrapDeg(outRx, refRx);
                float outLocalRy = unwrapDeg(outRy, refRy);
                float outLocalRz = unwrapDeg(outRz, refRz);

                boolean inherit = shouldInheritTransform(props.get("@inherit_transform"));
                long parentId = sel.parentId();
                if (inherit && parentId > 0L) {
                    Pose parent = worldPose(state, parentId, poseCache);

                    tmpLocal.set(outWorldX - parent.pos.x, outWorldY - parent.pos.y, outWorldZ - parent.pos.z);
                    Quaternionf inv = new Quaternionf(parent.rot).conjugate();
                    inv.transform(tmpLocal);
                    tmpLocal.div(
                            parent.scale.x == 0.0f ? 1.0f : parent.scale.x,
                            parent.scale.y == 0.0f ? 1.0f : parent.scale.y,
                            parent.scale.z == 0.0f ? 1.0f : parent.scale.z
                    );
                    outLocalX = tmpLocal.x;
                    outLocalY = tmpLocal.y;
                    outLocalZ = tmpLocal.z;

                    Quaternionf worldRote = quatFromEulerDeg(outRx, outRy, outRz);
                    Quaternionf localRot = new Quaternionf(inv).mul(worldRote).normalize();
                    Vector3f euler = eulerDeg(localRot);
                    outLocalRx = unwrapDeg(euler.x, refRx);
                    outLocalRy = unwrapDeg(euler.y, refRy);
                    outLocalRz = unwrapDeg(euler.z, refRz);
                }

                ops = buildNodeTransformOps(sel.nodeId(), def, props, outLocalX, outLocalY, outLocalZ, outLocalRx, outLocalRy, outLocalRz);
            }

            if (!ops.isEmpty()) {
                state.scene.applyOps(ops);
                ClientSceneBus.applyOps(ops);
                boolean sent = sendOpsThrottled(session, state, sel.nodeId(), ops);
                dragHasUnsentOps = !sent;
            }
            return;
        }

        if (released) {
            boolean forceSend = dragHasUnsentOps && lastDragSendNodeId == sel.nodeId();
            dragHasUnsentOps = false;

            float outRx = canRotate ? (freeRotation ? rotDeg.x : snapDeg(rotDeg.x, rotSnapDeg)) : 0.0f;
            float outRy = canRotate ? (freeRotation ? rotDeg.y : snapDeg(rotDeg.y, rotSnapDeg)) : 0.0f;
            float outRz = canRotate ? (freeRotation ? rotDeg.z : snapDeg(rotDeg.z, rotSnapDeg)) : 0.0f;

            float startCmpRx = canRotate ? (freeRotation ? startRx : snapDeg(startRx, rotSnapDeg)) : 0.0f;
            float startCmpRy = canRotate ? (freeRotation ? startRy : snapDeg(startRy, rotSnapDeg)) : 0.0f;
            float startCmpRz = canRotate ? (freeRotation ? startRz : snapDeg(startRz, rotSnapDeg)) : 0.0f;

            if (pivotIsMinCorner) {
                float outWorldSx = size.x;
                float outWorldSy = size.y;
                float outWorldSz = size.z;

                float outWorldX = x;
                float outWorldY = y;
                float outWorldZ = z;
                if (snapMove) {
                    outWorldX = snapToStep(outWorldX, snapStep);
                    outWorldY = snapToStep(outWorldY, snapStep);
                    outWorldZ = snapToStep(outWorldZ, snapStep);
                }

                float outWorldPivotX = outWorldX + outWorldSx * 0.5f;
                float outWorldPivotY = outWorldY + outWorldSy * 0.5f;
                float outWorldPivotZ = outWorldZ + outWorldSz * 0.5f;

                float outLocalPivotX = outWorldPivotX;
                float outLocalPivotY = outWorldPivotY;
                float outLocalPivotZ = outWorldPivotZ;
                float outLocalSx = outWorldSx;
                float outLocalSy = outWorldSy;
                float outLocalSz = outWorldSz;

                float outLocalRx = unwrapDeg(outRx, startCmpRx);
                float outLocalRy = unwrapDeg(outRy, startCmpRy);
                float outLocalRz = unwrapDeg(outRz, startCmpRz);

                boolean inherit = !isCsgBlock && shouldInheritTransform(props.get("@inherit_transform"));
                long parentId = sel.parentId();
                if (inherit && parentId > 0L) {
                    Pose parent = worldPose(state, parentId, poseCache);

                    tmpLocal.set(outWorldPivotX - parent.pos.x, outWorldPivotY - parent.pos.y, outWorldPivotZ - parent.pos.z);
                    Quaternionf inv = new Quaternionf(parent.rot).conjugate();
                    inv.transform(tmpLocal);
                    tmpLocal.div(
                            parent.scale.x == 0.0f ? 1.0f : parent.scale.x,
                            parent.scale.y == 0.0f ? 1.0f : parent.scale.y,
                            parent.scale.z == 0.0f ? 1.0f : parent.scale.z
                    );
                    outLocalPivotX = tmpLocal.x;
                    outLocalPivotY = tmpLocal.y;
                    outLocalPivotZ = tmpLocal.z;

                    outLocalSx = outWorldSx / (parent.scale.x == 0.0f ? 1.0f : parent.scale.x);
                    outLocalSy = outWorldSy / (parent.scale.y == 0.0f ? 1.0f : parent.scale.y);
                    outLocalSz = outWorldSz / (parent.scale.z == 0.0f ? 1.0f : parent.scale.z);

                    Quaternionf worldRote = quatFromEulerDeg(outRx, outRy, outRz);
                    Quaternionf localRot = new Quaternionf(inv).mul(worldRote).normalize();
                    Vector3f euler = eulerDeg(localRot);
                    outLocalRx = unwrapDeg(euler.x, startCmpRx);
                    outLocalRy = unwrapDeg(euler.y, startCmpRy);
                    outLocalRz = unwrapDeg(euler.z, startCmpRz);
                }

                outLocalSx = Math.max(1.0f, outLocalSx);
                outLocalSy = Math.max(1.0f, outLocalSy);
                outLocalSz = Math.max(1.0f, outLocalSz);

                float outLocalX = outLocalPivotX - outLocalSx * 0.5f;
                float outLocalY = outLocalPivotY - outLocalSy * 0.5f;
                float outLocalZ = outLocalPivotZ - outLocalSz * 0.5f;

                if (isCsgBlock) {
                    int tx = Math.round(outLocalX);
                    int ty = Math.round(outLocalY);
                    int tz = Math.round(outLocalZ);
                    int tsx = Math.max(1, Math.round(outLocalSx));
                    int tsy = Math.max(1, Math.round(outLocalSy));
                    int tsz = Math.max(1, Math.round(outLocalSz));

                    boolean unchanged = Math.round(startX) == tx
                            && Math.round(startY) == ty
                            && Math.round(startZ) == tz
                            && Math.round(startSx) == tsx
                            && Math.round(startSy) == tsy
                            && Math.round(startSz) == tsz
                            && Math.abs(outLocalRx - startCmpRx) < DEG_EPS
                            && Math.abs(outLocalRy - startCmpRy) < DEG_EPS
                            && Math.abs(outLocalRz - startCmpRz) < DEG_EPS;
                    if (unchanged && !forceSend) {
                        ghosts.cancel();
                        return;
                    }

                    ghosts.commitAndPredict(new Vector3d(tx, ty, tz), new Vector3d(tsx, tsy, tsz), new Vector3d(outLocalRx, outLocalRy, outLocalRz));
                    sendCsgTransform(sel.nodeId(), session, state, tx, ty, tz, tsx, tsy, tsz, outLocalRx, outLocalRy, outLocalRz);
                } else if (isCsgBox) {
                    boolean unchanged = Math.abs(outLocalX - startX) < 1e-6f
                            && Math.abs(outLocalY - startY) < 1e-6f
                            && Math.abs(outLocalZ - startZ) < 1e-6f
                            && Math.abs(outLocalSx - startSx) < 1e-6f
                            && Math.abs(outLocalSy - startSy) < 1e-6f
                            && Math.abs(outLocalSz - startSz) < 1e-6f
                            && Math.abs(outLocalRx - startCmpRx) < DEG_EPS
                            && Math.abs(outLocalRy - startCmpRy) < DEG_EPS
                            && Math.abs(outLocalRz - startCmpRz) < DEG_EPS;
                    if (unchanged && !forceSend) {
                        return;
                    }

                    sendBoxTransform(sel.nodeId(), session, state, outLocalX, outLocalY, outLocalZ, outLocalSx, outLocalSy, outLocalSz, outLocalRx, outLocalRy, outLocalRz);
                }
            } else {
                float outWorldX = x;
                float outWorldY = y;
                float outWorldZ = z;
                if (snapMove) {
                    outWorldX = snapToStep(outWorldX, snapStep);
                    outWorldY = snapToStep(outWorldY, snapStep);
                    outWorldZ = snapToStep(outWorldZ, snapStep);
                }

                float outLocalX = outWorldX;
                float outLocalY = outWorldY;
                float outLocalZ = outWorldZ;

                float outLocalRx = unwrapDeg(outRx, startCmpRx);
                float outLocalRy = unwrapDeg(outRy, startCmpRy);
                float outLocalRz = unwrapDeg(outRz, startCmpRz);

                boolean inherit = shouldInheritTransform(props.get("@inherit_transform"));
                long parentId = sel.parentId();
                if (inherit && parentId > 0L) {
                    Pose parent = worldPose(state, parentId, poseCache);

                    tmpLocal.set(outWorldX - parent.pos.x, outWorldY - parent.pos.y, outWorldZ - parent.pos.z);
                    Quaternionf inv = new Quaternionf(parent.rot).conjugate();
                    inv.transform(tmpLocal);
                    tmpLocal.div(
                            parent.scale.x == 0.0f ? 1.0f : parent.scale.x,
                            parent.scale.y == 0.0f ? 1.0f : parent.scale.y,
                            parent.scale.z == 0.0f ? 1.0f : parent.scale.z
                    );
                    outLocalX = tmpLocal.x;
                    outLocalY = tmpLocal.y;
                    outLocalZ = tmpLocal.z;

                    Quaternionf worldRote = quatFromEulerDeg(outRx, outRy, outRz);
                    Quaternionf localRot = new Quaternionf(inv).mul(worldRote).normalize();
                    Vector3f euler = eulerDeg(localRot);
                    outLocalRx = unwrapDeg(euler.x, startCmpRx);
                    outLocalRy = unwrapDeg(euler.y, startCmpRy);
                    outLocalRz = unwrapDeg(euler.z, startCmpRz);
                }

                float startCmpX = startX;
                float startCmpY = startY;
                float startCmpZ = startZ;

                float outLocalSx = canScale ? size.x : startSx;
                float outLocalSy = canScale ? size.y : startSy;
                float outLocalSz = canScale ? size.z : startSz;
                if (inherit && parentId > 0L) {
                    Pose parent = worldPose(state, parentId, poseCache);
                    outLocalSx = outLocalSx / (parent.scale.x == 0.0f ? 1.0f : parent.scale.x);
                    outLocalSy = outLocalSy / (parent.scale.y == 0.0f ? 1.0f : parent.scale.y);
                    outLocalSz = outLocalSz / (parent.scale.z == 0.0f ? 1.0f : parent.scale.z);
                }

                boolean unchanged = (!canMove || (Math.abs(outLocalX - startCmpX) < 1e-6f
                        && Math.abs(outLocalY - startCmpY) < 1e-6f
                        && Math.abs(outLocalZ - startCmpZ) < 1e-6f))
                        && (!canScale || (Math.abs(outLocalSx - startSx) < SCALE_EPS
                        && Math.abs(outLocalSy - startSy) < SCALE_EPS
                        && Math.abs(outLocalSz - startSz) < SCALE_EPS))
                        && (!hasProp(def, props, "rx") || Math.abs(outLocalRx - startCmpRx) < DEG_EPS)
                        && (!hasProp(def, props, "ry") || Math.abs(outLocalRy - startCmpRy) < DEG_EPS)
                        && (!hasProp(def, props, "rz") || Math.abs(outLocalRz - startCmpRz) < DEG_EPS);
                if (unchanged && !forceSend) {
                    return;
                }

                sendNodeTransformWithScale(sel.nodeId(), session, state, def, props,
                        outLocalX, outLocalY, outLocalZ,
                        outLocalRx, outLocalRy, outLocalRz,
                        outLocalSx, outLocalSy, outLocalSz, canScale);
            }
            recordDragUndoOnRelease(sel.nodeId());
        }
    }

    private void recordDragUndoOnRelease(long nodeId) {
        if (dragStartProps == null || dragStartNodeId != nodeId) {
            dragStartProps = null;
            dragStartNodeId = 0L;
            return;
        }
        List<SceneOp> undoOps = new ArrayList<>();
        for (String k : new String[]{"x", "y", "z", "rx", "ry", "rz", "sx", "sy", "sz"}) {
            String oldVal = dragStartProps.get(k);
            if (oldVal != null) {
                undoOps.add(new SceneOp.SetProperty(nodeId, k, oldVal));
            }
        }
        if (!undoOps.isEmpty()) {
            runtime.history().push(undoOps, List.of());
        }
        dragStartProps = null;
        dragStartNodeId = 0L;
    }

    private boolean sendOpsThrottled(Session session, EditorState state, long nodeId, List<SceneOp> ops) {
        if (session == null || state == null || ops == null || ops.isEmpty()) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (lastDragSendNodeId != nodeId) {
            lastDragSendNodeId = nodeId;
            lastDragSendAtMs = 0L;
        }
        if (lastDragSendAtMs != 0L && (now - lastDragSendAtMs) < DRAG_SEND_INTERVAL_MS) {
            return false;
        }
        lastDragSendAtMs = now;
        session.send(Lane.EVENTS, new SceneOpBatch(state.nextBatchId++, false, List.copyOf(ops)));
        return true;
    }

    private static ArrayList<SceneOp> buildNodeTransformOps(long nodeId,
                                                            NodeTypeDef def,
                                                            Map<String, String> props,
                                                            float x,
                                                            float y,
                                                            float z,
                                                            float rxDeg,
                                                            float ryDeg,
                                                            float rzDeg) {
        ArrayList<SceneOp> ops = new ArrayList<>(6);
        maybeAddFloatOp(ops, nodeId, "x", x, def, props, "0", 1e-6f);
        maybeAddFloatOp(ops, nodeId, "y", y, def, props, "0", 1e-6f);
        maybeAddFloatOp(ops, nodeId, "z", z, def, props, "0", 1e-6f);
        maybeAddFloatOp(ops, nodeId, "rx", rxDeg, def, props, "0", DEG_EPS);
        maybeAddFloatOp(ops, nodeId, "ry", ryDeg, def, props, "0", DEG_EPS);
        maybeAddFloatOp(ops, nodeId, "rz", rzDeg, def, props, "0", DEG_EPS);
        return ops;
    }

    private static ArrayList<SceneOp> buildBoxTransformOps(long nodeId,
                                                           NodeTypeDef def,
                                                           Map<String, String> props,
                                                           float x,
                                                           float y,
                                                           float z,
                                                           float sx,
                                                           float sy,
                                                           float sz,
                                                           float rxDeg,
                                                           float ryDeg,
                                                           float rzDeg) {
        ArrayList<SceneOp> ops = new ArrayList<>(9);
        maybeAddFloatOp(ops, nodeId, "x", x, def, props, "0", 1e-6f);
        maybeAddFloatOp(ops, nodeId, "y", y, def, props, "0", 1e-6f);
        maybeAddFloatOp(ops, nodeId, "z", z, def, props, "0", 1e-6f);
        maybeAddFloatOp(ops, nodeId, "sx", Math.max(1.0f, sx), def, props, "1", 1e-6f);
        maybeAddFloatOp(ops, nodeId, "sy", Math.max(1.0f, sy), def, props, "1", 1e-6f);
        maybeAddFloatOp(ops, nodeId, "sz", Math.max(1.0f, sz), def, props, "1", 1e-6f);
        maybeAddFloatOp(ops, nodeId, "rx", rxDeg, def, props, "0", DEG_EPS);
        maybeAddFloatOp(ops, nodeId, "ry", ryDeg, def, props, "0", DEG_EPS);
        maybeAddFloatOp(ops, nodeId, "rz", rzDeg, def, props, "0", DEG_EPS);
        return ops;
    }

    private static void maybeAddFloatOp(ArrayList<SceneOp> ops,
                                        long nodeId,
                                        String key,
                                        float next,
                                        NodeTypeDef def,
                                        Map<String, String> props,
                                        String fallbackDefault,
                                        float eps) {
        if (ops == null || key == null || key.isBlank()) {
            return;
        }
        if (!hasProp(def, props, key)) {
            return;
        }
        float current = parseFloat(props != null ? props.get(key) : null, defaultFor(def, key, fallbackDefault));
        if (Float.isFinite(next) && Float.isFinite(current) && Math.abs(next - current) < eps) {
            return;
        }
        ops.add(new SceneOp.SetProperty(nodeId, key, formatFloat(next)));
    }

    private void sendNodeTransform(long nodeId,
                                   Session session,
                                   EditorState state,
                                   NodeTypeDef def,
                                   Map<String, String> props,
                                   float x,
                                   float y,
                                   float z,
                                   float rxDeg,
                                   float ryDeg,
                                   float rzDeg) {
        ArrayList<SceneOp> ops = new ArrayList<>(6);
        if (hasProp(def, props, "x")) {
            ops.add(new SceneOp.SetProperty(nodeId, "x", formatFloat(x)));
        }
        if (hasProp(def, props, "y")) {
            ops.add(new SceneOp.SetProperty(nodeId, "y", formatFloat(y)));
        }
        if (hasProp(def, props, "z")) {
            ops.add(new SceneOp.SetProperty(nodeId, "z", formatFloat(z)));
        }
        if (hasProp(def, props, "rx")) {
            ops.add(new SceneOp.SetProperty(nodeId, "rx", formatFloat(rxDeg)));
        }
        if (hasProp(def, props, "ry")) {
            ops.add(new SceneOp.SetProperty(nodeId, "ry", formatFloat(ryDeg)));
        }
        if (hasProp(def, props, "rz")) {
            ops.add(new SceneOp.SetProperty(nodeId, "rz", formatFloat(rzDeg)));
        }
        if (ops.isEmpty()) {
            return;
        }
        runtime.net().sendOps(session, state, List.copyOf(ops));
    }

    private void sendNodeTransformWithScale(long nodeId,
                                            Session session,
                                            EditorState state,
                                            NodeTypeDef def,
                                            Map<String, String> props,
                                            float x, float y, float z,
                                            float rxDeg, float ryDeg, float rzDeg,
                                            float sx, float sy, float sz,
                                            boolean includeScale) {
        ArrayList<SceneOp> ops = new ArrayList<>(9);
        if (hasProp(def, props, "x")) ops.add(new SceneOp.SetProperty(nodeId, "x", formatFloat(x)));
        if (hasProp(def, props, "y")) ops.add(new SceneOp.SetProperty(nodeId, "y", formatFloat(y)));
        if (hasProp(def, props, "z")) ops.add(new SceneOp.SetProperty(nodeId, "z", formatFloat(z)));
        if (hasProp(def, props, "rx")) ops.add(new SceneOp.SetProperty(nodeId, "rx", formatFloat(rxDeg)));
        if (hasProp(def, props, "ry")) ops.add(new SceneOp.SetProperty(nodeId, "ry", formatFloat(ryDeg)));
        if (hasProp(def, props, "rz")) ops.add(new SceneOp.SetProperty(nodeId, "rz", formatFloat(rzDeg)));
        if (includeScale) {
            if (hasProp(def, props, "sx")) ops.add(new SceneOp.SetProperty(nodeId, "sx", formatFloat(sx)));
            if (hasProp(def, props, "sy")) ops.add(new SceneOp.SetProperty(nodeId, "sy", formatFloat(sy)));
            if (hasProp(def, props, "sz")) ops.add(new SceneOp.SetProperty(nodeId, "sz", formatFloat(sz)));
        }
        if (ops.isEmpty()) return;
        runtime.net().sendOps(session, state, List.copyOf(ops));
    }

    private void renderCameraFrustums(Ui ui,
                                     UiRenderer r,
                                     EditorState state,
                                     int viewportX,
                                     int viewportY,
                                     int viewportW,
                                     int viewportH) {
        if (ui == null || r == null || state == null) {
            return;
        }
        if (state.scene == null || state.scene.nodes().isEmpty()) {
            return;
        }

        HashMap<Long, Pose> poseCache = new HashMap<>();

        SceneSnapshot.NodeSnapshot selected = state.scene.getNode(state.selectedId);
        boolean focusSet = false;
        if (selected != null && "Camera3D".equals(selected.type())) {
            if (tryReadNodePos(state, selected, focusWorld, poseCache)) {
                focusSet = true;
            }
        }
        if (!focusSet) {
            for (SceneSnapshot.NodeSnapshot node : state.scene.nodes()) {
                if (node != null && "Camera3D".equals(node.type())) {
                    if (tryReadNodePos(state, node, focusWorld, poseCache)) {
                        focusSet = true;
                    }
                    break;
                }
            }
        }

        float aspect = viewportW / (float) Math.max(1, viewportH);
        Matrix4f viewProj = MinecraftRenderBridge.viewProjection(DEFAULT_FOV_DEG, aspect, focusSet ? focusWorld : null);
        Vector3f cameraPos = MinecraftRenderBridge.cameraPos();
        if (viewProj == null || cameraPos == null) {
            return;
        }

        Theme theme = ui.theme();
        int colInactive = 0xFF00FFFF; // Bright cyan for visibility
        int colActive = 0xFFFFFF00; // Bright yellow for active camera

        r.pushClipRect(viewportX, viewportY, viewportW, viewportH);
        try {
            for (SceneSnapshot.NodeSnapshot node : state.scene.nodes()) {
                if (node == null || !"Camera3D".equals(node.type())) {
                    continue;
                }

                boolean active = node.nodeId() == state.selectedId;
                int thickness = active ? 3 : 2;
                int color = active ? colActive : colInactive;

                if (!buildCameraFrustumWorld(state, node, aspect, poseCache)) {
                    continue;
                }
                for (int i = 0; i < frustumWorld.length; i++) {
                    Vector3f p = frustumWorld[i];
                    frustumOk[i] = projectToScreen(viewProj, p.x, p.y, p.z, viewportX, viewportY, viewportW, viewportH, i);
                }

                drawFrustumLines(r, thickness, color);
                drawCameraOrigin(r, viewProj, node, state, poseCache, viewportX, viewportY, viewportW, viewportH, thickness, color);
            }
        } finally {
            r.popClipRect();
        }
    }

    private void drawFrustumLines(UiRenderer r, int thickness, int color) {
        // Near plane
        drawEdge(r, 0, 1, thickness, color);
        drawEdge(r, 1, 2, thickness, color);
        drawEdge(r, 2, 3, thickness, color);
        drawEdge(r, 3, 0, thickness, color);

        // Far plane
        drawEdge(r, 4, 5, thickness, color);
        drawEdge(r, 5, 6, thickness, color);
        drawEdge(r, 6, 7, thickness, color);
        drawEdge(r, 7, 4, thickness, color);

        // Sides
        drawEdge(r, 0, 4, thickness, color);
        drawEdge(r, 1, 5, thickness, color);
        drawEdge(r, 2, 6, thickness, color);
        drawEdge(r, 3, 7, thickness, color);
    }

    private void drawEdge(UiRenderer r, int a, int b, int thickness, int color) {
        if (!frustumOk[a] || !frustumOk[b]) {
            return;
        }
        r.drawLine(frustumX[a], frustumY[a], frustumX[b], frustumY[b], thickness, color);
    }

    private void drawCameraOrigin(UiRenderer r,
                                  Matrix4f viewProj,
                                  SceneSnapshot.NodeSnapshot node,
                                  EditorState state,
                                  Map<Long, Pose> poseCache,
                                  int viewportX,
                                  int viewportY,
                                  int viewportW,
                                  int viewportH,
                                  int thickness,
                                  int color) {
        if (node == null || state == null) {
            return;
        }
        if (!tryReadNodePos(state, node, tmpWorld, poseCache)) {
            return;
        }
        int idx = 0;
        if (!projectToScreen(viewProj, tmpWorld.x, tmpWorld.y, tmpWorld.z, viewportX, viewportY, viewportW, viewportH, idx)) {
            return;
        }
        int x = frustumX[idx];
        int y = frustumY[idx];
        int half = 4 + thickness;
        r.drawLine(x - half, y, x + half, y, thickness, color);
        r.drawLine(x, y - half, x, y + half, thickness, color);
    }

    private boolean projectToScreen(Matrix4f viewProj,
                                    float worldX,
                                    float worldY,
                                    float worldZ,
                                    int viewportX,
                                    int viewportY,
                                    int viewportW,
                                    int viewportH,
                                    int idx) {
        if (viewProj == null) {
            return false;
        }
        clip.set(worldX, worldY, worldZ, 1.0f).mul(viewProj);
        float w = clip.w;
        if (!(w > 1e-6f) || !Float.isFinite(w)) {
            return false;
        }

        float ndcX = clip.x / w;
        float ndcY = clip.y / w;
        float ndcZ = clip.z / w;
        if (!Float.isFinite(ndcX) || !Float.isFinite(ndcY) || !Float.isFinite(ndcZ)) {
            return false;
        }

        float sx = viewportX + (ndcX * 0.5f + 0.5f) * viewportW;
        float sy = viewportY + (1.0f - (ndcY * 0.5f + 0.5f)) * viewportH;
        if (!Float.isFinite(sx) || !Float.isFinite(sy)) {
            return false;
        }

        frustumX[idx] = Math.round(sx);
        frustumY[idx] = Math.round(sy);
        return true;
    }

    private boolean buildCameraFrustumWorld(EditorState state, SceneSnapshot.NodeSnapshot node, float aspect, Map<Long, Pose> poseCache) {
        if (state == null || node == null) {
            return false;
        }
        NodeTypeDef def = state.typesById.get("Camera3D");
        Map<String, String> props = toPropertyMap(node.properties());

        Pose world = worldPose(state, node.nodeId(), poseCache != null ? poseCache : new HashMap<>());
        float x = world.pos.x;
        float y = world.pos.y;
        float z = world.pos.z;
        Vector3f euler = eulerDeg(world.rot);
        float rx = euler.x;
        float ry = euler.y;
        float rz = euler.z;

        float fov = parseFloat(props.get("fov"), defaultFor(def, "fov", "70"));
        float nearDist = parseFloat(props.get("near"), defaultFor(def, "near", "0.05"));
        float farDist = parseFloat(props.get("far"), defaultFor(def, "far", "1000"));

        if (!Float.isFinite(fov)) {
            fov = 70.0f;
        }
        fov = Math.max(1.0f, Math.min(179.0f, fov));
        if (!Float.isFinite(nearDist) || nearDist <= 0.0f) {
            nearDist = 0.05f;
        }
        if (!Float.isFinite(farDist) || farDist <= 0.0f) {
            farDist = 10.0f;
        }
        float minGizmoFar = Math.max(nearDist + 0.05f, 0.25f);
        float maxGizmoFar = Math.max(2.0f, minGizmoFar);
        float gizmoFar = Math.min(Math.max(farDist, minGizmoFar), maxGizmoFar);

        float halfFovRad = (float) Math.toRadians(fov * 0.5f);
        float t = (float) Math.tan(halfFovRad);
        if (!Float.isFinite(t) || t <= 0.0f) {
            return false;
        }

        float nearH = nearDist * t;
        float nearW = nearH * Math.max(0.01f, aspect);
        float farH = gizmoFar * t;
        float farW = farH * Math.max(0.01f, aspect);

        cameraRot.identity().set(world.rot);

        // Near plane
        setFrustumPoint(0, -nearW, nearH, nearDist, x, y, z);
        setFrustumPoint(1, nearW, nearH, nearDist, x, y, z);
        setFrustumPoint(2, nearW, -nearH, nearDist, x, y, z);
        setFrustumPoint(3, -nearW, -nearH, nearDist, x, y, z);

        // Far plane
        setFrustumPoint(4, -farW, farH, gizmoFar, x, y, z);
        setFrustumPoint(5, farW, farH, gizmoFar, x, y, z);
        setFrustumPoint(6, farW, -farH, gizmoFar, x, y, z);
        setFrustumPoint(7, -farW, -farH, gizmoFar, x, y, z);

        return true;
    }

    private void setFrustumPoint(int idx, float localX, float localY, float localZ, float worldX, float worldY, float worldZ) {
        Vector3f p = frustumWorld[idx];
        p.set(localX, localY, localZ);
        cameraRot.transform(p);
        p.add(worldX, worldY, worldZ);
    }

    private static boolean tryReadNodePos(EditorState state, SceneSnapshot.NodeSnapshot node, Vector3f out, Map<Long, Pose> poseCache) {
        if (state == null || node == null || out == null) {
            return false;
        }
        Pose world = worldPose(state, node.nodeId(), poseCache != null ? poseCache : new HashMap<>());
        out.set(world.pos);
        return true;
    }

    private static Pose worldPose(EditorState state, long nodeId, Map<Long, Pose> cache) {
        if (state == null || state.scene == null || nodeId <= 0L) {
            return Pose.IDENTITY;
        }
        if (cache != null) {
            Pose cached = cache.get(nodeId);
            if (cached != null) {
                return cached;
            }
        }

        SceneSnapshot.NodeSnapshot node = state.scene.getNode(nodeId);
        if (node == null) {
            return Pose.IDENTITY;
        }

        Pose local = localPose(state, node);
        boolean inherit = shouldInheritTransform(local.inherit);
        Pose world;
        if (inherit && node.parentId() > 0L) {
            Pose parent = worldPose(state, node.parentId(), cache);
            world = Pose.compose(parent, local);
        } else {
            world = local.toWorld();
        }

        if (cache != null) {
            cache.put(nodeId, world);
        }
        return world;
    }

    private static Pose localPose(EditorState state, SceneSnapshot.NodeSnapshot node) {
        NodeTypeDef def = state != null ? state.typesById.get(node.type()) : null;
        Map<String, String> props = toPropertyMap(node.properties());

        float x = parseFloat(props.get("x"), defaultFor(def, "x", "0"));
        float y = parseFloat(props.get("y"), defaultFor(def, "y", "0"));
        float z = parseFloat(props.get("z"), defaultFor(def, "z", "0"));

        float rx = parseFloat(props.get("rx"), defaultFor(def, "rx", "0"));
        float ry = parseFloat(props.get("ry"), defaultFor(def, "ry", "0"));
        float rz = parseFloat(props.get("rz"), defaultFor(def, "rz", "0"));

        float sx = Math.max(SCALE_EPS, parseFloat(props.get("sx"), defaultFor(def, "sx", "1")));
        float sy = Math.max(SCALE_EPS, parseFloat(props.get("sy"), defaultFor(def, "sy", "1")));
        float sz = Math.max(SCALE_EPS, parseFloat(props.get("sz"), defaultFor(def, "sz", "1")));

        String inherit = props.get("@inherit_transform");
        boolean pivotIsMinCorner = "CSGBlock".equals(node.type()) || "CSGBox".equals(node.type());
        boolean hasScale = hasProp(def, props, "sx") || hasProp(def, props, "sy") || hasProp(def, props, "sz");

        float px = x;
        float py = y;
        float pz = z;
        if (pivotIsMinCorner && hasScale) {
            px = x + sx * 0.5f;
            py = y + sy * 0.5f;
            pz = z + sz * 0.5f;
        }

        Pose pose = new Pose();
        pose.pos.set(px, py, pz);
        pose.rot.set(quatFromEulerDeg(rx, ry, rz));
        pose.scale.set(sx, sy, sz);
        pose.inherit = "CSGBlock".equals(node.type()) ? "false" : inherit;
        return pose;
    }

    private static boolean shouldInheritTransform(String v) {
        if (v == null || v.isBlank()) {
            return true;
        }
        String s = v.trim().toLowerCase();
        return !("false".equals(s) || "0".equals(s));
    }

    private static boolean parseBool(String v, boolean fallback) {
        if (v == null) {
            return fallback;
        }
        String s = v.trim().toLowerCase();
        if (s.isEmpty()) {
            return fallback;
        }
        if ("true".equals(s) || "1".equals(s) || "t".equals(s) || "yes".equals(s) || "y".equals(s)) {
            return true;
        }
        if ("false".equals(s) || "0".equals(s) || "f".equals(s) || "no".equals(s) || "n".equals(s)) {
            return false;
        }
        return fallback;
    }

    private static Quaternionf quatFromEulerDeg(float rxDeg, float ryDeg, float rzDeg) {
        float rx = (float) Math.toRadians(Float.isFinite(rxDeg) ? rxDeg : 0.0f);
        float ry = (float) Math.toRadians(Float.isFinite(ryDeg) ? ryDeg : 0.0f);
        float rz = (float) Math.toRadians(Float.isFinite(rzDeg) ? rzDeg : 0.0f);
        return new Quaternionf().rotationXYZ(rx, ry, rz);
    }

    private static Vector3f eulerDeg(Quaternionf q) {
        if (q == null) {
            return new Vector3f();
        }
        Vector3f eulerRad = new Vector3f();
        new Quaternionf(q).normalize().getEulerAnglesXYZ(eulerRad);
        return eulerRad.mul((float) (180.0 / Math.PI));
    }

    private static final class Pose {
        static final Pose IDENTITY = new Pose(true);

        final Vector3f pos = new Vector3f();
        final Quaternionf rot = new Quaternionf();
        final Vector3f scale = new Vector3f(1, 1, 1);
        String inherit;

        Pose() {
        }

        Pose(boolean identity) {
            if (identity) {
                pos.set(0, 0, 0);
                rot.identity();
                scale.set(1, 1, 1);
                inherit = null;
            }
        }

        Pose toWorld() {
            Pose out = new Pose();
            out.pos.set(pos);
            out.rot.set(rot);
            out.scale.set(scale);
            out.inherit = inherit;
            return out;
        }

        static Pose compose(Pose parent, Pose child) {
            if (parent == null) {
                return child.toWorld();
            }
            Pose out = new Pose();

            Vector3f scaled = new Vector3f(child.pos).mul(parent.scale);
            parent.rot.transform(scaled);
            out.pos.set(parent.pos).add(scaled);

            out.rot.set(parent.rot).mul(child.rot).normalize();
            out.scale.set(parent.scale).mul(child.scale);
            out.inherit = child.inherit;
            return out;
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

    private void sendBoxTransform(long nodeId,
                                  Session session,
                                  EditorState state,
                                  float x,
                                  float y,
                                  float z,
                                  float sx,
                                  float sy,
                                  float sz,
                                  float rxDeg,
                                  float ryDeg,
                                  float rzDeg) {
        ArrayList<SceneOp> ops = new ArrayList<>(9);
        ops.add(new SceneOp.SetProperty(nodeId, "x", formatFloat(x)));
        ops.add(new SceneOp.SetProperty(nodeId, "y", formatFloat(y)));
        ops.add(new SceneOp.SetProperty(nodeId, "z", formatFloat(z)));
        ops.add(new SceneOp.SetProperty(nodeId, "sx", formatFloat(Math.max(1.0f, sx))));
        ops.add(new SceneOp.SetProperty(nodeId, "sy", formatFloat(Math.max(1.0f, sy))));
        ops.add(new SceneOp.SetProperty(nodeId, "sz", formatFloat(Math.max(1.0f, sz))));
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

    private static float snapToStep(float value, float step) {
        if (!Float.isFinite(value) || !Float.isFinite(step) || step <= 0.0f) {
            return value;
        }
        return Math.round(value / step) * step;
    }

    private static float unwrapDeg(float deg, float referenceDeg) {
        if (!Float.isFinite(deg) || !Float.isFinite(referenceDeg)) {
            return deg;
        }
        float a = deg;
        float diff = a - referenceDeg;
        while (diff > 180.0f) {
            a -= 360.0f;
            diff = a - referenceDeg;
        }
        while (diff < -180.0f) {
            a += 360.0f;
            diff = a - referenceDeg;
        }
        return a;
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

    private static boolean hasProp(NodeTypeDef def, Map<String, String> props, String key) {
        if (key == null) {
            return false;
        }
        if (def != null && def.properties() != null && def.properties().containsKey(key)) {
            return true;
        }
        return props != null && props.containsKey(key);
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
