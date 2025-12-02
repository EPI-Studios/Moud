package com.moud.client.editor.ui;

import com.moud.client.animation.ClientPlayerModelManager;
import com.moud.client.animation.PlayerPartConfigManager;
import com.moud.client.editor.EditorModeManager;
import com.moud.client.editor.assets.EditorAssetCatalog;
import com.moud.client.editor.camera.EditorCameraController;
import com.moud.client.editor.runtime.Capsule;
import com.moud.client.editor.runtime.RuntimeObject;
import com.moud.client.editor.runtime.RuntimeObjectRegistry;
import com.moud.client.editor.runtime.RuntimeObjectType;
import com.moud.client.editor.scene.blueprint.*;
import com.moud.client.editor.selection.SceneSelectionManager;
import com.moud.client.editor.picking.RaycastPicker;
import com.moud.client.editor.scene.SceneEditorDiagnostics;
import com.moud.client.editor.scene.SceneHistoryManager;
import com.moud.client.editor.scene.SceneObject;
import com.moud.client.editor.scene.SceneSessionManager;
import com.moud.client.model.ClientModelManager;
import com.moud.client.model.RenderableModel;
import com.moud.client.editor.ui.panel.AssetBrowserPanel;
import com.moud.client.editor.ui.panel.AnimationTimelinePanel;
import com.moud.client.editor.ui.panel.BlueprintToolsPanel;
import com.moud.client.editor.ui.panel.DiagnosticsPanel;
import com.moud.client.editor.ui.panel.ExplorerPanel;
import com.moud.client.editor.ui.panel.InspectorPanel;
import com.moud.client.editor.ui.panel.RibbonBar;
import com.moud.client.editor.ui.panel.ScriptViewerPanel;
import com.moud.client.editor.ui.panel.ViewportToolbar;
import com.moud.network.MoudPackets.AnimationLoadResponsePacket;
import com.moud.network.MoudPackets.AnimationListResponsePacket;
import com.moud.client.network.ClientPacketWrapper;
import com.moud.network.MoudPackets;
import com.moud.network.MoudPackets;
import imgui.ImGui;
import imgui.extension.imguizmo.ImGuizmo;
import imgui.extension.imguizmo.flag.Mode;
import imgui.extension.imguizmo.flag.Operation;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImFloat;
import imgui.type.ImString;
import com.moud.client.editor.ui.WorldViewCapture;
import com.moud.client.editor.ui.layout.EditorDockingLayout;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import com.zigythebird.playeranim.animation.PlayerAnimResources;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SceneEditorOverlay {
    private static final Logger LOGGER = LoggerFactory.getLogger(SceneEditorOverlay.class);
    private static final SceneEditorOverlay INSTANCE = new SceneEditorOverlay();
    private static final long MAX_BLOCK_CAPTURE_BLOCKS = 10000;

    private final ImString markerNameBuffer = new ImString("Marker", 64);
    private final ImString fakePlayerLabelBuffer = new ImString("Fake Player", 64);
    private final ImString fakePlayerSkinBuffer = new ImString("", 256);
    private final ImBoolean fakePlayerPhysics = new ImBoolean(false);
    private final ImBoolean fakePlayerSneak = new ImBoolean(false);
    private final ImBoolean fakePlayerSprint = new ImBoolean(false);
    private final ImBoolean fakePlayerSwing = new ImBoolean(false);
    private final ImBoolean fakePlayerUse = new ImBoolean(false);
    private final ImBoolean fakePlayerPathLoop = new ImBoolean(false);
    private final ImBoolean fakePlayerPathPingPong = new ImBoolean(false);
    private final float[] fakePlayerDimensions = new float[]{0.6f, 1.8f};
    private final float[] fakePlayerPathSpeed = new float[]{0f};
    private final List<float[]> fakePlayerPathPoints = new ArrayList<>();
    private final ImString cameraLabelBuffer = new ImString("Camera", 64);
    private String selectedLimbType;
    private final float[] cameraPositionBuffer = new float[]{0f, 65f, 0f};
    private final float[] cameraRotationBuffer = new float[]{0f, 0f, 0f};
    private final ImFloat cameraFov = new ImFloat(70f);
    private final ImFloat cameraNear = new ImFloat(0.1f);
    private final ImFloat cameraFar = new ImFloat(128f);
    private final float[] newObjectPosition = new float[]{0f, 65f, 0f};
    private final float[] newObjectScale = new float[]{1f, 1f, 1f};
    private String selectedSceneObjectId;
    private String selectedRuntimeId;
    private final float[] activeMatrix = identity();
    private final float[] activeTranslation = new float[3];
    private final float[] activeRotation = new float[3];
    private final float[] activeScale = new float[]{1f, 1f, 1f};
    private final float[] activeRotationQuat = new float[]{0f, 0f, 0f, 1f};
    private final SceneHistoryManager history = SceneHistoryManager.getInstance();

    private int currentOperation = Operation.TRANSLATE;
    private int gizmoMode = Mode.LOCAL;
    private boolean useSnap = false;
    private final float[] snapValues = new float[]{0.5f, 0.5f, 0.5f};
    private boolean gizmoManipulating;
    private String gizmoObjectId;
    private final EditorDockingLayout dockingLayout = new EditorDockingLayout();

    public static final String PAYLOAD_HIERARCHY = "MoudHierarchyObject";
    public static final String PAYLOAD_ASSET = "MoudAssetDefinition";
    public static final String PAYLOAD_PROJECT_FILE = "MoudProjectFile";
    public static final String[] DISPLAY_CONTENT_TYPES = {"image", "video", "sequence"};
    public static final String[] LIGHT_TYPE_LABELS = {"Point", "Area"};
    private static final String DEFAULT_FAKE_PLAYER_SKIN = "https://textures.minecraft.net/texture/45c338913be11c119f0e90a962f8d833b0dff78eaefdd8f2fa2a3434a1f2af0";
    private static final int PANEL_WINDOW_FLAGS = ImGuiWindowFlags.NoCollapse | ImGuiWindowFlags.NoMove | ImGuiWindowFlags.NoResize;
    private final ExplorerPanel explorerPanel = new ExplorerPanel(this);
    private final InspectorPanel inspectorPanel = new InspectorPanel(this);
    private final ScriptViewerPanel scriptViewerPanel = new ScriptViewerPanel(this);
    private final AnimationTimelinePanel animationTimelinePanel = new AnimationTimelinePanel(this);
    private final com.moud.client.editor.ui.panel.EventTrackEditor eventTrackEditor = new com.moud.client.editor.ui.panel.EventTrackEditor();
    private final AssetBrowserPanel assetBrowserPanel = new AssetBrowserPanel(this);
    private final DiagnosticsPanel diagnosticsPanel = new DiagnosticsPanel(this);
    private final ViewportToolbar viewportToolbar = new ViewportToolbar(this);
    private final RibbonBar ribbonBar = new RibbonBar(this);
    private final BlueprintToolsPanel blueprintToolsPanel = new BlueprintToolsPanel(this);
    private final ImString modelPopupFilter = new ImString(64);
    private final ImString displayPopupFilter = new ImString(64);
    private final ImString lightPopupFilter = new ImString(64);
    private final ImBoolean markerSnapToggle = new ImBoolean(false);
    private final ImFloat markerSnapValue = new ImFloat(0.5f);
    private volatile String[] cachedPlayerAnimations = new String[0];
    private volatile long lastAnimationFetch;

    private Map<String, List<SceneObject>> cachedHierarchy = new ConcurrentHashMap<>();
    private long lastHierarchyBuild = 0;
    private static final long HIERARCHY_CACHE_MS = 100;

    private boolean showExplorer = true;
    private boolean showInspectorPanel = true;
    private boolean showAssetBrowser = true;
    private boolean showScriptViewer = true;
    private boolean showDiagnostics = true;
    private boolean showGizmoToolbar = true;
    private boolean showSelectionBounds = true;

    private final float[] regionCornerA = new float[3];
    private final float[] regionCornerB = new float[3];
    private boolean regionASet;
    private boolean regionBSet;
    private final ImString blueprintNameBuffer = new ImString("new_blueprint", 128);
    private final ImString blueprintPreviewName = new ImString("", 128);
    private final float[] previewPosition = new float[]{0f, 64f, 0f};
    private final float[] previewRotation = new float[]{0f, 0f, 0f};
    private final float[] previewScale = new float[]{1f, 1f, 1f};
    private final float[] previewMatrix = identity();
    private boolean previewGizmoActive = false;
    private int previewGizmoOperation = Operation.TRANSLATE;
    private int markerCounter = 1;

    private SceneEditorOverlay() {
        fakePlayerSkinBuffer.set(DEFAULT_FAKE_PLAYER_SKIN);
    }

    public static SceneEditorOverlay getInstance() {
        return INSTANCE;
    }

    public void render() {
        if (!EditorModeManager.getInstance().isActive()) {
            return;
        }
        SceneSessionManager session = SceneSessionManager.getInstance();
        EditorAssetCatalog.getInstance().requestAssetsIfNeeded();
        float displayWidth = ImGui.getIO().getDisplaySizeX();
        float displayHeight = ImGui.getIO().getDisplaySizeY();

        dockingLayout.begin(displayWidth, displayHeight);

        renderRibbonWindow(session);
        if (showExplorer) {
            explorerPanel.render(session);
        }
        if (showInspectorPanel) {
            inspectorPanel.render(session);
        }
        if (showScriptViewer) {
            scriptViewerPanel.render(session);
        }
        if (showAssetBrowser) {
            assetBrowserPanel.render();
        }
        if (showDiagnostics) {
            showDiagnostics = diagnosticsPanel.render(showDiagnostics);
        }
        if (showGizmoToolbar) {
            viewportToolbar.render(dockingLayout.getViewportRect());
        }
        renderAssetPopups(session);
        blueprintToolsPanel.render(session);
    }

    public void openAnimation(String projectPath) {
        if (projectPath == null || projectPath.isBlank()) {
            return;
        }
        SceneEditorDiagnostics.log("Requesting animation: " + projectPath);
        ClientPacketWrapper.sendToServer(new MoudPackets.AnimationLoadPacket(projectPath));
    }

    public void handleAnimationLoadResponse(AnimationLoadResponsePacket packet) {
        if (packet == null) {
            return;
        }
        if (!packet.success()) {
            SceneEditorDiagnostics.log("Animation load failed: " + packet.error());
            return;
        }
        animationTimelinePanel.setCurrentClip(packet.clip(), packet.projectPath());
        SceneEditorDiagnostics.log("Loaded animation '" + packet.projectPath() + "'");
    }

    public void handleAnimationListResponse(AnimationListResponsePacket packet) {
        if (packet == null || packet.animations() == null) {
            return;
        }
        animationTimelinePanel.setAvailableAnimations(packet.animations());
        SceneEditorDiagnostics.log("Received " + packet.animations().size() + " animations from server.");
    }

    public void requestAnimationList() {
        ClientPacketWrapper.sendToServer(new MoudPackets.AnimationListPacket());
    }

    public void saveAnimation(String projectPath, com.moud.api.animation.AnimationClip clip) {
        if (projectPath == null || projectPath.isBlank() || clip == null) {
            SceneEditorDiagnostics.log("Cannot save animation: missing path or clip.");
            return;
        }
        ClientPacketWrapper.sendToServer(new MoudPackets.AnimationSavePacket(
                clip.id(),
                projectPath,
                clip
        ));
        SceneEditorDiagnostics.log("Save requested for animation: " + projectPath);
    }

    public void playAnimation(String animationId, boolean loop, float speed) {
        ClientPacketWrapper.sendToServer(new MoudPackets.AnimationPlayPacket(animationId, loop, speed));
    }

    public void stopAnimation(String animationId) {
        ClientPacketWrapper.sendToServer(new MoudPackets.AnimationStopPacket(animationId));
    }

    private String lastSeekId = null;
    private float lastSeekTime = Float.NaN;
    private long lastSeekSentMs = 0L;

    public void seekAnimation(String animationId, float time) {
        if (animationId == null) return;
        if (animationId.equals(lastSeekId) && Math.abs(time - lastSeekTime) < 1e-4f) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastSeekSentMs < 30) { // throttle to ~33 FPS to avoid spam
            return;
        }
        lastSeekSentMs = now;
        lastSeekId = animationId;
        lastSeekTime = time;
        ClientPacketWrapper.sendToServer(new MoudPackets.AnimationSeekPacket(animationId, time));
    }

    private void renderRibbonWindow(SceneSessionManager session) {
        dockingLayout.apply(EditorDockingLayout.Region.RIBBON);
        if (!ImGui.begin("Ribbon", PANEL_WINDOW_FLAGS | ImGuiWindowFlags.NoTitleBar | ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse)) {
            ImGui.end();
            return;
        }
        ribbonBar.render(session);
        ImGui.end();
    }

    private void renderAssetPopups(SceneSessionManager session) {
        renderAssetPopup("hierarchy_add_model_popup", "model", modelPopupFilter, null);
        renderAssetPopup("hierarchy_add_display_popup", "display", displayPopupFilter, null);
        renderAssetPopup("hierarchy_add_light_popup", "light", lightPopupFilter, null);
        renderMarkerPopup("hierarchy_add_marker_popup", session, null);
        renderFakePlayerPopup("hierarchy_add_fake_player_popup", session, null);
        renderCameraPopup("hierarchy_add_camera_popup", session, null);
    }

    public void renderWorldGizmo(Matrix4f viewMatrix, Matrix4f projectionMatrix) {
        if (!EditorModeManager.getInstance().isActive()) {
            return;
        }
        SceneSessionManager session = SceneSessionManager.getInstance();
        SceneObject selected = getSelected(session);
        RuntimeObject selectedRuntime = getSelectedRuntime();

        if (selected == null && selectedRuntime == null) {
            finalizeGizmoChange();
            return;
        }

        String currentObjectId = selected != null ? selected.getId() : (selectedRuntime != null ? selectedRuntime.getObjectId() : null);
        if (gizmoManipulating && !Objects.equals(currentObjectId, gizmoObjectId)) {
            finalizeGizmoChange();
        }
        float width = ImGui.getIO().getDisplaySizeX();
        float height = ImGui.getIO().getDisplaySizeY();

        ImGuizmo.beginFrame();
        ImGuizmo.setOrthographic(false);
        ImGuizmo.setDrawList(ImGui.getBackgroundDrawList());
        ImGuizmo.setRect(0, 0, width, height);

        float[] viewArr = matrixToArray(viewMatrix);
        float[] projArr = matrixToArray(projectionMatrix);

        ImGuizmo.manipulate(
                viewArr,
                projArr,
                currentOperation,
                gizmoMode,
                activeMatrix,
                null,
                useSnap ? snapValues : null
        );
        boolean using = ImGuizmo.isUsing();
        if (using) {
            if (!gizmoManipulating) {
                gizmoManipulating = true;
                gizmoObjectId = currentObjectId;
                if (selected != null) {
                    history.beginContinuousChange(selected);
                }
            }
            ImGuizmo.decomposeMatrixToComponents(activeMatrix, activeTranslation, activeRotation, activeScale);
            updateRotationFromMatrix(activeMatrix, activeRotationQuat, activeRotation);
            if (selected != null) {
                updateTransform(session, selected, activeTranslation, activeRotation, activeScale, false, false, activeRotationQuat);
            } else if (selectedRuntime != null) {
                if (selectedLimbType != null && selectedRuntime.getType() == RuntimeObjectType.PLAYER_MODEL) {
                    applyLimbTransform(selectedRuntime, selectedLimbType, activeTranslation, activeRotation, activeScale);
                } else {
                    updateRuntimeTransform(selectedRuntime, activeTranslation, activeRotation, activeScale);
                }
            }
        } else if (gizmoManipulating) {
            finalizeGizmoChange();
        }
    }

    public void renderCameraGizmos(WorldRenderContext renderContext) {
        if (!EditorModeManager.getInstance().isActive()) {
            return;
        }
        var consumers = renderContext.consumers();
        if (consumers == null) {
            return;
        }
        var objects = SceneSessionManager.getInstance().getSceneGraph().getObjects();
        if (objects.isEmpty()) {
            return;
        }
        MatrixStack matrices = renderContext.matrixStack();
        Vec3d camPos = renderContext.camera().getPos();
        VertexConsumer lineBuffer = consumers.getBuffer(RenderLayer.getLines());

        matrices.push();
        matrices.translate(-camPos.x, -camPos.y, -camPos.z);

        for (SceneObject object : objects) {
            if (!"camera".equalsIgnoreCase(object.getType())) continue;
            Map<String, Object> props = object.getProperties();
            Vec3d pos = readVec3(props.get("position"), new Vec3d(0, 65, 0));
            Vec3d rot = readVec3(props.get("rotation"), Vec3d.ZERO);
            double fov = toDouble(props.getOrDefault("fov", 70.0), 70.0);
            double near = Math.max(0.01, toDouble(props.getOrDefault("near", 0.1), 0.1));
            double far = Math.max(near + 0.1, toDouble(props.getOrDefault("far", 128.0), 128.0));

            Quaternionf q = new Quaternionf()
                    .rotateY((float) Math.toRadians(rot.y))
                    .rotateX((float) Math.toRadians(rot.x))
                    .rotateZ((float) Math.toRadians(rot.z));
            Vector3f fwdVec = new Vector3f(0, 0, 1).rotate(q);
            Vector3f rightVec = new Vector3f(1, 0, 0).rotate(q);
            Vector3f upVec = new Vector3f(0, 1, 0).rotate(q);
            Vec3d forward = new Vec3d(fwdVec.x, fwdVec.y, fwdVec.z).normalize();
            Vec3d right = new Vec3d(rightVec.x, rightVec.y, rightVec.z).normalize();
            Vec3d up = new Vec3d(upVec.x, upVec.y, upVec.z).normalize();

            double aspect = 16.0 / 9.0;
            double nearH = Math.tan(Math.toRadians(fov * 0.5)) * near;
            double nearW = nearH * aspect;
            double farH = Math.tan(Math.toRadians(fov * 0.5)) * far;
            double farW = farH * aspect;

            Vec3d nearCenter = pos.add(forward.multiply(near));
            Vec3d farCenter = pos.add(forward.multiply(far));

            Vec3d nr = right.multiply(nearW);
            Vec3d nu = up.multiply(nearH);
            Vec3d fr = right.multiply(farW);
            Vec3d fu = up.multiply(farH);

            Vec3d[] nearCorners = new Vec3d[]{
                    nearCenter.add(nr).add(nu),
                    nearCenter.add(nr).subtract(nu),
                    nearCenter.subtract(nr).add(nu),
                    nearCenter.subtract(nr).subtract(nu)
            };
            Vec3d[] farCorners = new Vec3d[]{
                    farCenter.add(fr).add(fu),
                    farCenter.add(fr).subtract(fu),
                    farCenter.subtract(fr).add(fu),
                    farCenter.subtract(fr).subtract(fu)
            };

            for (int i = 0; i < 4; i++) {
                drawLine(matrices, lineBuffer, nearCorners[i], farCorners[i], 0f, 1f, 1f, 1f);
            }
            drawLine(matrices, lineBuffer, nearCorners[0], nearCorners[1], 0f, 1f, 0f, 1f);
            drawLine(matrices, lineBuffer, nearCorners[1], nearCorners[3], 0f, 1f, 0f, 1f);
            drawLine(matrices, lineBuffer, nearCorners[3], nearCorners[2], 0f, 1f, 0f, 1f);
            drawLine(matrices, lineBuffer, nearCorners[2], nearCorners[0], 0f, 1f, 0f, 1f);
            drawLine(matrices, lineBuffer, farCorners[0], farCorners[1], 0f, 0.6f, 1f, 1f);
            drawLine(matrices, lineBuffer, farCorners[1], farCorners[3], 0f, 0.6f, 1f, 1f);
            drawLine(matrices, lineBuffer, farCorners[3], farCorners[2], 0f, 0.6f, 1f, 1f);
            drawLine(matrices, lineBuffer, farCorners[2], farCorners[0], 0f, 0.6f, 1f, 1f);
            for (Vec3d c : nearCorners) {
                drawLine(matrices, lineBuffer, pos, c, 1f, 0.8f, 0f, 1f);
            }
        }

        for (RuntimeObject runtime : RuntimeObjectRegistry.getInstance().getObjects()) {
            if (runtime.getType() == RuntimeObjectType.PLAYER_MODEL) {
                Vec3d pos = runtime.getPosition();
                Vec3d rot = runtime.getRotation();
                double yawRad = Math.toRadians(rot.y);
                double pitchRad = Math.toRadians(rot.x);
                double cosPitch = Math.cos(pitchRad);
                Vec3d dir = new Vec3d(-Math.sin(yawRad) * cosPitch, -Math.sin(pitchRad), Math.cos(yawRad) * cosPitch);
                Vec3d tip = pos.add(dir.multiply(3.0));
                drawLine(matrices, lineBuffer, pos, tip, 0f, 1f, 0f, 1f);
                Vec3d right = new Vec3d(0.2, 0, 0);
                Vec3d up = new Vec3d(0, 0.2, 0);
                drawLine(matrices, lineBuffer, tip.subtract(right), tip.add(right), 0f, 1f, 0f, 1f);
                drawLine(matrices, lineBuffer, tip.subtract(up), tip.add(up), 0f, 1f, 0f, 1f);
            } else if (runtime.getType() == RuntimeObjectType.PARTICLE_EMITTER) {
                Vec3d pos = runtime.getPosition();
                double r = 0.35;
                Vec3d x = new Vec3d(r, 0, 0);
                Vec3d y = new Vec3d(0, r, 0);
                Vec3d z = new Vec3d(0, 0, r);
                drawLine(matrices, lineBuffer, pos.subtract(x), pos.add(x), 1f, 0.6f, 0.2f, 1f);
                drawLine(matrices, lineBuffer, pos.subtract(y), pos.add(y), 1f, 0.6f, 0.2f, 1f);
                drawLine(matrices, lineBuffer, pos.subtract(z), pos.add(z), 1f, 0.6f, 0.2f, 1f);
            }
        }

        matrices.pop();

        renderLimbHighlight(renderContext, lineBuffer);
    }

    private void renderLimbHighlight(WorldRenderContext ctx, VertexConsumer lineBuffer) {
        RaycastPicker picker = RaycastPicker.getInstance();
        RuntimeObject hovered = picker.getHoveredObject();
        String limb = picker.getHoveredLimb();
        if ((hovered == null || limb == null) && picker.getSelectedObject() != null && picker.getSelectedLimb() != null) {
            hovered = picker.getSelectedObject();
            limb = picker.getSelectedLimb();
        }
        if (hovered == null || limb == null) return;
        var caps = hovered.getLimbCapsules();
        if (caps == null) return;
        Capsule capsule = caps.get(limb);
        if (capsule == null) return;

        MatrixStack matrices = ctx.matrixStack();
        matrices.push();
        Vec3d camPos = ctx.camera().getPos();
        matrices.translate(-camPos.x, -camPos.y, -camPos.z);

        Vec3d a = capsule.a();
        Vec3d b = capsule.b();
        Vec3d mid = a.add(b).multiply(0.5);
        double r = capsule.radius();

        // Draw capsule axis
        drawLine(matrices, lineBuffer, a, b, 1f, 0.7f, 0.2f, 1f);
        // Small cross at midpoint
        Vec3d right = new Vec3d(r, 0, 0);
        Vec3d up = new Vec3d(0, r, 0);
        drawLine(matrices, lineBuffer, mid.subtract(right), mid.add(right), 1f, 0.7f, 0.2f, 1f);
        drawLine(matrices, lineBuffer, mid.subtract(up), mid.add(up), 1f, 0.7f, 0.2f, 1f);

        // Approximate capsule bounds as box so users see the clickable volume
        Vec3d min = new Vec3d(
                Math.min(a.x, b.x) - r,
                Math.min(a.y, b.y) - r,
                Math.min(a.z, b.z) - r
        );
        Vec3d max = new Vec3d(
                Math.max(a.x, b.x) + r,
                Math.max(a.y, b.y) + r,
                Math.max(a.z, b.z) + r
        );
        // 12 edges of the box
        drawLine(matrices, lineBuffer, new Vec3d(min.x, min.y, min.z), new Vec3d(max.x, min.y, min.z), 1f, 0.4f, 0.1f, 0.8f);
        drawLine(matrices, lineBuffer, new Vec3d(min.x, min.y, min.z), new Vec3d(min.x, max.y, min.z), 1f, 0.4f, 0.1f, 0.8f);
        drawLine(matrices, lineBuffer, new Vec3d(min.x, min.y, min.z), new Vec3d(min.x, min.y, max.z), 1f, 0.4f, 0.1f, 0.8f);
        drawLine(matrices, lineBuffer, new Vec3d(max.x, max.y, max.z), new Vec3d(min.x, max.y, max.z), 1f, 0.4f, 0.1f, 0.8f);
        drawLine(matrices, lineBuffer, new Vec3d(max.x, max.y, max.z), new Vec3d(max.x, min.y, max.z), 1f, 0.4f, 0.1f, 0.8f);
        drawLine(matrices, lineBuffer, new Vec3d(max.x, max.y, max.z), new Vec3d(max.x, max.y, min.z), 1f, 0.4f, 0.1f, 0.8f);
        drawLine(matrices, lineBuffer, new Vec3d(max.x, min.y, min.z), new Vec3d(max.x, max.y, min.z), 1f, 0.4f, 0.1f, 0.8f);
        drawLine(matrices, lineBuffer, new Vec3d(max.x, min.y, min.z), new Vec3d(max.x, min.y, max.z), 1f, 0.4f, 0.1f, 0.8f);
        drawLine(matrices, lineBuffer, new Vec3d(min.x, max.y, min.z), new Vec3d(max.x, max.y, min.z), 1f, 0.4f, 0.1f, 0.8f);
        drawLine(matrices, lineBuffer, new Vec3d(min.x, max.y, min.z), new Vec3d(min.x, max.y, max.z), 1f, 0.4f, 0.1f, 0.8f);
        drawLine(matrices, lineBuffer, new Vec3d(min.x, min.y, max.z), new Vec3d(max.x, min.y, max.z), 1f, 0.4f, 0.1f, 0.8f);
        drawLine(matrices, lineBuffer, new Vec3d(min.x, min.y, max.z), new Vec3d(min.x, max.y, max.z), 1f, 0.4f, 0.1f, 0.8f);

        matrices.pop();
    }
    public void selectFromExternal(String objectId) {
        if (objectId == null) {
            return;
        }
        SceneObject object = SceneSessionManager.getInstance().getSceneGraph().get(objectId);
        if (object != null) {
            selectObject(object);
        }
    }

    public boolean isSelectionBoundsVisible() {
        return showSelectionBounds;
    }

    public boolean isExplorerVisible() {
        return showExplorer;
    }

    public void setExplorerVisible(boolean visible) {
        this.showExplorer = visible;
    }

    public boolean isInspectorVisible() {
        return showInspectorPanel;
    }

    public void setInspectorVisible(boolean visible) {
        this.showInspectorPanel = visible;
    }

    public boolean isScriptViewerVisible() {
        return showScriptViewer;
    }

    public void setScriptViewerVisible(boolean visible) {
        this.showScriptViewer = visible;
    }

    public boolean isAssetBrowserVisible() {
        return showAssetBrowser;
    }

    public void setAssetBrowserVisible(boolean visible) {
        this.showAssetBrowser = visible;
    }

    public boolean isDiagnosticsVisible() {
        return showDiagnostics;
    }

    public void setDiagnosticsVisible(boolean visible) {
        this.showDiagnostics = visible;
    }

    public boolean isGizmoToolbarVisible() {
        return showGizmoToolbar;
    }

    public AnimationTimelinePanel getTimelinePanel() {
        return animationTimelinePanel;
    }

    public void triggerEvent(String name, String payload) {
        if (name == null || name.isEmpty()) return;
        if ("particle_burst".equalsIgnoreCase(name)) {
            triggerParticleEvent(payload);
        }
        // TODO: other events type
    }

    private void triggerParticleEvent(String payload) {
        if (payload == null || payload.isEmpty()) {
            return;
        }
        try {
            com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(payload).getAsJsonObject();
            String type = json.has("type") ? json.get("type").getAsString() : "burst";
            if ("burst".equalsIgnoreCase(type)) {
                var descriptor = com.moud.client.util.ParticleDescriptorMapper.fromMap("event-burst", jsonToMap(json));
                if (descriptor != null) {
                    com.moud.client.MoudClientMod.getInstance().getParticleSystem().spawn(descriptor);
                }
            }
        } catch (Exception e) {
            com.moud.client.MoudClientMod.getLogger().warn("Failed to trigger particle event payload {}", payload, e);
        }
    }

    private Map<String, Object> jsonToMap(com.google.gson.JsonObject json) {
        java.util.HashMap<String, Object> map = new java.util.HashMap<>();
        for (var entry : json.entrySet()) {
            if (entry.getValue().isJsonPrimitive()) {
                var prim = entry.getValue().getAsJsonPrimitive();
                if (prim.isNumber()) map.put(entry.getKey(), prim.getAsNumber());
                else if (prim.isBoolean()) map.put(entry.getKey(), prim.getAsBoolean());
                else map.put(entry.getKey(), prim.getAsString());
            } else if (entry.getValue().isJsonObject()) {
                map.put(entry.getKey(), jsonToMap(entry.getValue().getAsJsonObject()));
            }
        }
        return map;
    }

    public void setGizmoToolbarVisible(boolean visible) {
        this.showGizmoToolbar = visible;
    }

    public void setSelectionBoundsVisible(boolean visible) {
        this.showSelectionBounds = visible;
    }

    private boolean hasExtension(String path, String... extensions) {
        if (path == null || extensions == null) {
            return false;
        }
        String lower = path.toLowerCase(Locale.ROOT);
        for (String extension : extensions) {
            if (extension == null) {
                continue;
            }
            if (lower.endsWith(extension.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private void selectObject(SceneObject object) {
        finalizeGizmoChange();
        selectedSceneObjectId = object.getId();
        selectedRuntimeId = null;
        selectedLimbType = null;
        RaycastPicker.getInstance().clearSelection();
        Map<String, Object> props = object.getProperties();
        extractVector(props.getOrDefault("position", null), activeTranslation);
        extractVector(props.getOrDefault("scale", null), activeScale);
        float[] maybeQuat = extractQuaternion(props.get("rotationQuat"), null);
        if (maybeQuat != null) {
            System.arraycopy(maybeQuat, 0, activeRotationQuat, 0, 4);
            quaternionToEulerDegrees(activeRotationQuat, activeRotation);
        } else {
            extractEuler(props.getOrDefault("rotation", null), activeRotation);
            eulerDegreesToQuaternion(activeRotation, activeRotationQuat);
        }
        composeMatrix(activeTranslation, activeScale, activeRotationQuat, activeMatrix);
        EditorCameraController.getInstance().initialSelectionChanged(object, false);
        inspectorPanel.onSelectionChanged(object);
    }

    public void selectRuntimeObject(RuntimeObject runtimeObject) {
        if (runtimeObject == null) {
            return;
        }
        finalizeGizmoChange();
        selectedSceneObjectId = runtimeObject.getObjectId();
        selectedRuntimeId = runtimeObject.getObjectId();
        RaycastPicker picker = RaycastPicker.getInstance();
        String hoveredLimb = picker.getHoveredLimb();
        picker.setSelection(runtimeObject, hoveredLimb);
        selectedLimbType = hoveredLimb;
        boolean limbApplied = false;
        if (selectedLimbType != null && runtimeObject.getType() == RuntimeObjectType.PLAYER_MODEL) {
            limbApplied = populateLimbTransform(runtimeObject, selectedLimbType);
        }
        if (!limbApplied) {
            Vec3d pos = runtimeObject.getPosition();
            activeTranslation[0] = (float) pos.x;
            activeTranslation[1] = (float) pos.y;
            activeTranslation[2] = (float) pos.z;
            Vec3d rot = runtimeObject.getRotation();
            activeRotation[0] = (float) rot.x;
            activeRotation[1] = (float) rot.y;
            activeRotation[2] = (float) rot.z;
            eulerDegreesToQuaternion(activeRotation, activeRotationQuat);
            Vec3d scl = runtimeObject.getScale();
            activeScale[0] = (float) scl.x;
            activeScale[1] = (float) scl.y;
            activeScale[2] = (float) scl.z;
        }
        composeMatrix(activeTranslation, activeScale, activeRotationQuat, activeMatrix);
        inspectorPanel.onRuntimeSelection(runtimeObject);
    }

    public void updateTransform(SceneSessionManager session, SceneObject selected, float[] translation, float[] rotation, float[] scale, boolean discreteChange) {
        updateTransform(session, selected, translation, rotation, scale, discreteChange, true, null);
    }

    public void updateTransform(SceneSessionManager session, SceneObject selected, float[] translation, float[] rotation, float[] scale, boolean discreteChange, boolean recomputeMatrix, float[] quaternion) {
        if (selected == null) {
            return;
        }
        float[] quat = quaternion != null ? quaternion.clone() : null;
        if (quat == null) {
            // derive from current active rotation or provided Euler
            float[] sourceEuler = rotation != null ? rotation : activeRotation;
            quat = new float[4];
            eulerDegreesToQuaternion(sourceEuler, quat);
        }
        System.arraycopy(quat, 0, activeRotationQuat, 0, 4);
        quaternionToEulerDegrees(activeRotationQuat, activeRotation);
        RuntimeObject runtime = getSelectedRuntime();
        if (selectedLimbType != null && runtime != null && runtime.getType() == RuntimeObjectType.PLAYER_MODEL) {
            if (applyLimbTransform(runtime, selectedLimbType, translation, activeRotation, scale)) {
                if (recomputeMatrix) {
                    composeMatrix(translation, scale, activeRotationQuat, activeMatrix);
                }
                animationTimelinePanel.recordLimbTransform(selected, selectedLimbType, translation, activeRotation, scale);
                return;
            }
        }
        if (recomputeMatrix) {
            composeMatrix(translation, scale, activeRotationQuat, activeMatrix);
        }
        if (discreteChange) {
            Map<String, Object> before = history.snapshot(selected);
            session.submitTransformUpdate(selected.getId(), translation, activeRotation, scale, activeRotationQuat);
            animationTimelinePanel.recordTransform(selected, translation, activeRotation, scale);
            Map<String, Object> after = new ConcurrentHashMap<>(before);
            after.put("position", vectorToMap(translation));
            after.put("rotation", rotationMap(activeRotation));
            after.put("rotationQuat", quaternionMap(activeRotationQuat));
            after.put("scale", vectorToMap(scale));
            history.recordDiscreteChange(selected.getId(), before, after);
        } else {
            session.submitTransformUpdate(selected.getId(), translation, activeRotation, scale, activeRotationQuat);
            animationTimelinePanel.recordTransform(selected, translation, activeRotation, scale);
            history.updateContinuousChange(selected);
        }
    }

    private boolean applyLimbTransform(RuntimeObject runtime, String limbKey, float[] translation, float[] rotation, float[] scale) {
        java.util.UUID uuid = resolveFakePlayerUuid(runtime);
        String bone = boneNameFromLimb(limbKey);
        if (uuid == null || bone == null) {
            return false;
        }
        java.util.Map<String, Object> updates = new java.util.HashMap<>();
        updates.put("position", new com.moud.api.math.Vector3(translation[0], translation[1], translation[2]));
        updates.put("rotation", new com.moud.api.math.Vector3(rotation[0], rotation[1], rotation[2]));
        updates.put("scale", new com.moud.api.math.Vector3(scale[0], scale[1], scale[2]));
        PlayerPartConfigManager.getInstance().updatePartConfig(uuid, bone, updates);
        return true;
    }

    private boolean populateLimbTransform(RuntimeObject runtime, String limbKey) {
        java.util.UUID uuid = resolveFakePlayerUuid(runtime);
        String bone = boneNameFromLimb(limbKey);
        if (uuid == null || bone == null) {
            return false;
        }
        PlayerPartConfigManager.PartConfig config = PlayerPartConfigManager.getInstance().getPartConfig(uuid, bone);
        com.moud.api.math.Vector3 pos = config != null ? config.getInterpolatedPosition() : null;
        com.moud.api.math.Vector3 rot = config != null ? config.getInterpolatedRotation() : null;
        com.moud.api.math.Vector3 scl = config != null ? config.getInterpolatedScale() : null;

        activeTranslation[0] = pos != null ? (float) pos.x : 0f;
        activeTranslation[1] = pos != null ? (float) pos.y : 0f;
        activeTranslation[2] = pos != null ? (float) pos.z : 0f;
        activeRotation[0] = rot != null ? (float) rot.x : 0f;
        activeRotation[1] = rot != null ? (float) rot.y : 0f;
        activeRotation[2] = rot != null ? (float) rot.z : 0f;
        eulerDegreesToQuaternion(activeRotation, activeRotationQuat);
        activeScale[0] = scl != null ? (float) scl.x : 1f;
        activeScale[1] = scl != null ? (float) scl.y : 1f;
        activeScale[2] = scl != null ? (float) scl.z : 1f;
        return true;
    }

    private java.util.UUID resolveFakePlayerUuid(RuntimeObject runtime) {
        if (runtime == null || runtime.getObjectId() == null) {
            return null;
        }
        long modelId = -1;
        int idx = runtime.getObjectId().indexOf(':');
        if (idx >= 0) {
            try {
                modelId = Long.parseLong(runtime.getObjectId().substring(idx + 1));
            } catch (NumberFormatException ignored) {
            }
        }
        if (modelId < 0) {
            return null;
        }
        var model = ClientPlayerModelManager.getInstance().getModel(modelId);
        if (model == null || model.getFakePlayer() == null) {
            return null;
        }
        return model.getFakePlayer().getUuid();
    }

    private String boneNameFromLimb(String limbKey) {
        if (limbKey == null) return null;
        if (limbKey.startsWith("fakeplayer:")) {
            limbKey = limbKey.substring("fakeplayer:".length());
        }
        return switch (limbKey) {
            case "left_arm" -> "left_arm";
            case "right_arm" -> "right_arm";
            case "left_leg" -> "left_leg";
            case "right_leg" -> "right_leg";
            case "head" -> "head";
            case "torso" -> "body";
            default -> null;
        };
    }

    private SceneObject getSelected(SceneSessionManager session) {
        if (selectedSceneObjectId == null) {
            return null;
        }
        return session.getSceneGraph().get(selectedSceneObjectId);
    }

    public SceneObject getSelectedObject() {
        return getSelected(SceneSessionManager.getInstance());
    }

    public float[] getActiveTranslation() {
        return activeTranslation;
    }

    public float[] getActiveRotation() {
        return activeRotation;
    }

    public float[] getActiveScale() {
        return activeScale;
    }

    public ImString getMarkerNameBuffer() {
        return markerNameBuffer;
    }

    public int getCurrentGizmoOperation() {
        return currentOperation;
    }

    public void setCurrentGizmoOperation(int operation) {
        this.currentOperation = operation;
    }

    public int getGizmoMode() {
        return gizmoMode;
    }

    public void setGizmoMode(int gizmoMode) {
        this.gizmoMode = gizmoMode;
    }

    public boolean isSnapEnabled() {
        return useSnap;
    }

    public void setSnapEnabled(boolean enabled) {
        this.useSnap = enabled;
    }

    public float[] getSnapValues() {
        return snapValues;
    }

    public RuntimeObject getSelectedRuntime() {

        RaycastPicker picker = RaycastPicker.getInstance();
        if (picker.hasSelection()) {
            return picker.getSelectedObject();
        }

        if (selectedRuntimeId == null) {
            return null;
        }
        return RuntimeObjectRegistry.getInstance().getById(selectedRuntimeId);
    }

    public void focusSelection(SceneObject object) {
        if (object == null) {
            return;
        }
        EditorCameraController.getInstance().focusSelection(object);
    }

    public boolean canUndo() {
        return history.canUndo();
    }

    public boolean canRedo() {
        return history.canRedo();
    }

    public void undo() {
        history.undo();
    }

    public void redo() {
        history.redo();
    }

    public RuntimeObject getHoveredRuntime() {
        return RaycastPicker.getInstance().getHoveredObject();
    }

    public String getSelectedLimbType() {
        return selectedLimbType;
    }

    public boolean beginDockedPanel(EditorDockingLayout.Region region, String title) {
        dockingLayout.apply(region);
        return ImGui.begin(title, PANEL_WINDOW_FLAGS);
    }

    public boolean beginDockedPanel(EditorDockingLayout.Region region, String title, ImBoolean openFlag) {
        dockingLayout.apply(region);
        return ImGui.begin(title, openFlag, PANEL_WINDOW_FLAGS);
    }

    public void emitProjectFileDragPayload(String path) {
        if (path == null) {
            return;
        }
        if (ImGui.beginDragDropSource()) {
            byte[] pathBytes = path.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            ImGui.setDragDropPayload(PAYLOAD_PROJECT_FILE, pathBytes);
            ImGui.text(path);
            ImGui.endDragDropSource();
        }
    }

    public Map<String, List<SceneObject>> getCachedHierarchy(SceneSessionManager session) {
        long now = System.currentTimeMillis();
        if (now - lastHierarchyBuild > HIERARCHY_CACHE_MS) {
            cachedHierarchy = buildHierarchy(session);
            lastHierarchyBuild = now;
        }
        return cachedHierarchy;
    }

    private Map<String, List<SceneObject>> buildHierarchy(SceneSessionManager session) {
        Map<String, List<SceneObject>> children = new ConcurrentHashMap<>();
        children.putIfAbsent("", new java.util.concurrent.CopyOnWriteArrayList<>());
        for (SceneObject object : session.getSceneGraph().getObjects()) {
            String parent = parentIdOf(object);
            children.computeIfAbsent(parent, k -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(object);
            children.putIfAbsent(object.getId(), new java.util.concurrent.CopyOnWriteArrayList<>());
        }
        children.values().forEach(list -> list.sort(java.util.Comparator.comparing(this::sceneLabel, String.CASE_INSENSITIVE_ORDER)));
        return children;
    }

    public void renderHierarchyNode(SceneSessionManager session, SceneObject object, Map<String, List<SceneObject>> tree, String filterText) {
        java.util.List<SceneObject> children = tree.getOrDefault(object.getId(), java.util.List.of());
        String filter = filterText == null ? "" : filterText.trim().toLowerCase();
        if (!filter.isEmpty() && !matchesHierarchyFilter(object, filter)) {
            boolean descendantMatches = false;
            for (SceneObject child : children) {
                if (matchesHierarchyFilterRecursive(child, tree, filter)) {
                    descendantMatches = true;
                    break;
                }
            }
            if (!descendantMatches) {
                return;
            }
        }
        boolean hasChildren = !children.isEmpty();
        int flags = ImGuiTreeNodeFlags.OpenOnArrow | ImGuiTreeNodeFlags.SpanAvailWidth | ImGuiTreeNodeFlags.FramePadding;
        if (!hasChildren) {
            flags |= ImGuiTreeNodeFlags.Leaf | ImGuiTreeNodeFlags.NoTreePushOnOpen;
        }
        if (Objects.equals(selectedSceneObjectId, object.getId())) {
            flags |= ImGuiTreeNodeFlags.Selected;
        }
        String label = sceneLabel(object);
        String visibleLabel = String.format("%s %s##%s", typeIcon(object.getType()), label, object.getId());
        boolean open = ImGui.treeNodeEx(object.getId(), flags, visibleLabel);
        if (ImGui.isItemClicked()) {
            selectObject(object);
        }
        if (ImGui.isItemHovered() && ImGui.isMouseDoubleClicked(0)) {
            EditorCameraController.getInstance().focusSelection(object);
        }
        if (ImGui.beginPopupContextItem("scene_object_ctx_" + object.getId())) {
            if (ImGui.menuItem("Focus")) {
                EditorCameraController.getInstance().focusSelection(object);
            }
            if (ImGui.menuItem("Create Empty Child")) {
                spawnEmptyObject(session, object.getId());
            }
            if (ImGui.menuItem("Delete")) {
                deleteSceneObject(object);
            }
            ImGui.endPopup();
        }
        if (ImGui.beginDragDropSource()) {
            byte[] idBytes = object.getId().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            ImGui.setDragDropPayload(PAYLOAD_HIERARCHY, idBytes);
            ImGui.text("Move " + label);
            ImGui.endDragDropSource();
        }
        if (ImGui.beginDragDropTarget()) {
            handleHierarchyDrop(object.getId());
            ImGui.endDragDropTarget();
        }
        if (open && hasChildren) {
            for (SceneObject child : children) {
                renderHierarchyNode(session, child, tree, filter);
            }
            ImGui.treePop();
        }
    }

    public void handleHierarchyDrop(String newParent) {
        byte[] payload = ImGui.acceptDragDropPayload(PAYLOAD_HIERARCHY);
        if (payload != null) {
            String draggedId = new String(payload, java.nio.charset.StandardCharsets.UTF_8);
            if (!Objects.equals(draggedId, newParent)) {
                reparentObject(draggedId, newParent);
            }
        }
        payload = ImGui.acceptDragDropPayload(PAYLOAD_ASSET);
        if (payload != null) {
            String assetId = new String(payload, java.nio.charset.StandardCharsets.UTF_8);
            spawnAssetById(assetId, newParent);
        }
    }

    private void renderRuntimeList(RuntimeObjectType type, String label, String filterTerm) {
        Collection<RuntimeObject> objects = RuntimeObjectRegistry.getInstance().getObjects(type);
        if (objects.isEmpty()) {
            ImGui.textDisabled("No " + label.toLowerCase());
            return;
        }
        String filter = filterTerm == null ? "" : filterTerm.trim().toLowerCase();
        if (ImGui.treeNodeEx("runtime_" + label, ImGuiTreeNodeFlags.DefaultOpen | ImGuiTreeNodeFlags.SpanAvailWidth, label)) {
            int shown = 0;
            for (RuntimeObject object : objects) {
                String name = object.getLabel();
                if (!filter.isEmpty() && !name.toLowerCase().contains(filter) && !object.getObjectId().toLowerCase().contains(filter)) {
                    continue;
                }
                shown++;
                boolean selected = Objects.equals(selectedRuntimeId, object.getObjectId());
                if (ImGui.selectable(name + "##runtime_" + object.getObjectId(), selected)) {
                    selectRuntimeObject(object);
                }
                if (ImGui.isItemHovered() && ImGui.isMouseDoubleClicked(0)) {
                    EditorCameraController.getInstance().focusPoint(object.getPosition());
                }
            }
            if (shown == 0) {
                ImGui.textDisabled("No " + label.toLowerCase() + " match filters");
            }
            ImGui.treePop();
        }
    }

    private void reparentObject(String childId, String parentId) {
        ConcurrentHashMap<String, Object> props = new ConcurrentHashMap<>();
        if (parentId == null || parentId.isEmpty()) {
            props.put("parent", "");
        } else {
            props.put("parent", parentId);
        }
        SceneSessionManager.getInstance().submitPropertyUpdate(childId, props);
        SceneEditorDiagnostics.log("Parent set: " + childId + " -> " + (parentId == null || parentId.isEmpty() ? "root" : parentId));
    }

    public void spawnEmptyObject(SceneSessionManager session, String parentId) {
        Map<String, Object> payload = new ConcurrentHashMap<>();
        payload.put("type", "group");
        Map<String, Object> props = new ConcurrentHashMap<>();
        props.put("label", "Empty Object");
        props.put("position", vectorToMap(resolveSpawnPosition(newObjectPosition)));
        props.put("scale", vectorToMap(newObjectScale));
        props.put("rotation", rotationMap(activeRotation));
        if (parentId != null && !parentId.isEmpty()) {
            props.put("parent", parentId);
        }
        payload.put("properties", props);
        session.submitEdit("create", payload);
        SceneEditorDiagnostics.log("Created empty object" + (parentId == null || parentId.isEmpty() ? "" : " under " + parentId));
    }

    public boolean hasAssetsOfType(String assetType) {
        for (MoudPackets.EditorAssetDefinition asset : EditorAssetCatalog.getInstance().getAssets()) {
            if (assetType.equalsIgnoreCase(asset.objectType())) {
                return true;
            }
        }
        return false;
    }

    private void renderAssetPopup(String popupId, String assetType, ImString filterBuffer, String parentId) {
        if (ImGui.beginPopup(popupId)) {
            String filterValue = filterBuffer.get().trim().toLowerCase();
            ImGui.setNextItemWidth(-1);
            ImGui.inputTextWithHint("##" + popupId + "_filter", "Search assets...", filterBuffer);
            ImGui.separator();
            int shown = 0;
            for (MoudPackets.EditorAssetDefinition asset : EditorAssetCatalog.getInstance().getAssets()) {
                if (!assetType.equalsIgnoreCase(asset.objectType())) {
                    continue;
                }
                if (!filterValue.isEmpty() && !asset.label().toLowerCase().contains(filterValue) && !asset.id().toLowerCase().contains(filterValue)) {
                    continue;
                }
                shown++;
                if (ImGui.selectable(asset.label() + "##popup_asset_" + asset.id())) {
                    spawnAsset(asset, parentId);
                    ImGui.closeCurrentPopup();
                }
            }
            if (shown == 0) {
                ImGui.textDisabled("No assets available");
            }
            ImGui.endPopup();
        }
    }

    private void renderMarkerPopup(String popupId, SceneSessionManager session, String parentId) {
        if (!ImGui.beginPopup(popupId)) {
            return;
        }
        ImGui.inputText("Name", markerNameBuffer);
        if (ImGui.checkbox("Snap to Grid", markerSnapToggle)) {

        }
        if (markerSnapToggle.get()) {
            ImGui.dragFloat("Step", markerSnapValue.getData(), 0.05f, 0.05f, 10f, "%.2f");
        }
        if (ImGui.button("Create")) {
            spawnMarker(session, parentId);
            ImGui.closeCurrentPopup();
        }
        ImGui.sameLine();
        if (ImGui.button("Cancel")) {
            ImGui.closeCurrentPopup();
        }
        ImGui.endPopup();
    }

    private void renderFakePlayerPopup(String popupId, SceneSessionManager session, String parentId) {
        if (!ImGui.beginPopup(popupId)) {
            return;
        }
        ImGui.inputText("Label", fakePlayerLabelBuffer);
        ImGui.inputText("Skin URL", fakePlayerSkinBuffer);
        ImGui.dragFloat2("Size (w,h)", fakePlayerDimensions, 0.05f, 0.1f, 5.0f, "%.2f");
        ImGui.dragFloat("Path Speed", fakePlayerPathSpeed, 0.05f, 0.0f, 20.0f, "%.2f");
        ImGui.checkbox("Loop Path", fakePlayerPathLoop);
        ImGui.sameLine();
        ImGui.checkbox("Ping Pong", fakePlayerPathPingPong);
        FakePlayerPathEditor.render(fakePlayerPathPoints, () -> {
            float[] pos = resolveSpawnPosition(newObjectPosition);
            fakePlayerPathPoints.add(new float[]{pos[0], pos[1], pos[2]});
        });
        ImGui.checkbox("Physics", fakePlayerPhysics);
        ImGui.sameLine();
        ImGui.checkbox("Sneak", fakePlayerSneak);
        ImGui.sameLine();
        ImGui.checkbox("Sprint", fakePlayerSprint);
        ImGui.checkbox("Swing", fakePlayerSwing);
        ImGui.sameLine();
        ImGui.checkbox("Use Item", fakePlayerUse);
        if (ImGui.button("Create##fake_player_create")) {
            spawnFakePlayer(session, parentId);
            ImGui.closeCurrentPopup();
        }
        ImGui.sameLine();
        if (ImGui.button("Cancel##fake_player_cancel")) {
            ImGui.closeCurrentPopup();
        }
        ImGui.endPopup();
    }

    private void renderCameraPopup(String popupId, SceneSessionManager session, String parentId) {
        if (!ImGui.beginPopup(popupId)) {
            return;
        }
        ImGui.inputText("Label", cameraLabelBuffer);
        ImGui.dragFloat3("Position", cameraPositionBuffer, 0.05f);
        ImGui.dragFloat3("Rotation (pitch/yaw/roll)", cameraRotationBuffer, 0.5f);
        ImGui.dragFloat("FOV", cameraFov.getData(), 1f, 10f, 170f, "%.1f");
        ImGui.dragFloat("Near", cameraNear.getData(), 0.01f, 0.01f, 10f, "%.2f");
        ImGui.dragFloat("Far", cameraFar.getData(), 1f, 1f, 2048f, "%.1f");
        if (ImGui.button("Create##camera_create")) {
            spawnCamera(session, parentId);
            ImGui.closeCurrentPopup();
        }
        ImGui.sameLine();
        if (ImGui.button("Cancel##camera_cancel")) {
            ImGui.closeCurrentPopup();
        }
        ImGui.endPopup();
    }

    private void deleteSceneObject(SceneObject object) {
        if (object == null) {
            return;
        }
        Map<String, Object> payload = new ConcurrentHashMap<>();
        payload.put("id", object.getId());
        SceneSessionManager.getInstance().submitEdit("delete", payload);
        SceneEditorDiagnostics.log("Deleted " + object.getId());
    }

    private String sceneLabel(SceneObject object) {
        if (object == null) {
            return "";
        }
        Object label = object.getProperties().getOrDefault("label", object.getId());
        return label == null ? object.getId() : String.valueOf(label);
    }

    public void beginCornerSelection(BlueprintCornerSelector.Corner corner) {
        BlueprintCornerSelector.getInstance().beginSelection(corner, pos -> applyPickedCorner(corner, pos));
    }

    public void clearCornerSelection() {
        regionASet = false;
        regionBSet = false;
        BlueprintCornerSelector.getInstance().cancel();
    }

    private void applyPickedCorner(BlueprintCornerSelector.Corner corner, float[] position) {
        if (position == null) {
            return;
        }
        float[] target = corner == BlueprintCornerSelector.Corner.A ? regionCornerA : regionCornerB;
        System.arraycopy(position, 0, target, 0, 3);
        if (corner == BlueprintCornerSelector.Corner.A) {
            regionASet = true;
        } else {
            regionBSet = true;
        }
    }

    public boolean isCornerSelectionActive() {
        return BlueprintCornerSelector.getInstance().isPicking();
    }

    public BlueprintCornerSelector.Corner getPendingCorner() {
        return BlueprintCornerSelector.getInstance().getPendingCorner();
    }

    private Box buildRegionBox() {
        if (!regionASet || !regionBSet) {
            return null;
        }
        double minX = Math.min(regionCornerA[0], regionCornerB[0]);
        double minY = Math.min(regionCornerA[1], regionCornerB[1]);
        double minZ = Math.min(regionCornerA[2], regionCornerB[2]);
        double maxX = Math.max(regionCornerA[0], regionCornerB[0]);
        double maxY = Math.max(regionCornerA[1], regionCornerB[1]);
        double maxZ = Math.max(regionCornerA[2], regionCornerB[2]);
        return new Box(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public Box getSelectedRegionBox() {
        return buildRegionBox();
    }

    public int countObjectsInRegion(Box region, boolean markersOnly) {
        if (region == null) {
            return 0;
        }
        int count = 0;
        for (SceneObject object : SceneSessionManager.getInstance().getSceneGraph().getObjects()) {
            String type = object.getType() == null ? "" : object.getType().toLowerCase();
            boolean isMarker = "marker".equals(type);
            if (markersOnly && !isMarker) continue;
            if (!markersOnly && isMarker) continue;
            Vec3d pos = getObjectPosition(object);
            if (pos == null) continue;
            if (region.contains(pos.x, pos.y, pos.z)) {
                count++;
            }
        }
        return count;
    }

    public void exportBlueprint(SceneSessionManager session, Box region, String requestedName) {
        if (region == null) {
            return;
        }
        String sanitized = requestedName == null ? "" : requestedName.trim();
        if (sanitized.isEmpty()) {
            SceneEditorDiagnostics.log("Blueprint export failed: name required");
            return;
        }
        sanitized = sanitized.replaceAll("[^a-zA-Z0-9_\\-]", "_");
        Blueprint blueprint = new Blueprint();
        blueprint.name = sanitized;
        blueprint.origin[0] = (float) region.minX;
        blueprint.origin[1] = (float) region.minY;
        blueprint.origin[2] = (float) region.minZ;
        blueprint.size[0] = (float) (region.maxX - region.minX);
        blueprint.size[1] = (float) (region.maxY - region.minY);
        blueprint.size[2] = (float) (region.maxZ - region.minZ);
        for (SceneObject object : session.getSceneGraph().getObjects()) {
            Vec3d pos = getObjectPosition(object);
            if (pos == null || !region.contains(pos.x, pos.y, pos.z)) {
                continue;
            }
            String type = object.getType() == null ? "" : object.getType().toLowerCase();
            if ("marker".equals(type)) {
                BlueprintMarker marker = new BlueprintMarker();
                marker.name = String.valueOf(object.getProperties().getOrDefault("name", object.getId()));
                marker.position = toRelative(pos, region);
                blueprint.markers.add(marker);
                continue;
            }
            BlueprintObject blueprintObject = new BlueprintObject();
            blueprintObject.type = type;
            blueprintObject.label = String.valueOf(object.getProperties().getOrDefault("label", object.getId()));
            blueprintObject.position = toRelative(pos, region);
            float[] rotation = new float[3];
            extractEuler(object.getProperties().getOrDefault("rotation", null), rotation);
            blueprintObject.rotation = rotation.clone();
            float[] scale = new float[]{1f, 1f, 1f};
            extractVector(object.getProperties().getOrDefault("scale", null), scale);
            blueprintObject.scale = scale.clone();
            if ("model".equals(type)) {
                Object modelPath = object.getProperties().get("modelPath");
                Object texture = object.getProperties().get("texture");
                blueprintObject.modelPath = modelPath != null ? modelPath.toString() : null;
                blueprintObject.texture = texture != null ? texture.toString() : null;
            }
            Box bounds = computeObjectBounds(object, pos);
            if (bounds != null) {
                blueprintObject.boundsMin = toRelative(new Vec3d(bounds.minX, bounds.minY, bounds.minZ), region);
                blueprintObject.boundsMax = toRelative(new Vec3d(bounds.maxX, bounds.maxY, bounds.maxZ), region);
            }
            blueprint.objects.add(blueprintObject);
        }
        Blueprint.BlockVolume volume = captureBlockVolume(region);
        if (volume != null) {
            blueprint.blocks = volume;
        }
        SceneEditorDiagnostics.log("Uploading blueprint '" + sanitized + "' to server...");
        String finalSanitized = sanitized;
        ClientBlueprintNetwork.getInstance().saveBlueprint(sanitized, blueprint, success -> {
            if (!success) {
                SceneEditorDiagnostics.log("Server failed to save blueprint '" + finalSanitized + "'");
            } else {
                SceneEditorDiagnostics.log("Blueprint '" + finalSanitized + "' saved on server");
            }
        });
    }

    public void loadBlueprintPreview(String fileName, float[] position) {
        if (fileName == null || fileName.trim().isEmpty()) {
            SceneEditorDiagnostics.log("Blueprint preview failed: name required");
            return;
        }
        String normalized = fileName.trim();
        SceneEditorDiagnostics.log("Requesting blueprint '" + normalized + "' from server...");
        ClientBlueprintNetwork.getInstance().requestBlueprint(normalized, blueprint -> {
            if (blueprint == null) {
                SceneEditorDiagnostics.log("Blueprint '" + normalized + "' not found on server");
                return;
            }
            previewRotation[0] = previewRotation[2] = 0f;
            previewRotation[1] = 0f;
            previewScale[0] = previewScale[1] = previewScale[2] = 1f;
            BlueprintPreviewManager.getInstance().show(blueprint, position);
            BlueprintPreviewManager.getInstance().rotate(previewRotation[1]);
            composeMatrix(previewPosition, previewRotation, previewScale, previewMatrix);
            SceneEditorDiagnostics.log("Loaded preview for " + blueprint.name);
        });
    }

    public void hideBlueprintPreview() {
        BlueprintPreviewManager.getInstance().clear();
        previewGizmoActive = false;
    }

    private float[] toRelative(Vec3d world, Box region) {
        return new float[]{
                (float) (world.x - region.minX),
                (float) (world.y - region.minY),
                (float) (world.z - region.minZ)
        };
    }

    private Blueprint.BlockVolume captureBlockVolume(Box region) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld world = client != null ? client.world : null;
        if (world == null || region == null) {
            return null;
        }
        int minX = MathHelper.floor(region.minX);
        int minY = MathHelper.floor(region.minY);
        int minZ = MathHelper.floor(region.minZ);
        int sizeX = Math.max(1, MathHelper.ceil(region.maxX - region.minX));
        int sizeY = Math.max(1, MathHelper.ceil(region.maxY - region.minY));
        int sizeZ = Math.max(1, MathHelper.ceil(region.maxZ - region.minZ));
        long total = (long) sizeX * sizeY * sizeZ;
        if (total >MAX_BLOCK_CAPTURE_BLOCKS) {
            SceneEditorDiagnostics.log("Region too large for block capture (" + total + " blocks). Skipping blocks.");
            return null;
        }
        List<String> palette = new ArrayList<>();
        Map<String, Integer> paletteIndex = new HashMap<>();
        palette.add("minecraft:air");
        paletteIndex.put("minecraft:air", 0);

        boolean useShort = false;
        byte[] byteVoxels = new byte[(int) total];
        byte[] shortVoxels = null;
        BlockPos.Mutable mutable = new BlockPos.Mutable();

        int cursor = 0;
        for (int y = 0; y < sizeY; y++) {
            int worldY = minY + y;
            for (int z = 0; z < sizeZ; z++) {
                int worldZ = minZ + z;
                for (int x = 0; x < sizeX; x++) {
                    int worldX = minX + x;
                    mutable.set(worldX, worldY, worldZ);
                    BlockState state = world.getBlockState(mutable);
                    String key = state == null || state.isAir() ? "minecraft:air" : describeBlockState(state);
                    int paletteIdx = paletteIndex.computeIfAbsent(key, k -> {
                        int idx = palette.size();
                        palette.add(k);
                        return idx;
                    });
                    if (!useShort && paletteIdx > 255) {
                        useShort = true;
                        shortVoxels = new byte[(int) total * 2];
                        for (int i = 0; i < cursor; i++) {
                            shortVoxels[i * 2] = byteVoxels[i];
                            shortVoxels[i * 2 + 1] = 0;
                        }
                    }
                    if (useShort) {
                        int offset = cursor * 2;
                        shortVoxels[offset] = (byte) (paletteIdx & 0xFF);
                        shortVoxels[offset + 1] = (byte) ((paletteIdx >> 8) & 0xFF);
                    } else {
                        byteVoxels[cursor] = (byte) paletteIdx;
                    }
                    cursor++;
                }
            }
        }

        Blueprint.BlockVolume volume = new Blueprint.BlockVolume();
        volume.sizeX = sizeX;
        volume.sizeY = sizeY;
        volume.sizeZ = sizeZ;
        volume.palette = palette;
        volume.useShortIndices = useShort;
        volume.voxels = useShort ? shortVoxels : byteVoxels;
        SceneEditorDiagnostics.log("Captured " + total + " blocks (" + palette.size() + " palette entries) for blueprint.");
        return volume;
    }

    private String describeBlockState(BlockState state) {
        var id = Registries.BLOCK.getId(state.getBlock());
        String base = id != null ? id.toString() : "minecraft:air";
        Map<Property<?>, Comparable<?>> entries = state.getEntries();
        if (entries == null || entries.isEmpty()) {
            return base;
        }
        StringBuilder builder = new StringBuilder(base);
        builder.append('[');
        List<Map.Entry<Property<?>, Comparable<?>>> ordered = new ArrayList<>(entries.entrySet());
        ordered.sort(Comparator.comparing(entry -> entry.getKey().getName()));
        boolean first = true;
        for (Map.Entry<Property<?>, Comparable<?>> entry : ordered) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append(entry.getKey().getName())
                    .append('=')
                    .append(describePropertyValue(entry.getKey(), entry.getValue()));
        }
        builder.append(']');
        return builder.toString();
    }

    @SuppressWarnings("unchecked")
    private <T extends Comparable<T>> String describePropertyValue(Property<T> property, Comparable<?> value) {
        try {
            return property.name((T) value);
        } catch (ClassCastException e) {
            return String.valueOf(value);
        }
    }

    public void renderPreviewGizmo() {
        if (!previewGizmoActive) {
            return;
        }
        BlueprintPreviewManager.PreviewState preview = BlueprintPreviewManager.getInstance().getCurrent();
        if (preview == null) {
            previewGizmoActive = false;
            return;
        }
        org.joml.Matrix4f view = new org.joml.Matrix4f();
        org.joml.Matrix4f projection = new org.joml.Matrix4f();
        if (!WorldViewCapture.copyMatrices(view, projection)) {
            return;
        }
        composeMatrix(previewPosition, previewRotation, previewScale, previewMatrix);
        float[] viewArr = matrixToArray(view);
        float[] projArr = matrixToArray(projection);
        ImGuizmo.manipulate(
                viewArr,
                projArr,
                previewGizmoOperation,
                Mode.LOCAL,
                previewMatrix,
                null,
                null
        );
        if (ImGuizmo.isUsing()) {
            ImGuizmo.decomposeMatrixToComponents(previewMatrix, previewPosition, previewRotation, previewScale);
            BlueprintPreviewManager.getInstance().move(previewPosition);
            BlueprintPreviewManager.getInstance().rotate(previewRotation[1]);
        }
    }

    public boolean hasRegionCorners() {
        return regionASet || regionBSet;
    }

    public boolean isRegionACaptured() {
        return regionASet;
    }

    public boolean isRegionBCaptured() {
        return regionBSet;
    }

    public float[] getRegionCornerA() {
        return regionCornerA;
    }

    public float[] getRegionCornerB() {
        return regionCornerB;
    }

    public ImString getBlueprintNameBuffer() {
        return blueprintNameBuffer;
    }

    public ImString getBlueprintPreviewNameBuffer() {
        return blueprintPreviewName;
    }

    public float[] getPreviewPositionBuffer() {
        return previewPosition;
    }

    public float[] getPreviewRotationBuffer() {
        return previewRotation;
    }

    public float[] getPreviewScaleBuffer() {
        return previewScale;
    }

    public boolean isPreviewGizmoActive() {
        return previewGizmoActive;
    }

    public void setPreviewGizmoActive(boolean active) {
        this.previewGizmoActive = active;
    }

    public int getPreviewGizmoOperation() {
        return previewGizmoOperation;
    }

    public void setPreviewGizmoOperation(int operation) {
        this.previewGizmoOperation = operation;
    }

    public void updatePreviewMatrix() {
        composeMatrix(previewPosition, previewRotation, previewScale, previewMatrix);
    }

    public static Box buildBoxFromCorners(float[] a, float[] b) {
        double minX = Math.min(a[0], b[0]);
        double minY = Math.min(a[1], b[1]);
        double minZ = Math.min(a[2], b[2]);
        double maxX = Math.max(a[0], b[0]);
        double maxY = Math.max(a[1], b[1]);
        double maxZ = Math.max(a[2], b[2]);
        return new Box(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private Vec3d getObjectPosition(SceneObject object) {
        Object raw = object.getProperties().get("position");
        if (!(raw instanceof Map<?, ?> map)) {
            return null;
        }
        Object x = map.get("x");
        Object y = map.get("y");
        Object z = map.get("z");
        if (x instanceof Number && y instanceof Number && z instanceof Number) {
            return new Vec3d(((Number) x).doubleValue(), ((Number) y).doubleValue(), ((Number) z).doubleValue());
        }
        return null;
    }

    private Box computeObjectBounds(SceneObject object, Vec3d position) {
        String type = object.getType() == null ? "" : object.getType().toLowerCase();
        if ("model".equals(type)) {
            Long modelId = SceneSelectionManager.getInstance().getBindingForObject(object.getId());
            if (modelId != null) {
                RenderableModel model = ClientModelManager.getInstance().getModel(modelId);
                if (model != null && model.hasMeshBounds()) {
                    Vec3d min = new Vec3d(model.getMeshMin().x, model.getMeshMin().y, model.getMeshMin().z);
                    Vec3d max = new Vec3d(model.getMeshMax().x, model.getMeshMax().y, model.getMeshMax().z);
                    Vec3d half = max.subtract(min).multiply(0.5);
                    Vec3d center = min.add(half);
                    Vec3d worldCenter = position.add(center.x, center.y, center.z);
                    return new Box(
                            worldCenter.x - half.x,
                            worldCenter.y - half.y,
                            worldCenter.z - half.z,
                            worldCenter.x + half.x,
                            worldCenter.y + half.y,
                            worldCenter.z + half.z
                    );
                }
            }
        }
        double size = 0.5;
        return new Box(
                position.x - size,
                position.y - size,
                position.z - size,
                position.x + size,
                position.y + size,
                position.z + size
        );
    }

    private static String typeIcon(String type) {
        return switch (type == null ? "" : type.toLowerCase()) {
            case "model" -> "[M]";
            case "display" -> "[D]";
            case "group" -> "[G]";
            case "light" -> "[L]";
            case "marker" -> "[K]";
            case "camera" -> "[C]";
            case "particle_emitter" -> "[P]";
            default -> "[ ]";
        };
    }

    private static float[] identity() {
        return new float[]{
                1f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f,
                0f, 0f, 1f, 0f,
                0f, 0f, 0f, 1f
        };
    }

    private static float[] matrixToArray(Matrix4f matrix) {
        float[] arr = new float[16];
        matrix.get(arr);
        return arr;
    }

    private static void composeMatrix(float[] translation, float[] scale, float[] quaternion, float[] outMatrix) {
        Quaternionf q = new Quaternionf(quaternion[0], quaternion[1], quaternion[2], quaternion[3]);
        Matrix4f matrix = new Matrix4f()
                .translation(translation[0], translation[1], translation[2])
                .rotate(q)
                .scale(scale[0], scale[1], scale[2]);
        matrix.get(outMatrix);
    }


    private static float[] extractVector(Object value, float[] fallback) {
        if (value instanceof Map<?,?> map) {
            Object x = map.get("x");
            Object y = map.get("y");
            Object z = map.get("z");
            if (x != null) fallback[0] = toFloat(x);
            if (y != null) fallback[1] = toFloat(y);
            if (z != null) fallback[2] = toFloat(z);
        }
        return fallback;
    }

    private static float[] extractQuaternion(Object value, float[] fallback) {
        if (value instanceof Map<?, ?> map) {
            Object x = map.get("x");
            Object y = map.get("y");
            Object z = map.get("z");
            Object w = map.get("w");
            if (x != null && y != null && z != null && w != null) {
                float[] out = fallback != null ? fallback : new float[4];
                out[0] = toFloat(x);
                out[1] = toFloat(y);
                out[2] = toFloat(z);
                out[3] = toFloat(w);
                return out;
            }
        }
        return null;
    }

    private static float[] extractEuler(Object value, float[] fallback) {
        if (value instanceof Map<?,?> map) {
            boolean hasEulerKeys = map.containsKey("pitch") || map.containsKey("yaw") || map.containsKey("roll");
            boolean hasAxisKeys = map.containsKey("x") || map.containsKey("y") || map.containsKey("z");
            if (hasEulerKeys) {
                Object pitch = map.get("pitch");
                Object yawObj = map.get("yaw");
                Object roll = map.get("roll");
                if (pitch != null) fallback[0] = toFloat(pitch);
                if (yawObj != null) fallback[1] = toFloat(yawObj);
                if (roll != null) fallback[2] = toFloat(roll);
            } else if (hasAxisKeys) {
                Object pitch = map.get("x");
                Object yawObj = map.get("y");
                Object roll = map.get("z");
                if (pitch != null) fallback[0] = toFloat(pitch);
                if (yawObj != null) fallback[1] = toFloat(yawObj);
                if (roll != null) fallback[2] = toFloat(roll);
            }
        }
        return fallback;
    }

    private static Map<String, Object> vectorToMap(float[] vector) {
        Map<String, Object> map = new ConcurrentHashMap<>();
        map.put("x", vector[0]);
        map.put("y", vector[1]);
        map.put("z", vector[2]);
        return map;
    }

    public String parentIdOf(SceneObject object) {
        Object parent = object.getProperties().get("parent");
        if (parent == null) {
            return "";
        }
        String id = String.valueOf(parent);
        return id.isBlank() ? "" : id;
    }

    private static float toFloat(Object value) {
        if (value instanceof Number number) {
            return number.floatValue();
        }
        try {
            return Float.parseFloat(String.valueOf(value));
        } catch (Exception e) {
            return 0f;
        }
    }

    private static double toDouble(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return value != null ? Double.parseDouble(String.valueOf(value)) : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    private static Vec3d readVec3(Object raw, Vec3d fallback) {
        if (raw instanceof Map<?, ?> map) {
            boolean hasEuler = map.containsKey("pitch") || map.containsKey("yaw") || map.containsKey("roll");
            if (hasEuler) {
                double pitch = toDouble(map.get("pitch"), fallback.x);
                double yaw = toDouble(map.get("yaw"), fallback.y);
                double roll = toDouble(map.get("roll"), fallback.z);
                return new Vec3d(pitch, yaw, roll);
            }
            double x = toDouble(map.get("x"), fallback.x);
            double y = toDouble(map.get("y"), fallback.y);
            double z = toDouble(map.get("z"), fallback.z);
            return new Vec3d(x, y, z);
        }
        return fallback;
    }

    private static void eulerDegreesToQuaternion(float[] eulerDeg, float[] outQuat) {
        Quaternionf q = new Quaternionf()
                .rotateXYZ(
                        (float) Math.toRadians(eulerDeg[0]),
                        (float) Math.toRadians(eulerDeg[1]),
                        (float) Math.toRadians(eulerDeg[2])
                );
        outQuat[0] = q.x;
        outQuat[1] = q.y;
        outQuat[2] = q.z;
        outQuat[3] = q.w;
    }

    private static void quaternionToEulerDegrees(float[] quat, float[] outEuler) {
        Quaternionf q = new Quaternionf(quat[0], quat[1], quat[2], quat[3]);
        Vector3f angles = q.getEulerAnglesXYZ(new Vector3f());
        float rx = angles.x;
        float ry = angles.y;
        float rz = angles.z;
        outEuler[0] = (float) Math.toDegrees(rx);
        outEuler[1] = (float) Math.toDegrees(ry);
        outEuler[2] = (float) Math.toDegrees(rz);
    }

    private static void updateRotationFromMatrix(float[] matrixArr, float[] outQuat, float[] outEuler) {
        float prevX = outQuat[0];
        float prevY = outQuat[1];
        float prevZ = outQuat[2];
        float prevW = outQuat[3];

        Quaternionf q = new Matrix4f().set(matrixArr).getNormalizedRotation(new Quaternionf());
        if (!Float.isFinite(q.x) || !Float.isFinite(q.y) || !Float.isFinite(q.z) || !Float.isFinite(q.w)
                || q.lengthSquared() < 1.0e-10f) {
            // Very small scales can obliterate the rotation axes; fall back to the last known rotation.
            q.set(prevX, prevY, prevZ, prevW);
            if (q.lengthSquared() < 1.0e-10f) {
                q.identity();
            }
        } else {
            q.normalize();
        }

        outQuat[0] = q.x;
        outQuat[1] = q.y;
        outQuat[2] = q.z;
        outQuat[3] = q.w;
        quaternionToEulerDegrees(outQuat, outEuler);
    }

    private static void drawLine(MatrixStack matrices, VertexConsumer buffer, Vec3d a, Vec3d b, float r, float g, float bl, float alpha) {
        Matrix4f mat = matrices.peek().getPositionMatrix();
        buffer.vertex(mat, (float) a.x, (float) a.y, (float) a.z).color(r, g, bl, alpha).normal(0, 1, 0);
        buffer.vertex(mat, (float) b.x, (float) b.y, (float) b.z).color(r, g, bl, alpha).normal(0, 1, 0);
    }

    private float[] resolveSpawnPosition(float[] fallback) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            ClientPlayerEntity player = client.player;
            if (player != null) {
                Vec3d look = player.getRotationVec(1.0f);
                Vec3d pos = player.getPos().add(look.multiply(2.0));
                return new float[]{(float) pos.x, (float) pos.y, (float) pos.z};
            }
        }
        return fallback.clone();
    }

    private Map<String, Object> rotationMap(float[] euler) {
        Map<String, Object> map = new ConcurrentHashMap<>();
        map.put("pitch", euler[0]);
        map.put("yaw", euler[1]);
        map.put("roll", euler[2]);
        return map;
    }

    private Map<String, Object> quaternionMap(float[] quat) {
        Map<String, Object> map = new ConcurrentHashMap<>();
        map.put("x", quat[0]);
        map.put("y", quat[1]);
        map.put("z", quat[2]);
        map.put("w", quat[3]);
        return map;
    }

    public void spawnAsset(MoudPackets.EditorAssetDefinition entry) {
        spawnAsset(entry, null);
    }

    private void spawnAsset(MoudPackets.EditorAssetDefinition entry, String parentId) {
        Map<String, Object> payload = new ConcurrentHashMap<>();
        payload.put("type", entry.objectType());
        Map<String, Object> props = new ConcurrentHashMap<>(entry.defaultProperties());
        props.putIfAbsent("label", entry.label());
        props.put("position", vectorToMap(resolveSpawnPosition(newObjectPosition)));
        props.putIfAbsent("scale", vectorToMap(new float[]{1f, 1f, 1f}));
        props.putIfAbsent("rotation", rotationMap(activeRotation));
        if (parentId != null && !parentId.isEmpty()) {
            props.put("parent", parentId);
        }
        payload.put("properties", props);
        SceneSessionManager.getInstance().submitEdit("create", payload);
        SceneEditorDiagnostics.log("Spawn request: " + entry.label());
    }

    private void spawnAssetById(String assetId, String parentId) {
        for (MoudPackets.EditorAssetDefinition entry : EditorAssetCatalog.getInstance().getAssets()) {
            if (entry.id().equals(assetId)) {
                spawnAsset(entry, parentId);
                SceneEditorDiagnostics.log("Spawned " + entry.label() + (parentId == null || parentId.isEmpty() ? "" : " under " + parentId));
                return;
            }
        }
    }

    public void spawnParticleEmitter(SceneSessionManager session, String parentId) {
        String label = "Emitter-" + System.currentTimeMillis();
        Map<String, Object> payload = new ConcurrentHashMap<>();
        payload.put("type", "particle_emitter");
        Map<String, Object> props = new ConcurrentHashMap<>();
        props.put("label", label);
        props.put("name", label);
        props.put("position", vectorToMap(resolveSpawnPosition(newObjectPosition)));
        props.putIfAbsent("rotation", rotationMap(activeRotation));
        props.putIfAbsent("rotationQuat", quaternionMap(activeRotationQuat));
        props.putIfAbsent("scale", vectorToMap(newObjectScale));
        props.putIfAbsent("texture", "minecraft:particle/generic_0");
        props.putIfAbsent("rate", 10f);
        props.putIfAbsent("maxParticles", 512);
        if (parentId != null && !parentId.isEmpty()) {
            props.put("parent", parentId);
        }
        payload.put("properties", props);
        SceneSessionManager.getInstance().submitEdit("create", payload);
        SceneEditorDiagnostics.log("Spawn particle emitter " + label);
    }

    public void spawnMarker(SceneSessionManager session, String parentId) {
        String label = markerNameBuffer.get().isBlank() ? generateMarkerName() : markerNameBuffer.get();
        String objectId = "marker-" + System.currentTimeMillis();
        float[] position = resolveSpawnPosition(newObjectPosition);
        if (markerSnapToggle.get()) {
            float step = Math.max(0.05f, markerSnapValue.get());
            position[0] = Math.round(position[0] / step) * step;
            position[1] = Math.round(position[1] / step) * step;
            position[2] = Math.round(position[2] / step) * step;
        }
        Map<String, Object> payload = new ConcurrentHashMap<>();
        payload.put("type", "marker");
        payload.put("id", objectId);
        Map<String, Object> props = new ConcurrentHashMap<>();
        props.put("label", label);
        props.put("name", label);
        props.put("position", vectorToMap(position));
        props.putIfAbsent("rotation", rotationMap(activeRotation));
        props.putIfAbsent("rotationQuat", quaternionMap(activeRotationQuat));
        props.putIfAbsent("scale", vectorToMap(newObjectScale));
        if (parentId != null && !parentId.isEmpty()) {
            props.put("parent", parentId);
        }
        payload.put("properties", props);
        session.submitEdit("create", payload);
        session.forceRefresh();
        SceneEditorDiagnostics.log("Created marker '" + label + "'");
    }

    public void spawnFakePlayer(SceneSessionManager session, String parentId) {
        Map<String, Object> payload = new ConcurrentHashMap<>();
        payload.put("type", "player_model");
        payload.put("id", "fakeplayer-" + System.currentTimeMillis());
        Map<String, Object> props = new ConcurrentHashMap<>();
        String label = fakePlayerLabelBuffer.get().isBlank() ? "Fake Player" : fakePlayerLabelBuffer.get();
        String skin = fakePlayerSkinBuffer.get().isBlank() ? DEFAULT_FAKE_PLAYER_SKIN : fakePlayerSkinBuffer.get();
        props.put("label", label);
        props.put("skinUrl", skin);
        props.put("position", vectorToMap(resolveSpawnPosition(newObjectPosition)));
        props.putIfAbsent("rotation", rotationMap(activeRotation));
        props.putIfAbsent("scale", vectorToMap(newObjectScale));
        props.put("physicsEnabled", fakePlayerPhysics.get());
        props.put("sneaking", fakePlayerSneak.get());
        props.put("sprinting", fakePlayerSprint.get());
        props.put("swinging", fakePlayerSwing.get());
        props.put("usingItem", fakePlayerUse.get());
        props.put("width", (double) fakePlayerDimensions[0]);
        props.put("height", (double) fakePlayerDimensions[1]);
        props.put("pathSpeed", (double) fakePlayerPathSpeed[0]);
        props.put("pathLoop", fakePlayerPathLoop.get());
        props.put("pathPingPong", fakePlayerPathPingPong.get());
        if (!fakePlayerPathPoints.isEmpty()) {
            List<Map<String, Object>> pathList = new ArrayList<>();
            for (float[] p : fakePlayerPathPoints) {
                pathList.add(vectorToMap(p));
            }
            props.put("path", pathList);
        }
        if (parentId != null && !parentId.isEmpty()) {
            props.put("parent", parentId);
        }
        payload.put("properties", props);
        session.submitEdit("create", payload);
        session.forceRefresh();
        SceneEditorDiagnostics.log("Spawned fake player '" + label + "'");
    }

    public void spawnCamera(SceneSessionManager session, String parentId) {
        Map<String, Object> payload = new ConcurrentHashMap<>();
        payload.put("type", "camera");
        payload.put("id", "camera-" + System.currentTimeMillis());
        Map<String, Object> props = new ConcurrentHashMap<>();
        String label = cameraLabelBuffer.get().isBlank() ? "Camera" : cameraLabelBuffer.get();
        props.put("label", label);
        props.put("position", vectorToMap(resolveSpawnPosition(newObjectPosition)));
        props.put("rotation", rotationMap(cameraRotationBuffer));
        props.put("fov", (double) cameraFov.get());
        props.put("near", (double) cameraNear.get());
        props.put("far", (double) cameraFar.get());
        props.put("rotationQuat", quaternionMap(activeRotationQuat));
        if (parentId != null && !parentId.isEmpty()) {
            props.put("parent", parentId);
        }
        payload.put("properties", props);
        session.submitEdit("create", payload);
        SceneEditorDiagnostics.log("Spawned camera '" + label + "'");
        LOGGER.info("SceneEditorOverlay spawnCamera payload={}", payload);
    }

    public String generateMarkerName() {
        return "Marker " + markerCounter++;
    }

    public String getDefaultFakePlayerSkin() {
        return DEFAULT_FAKE_PLAYER_SKIN;
    }

    public void resetCameraBuffers() {
        cameraLabelBuffer.set("Camera");
        cameraPositionBuffer[0] = cameraPositionBuffer[1] = cameraPositionBuffer[2] = 0f;
        cameraRotationBuffer[0] = cameraRotationBuffer[1] = cameraRotationBuffer[2] = 0f;
        cameraFov.set(70f);
        cameraNear.set(0.1f);
        cameraFar.set(128f);
    }

    public String[] getKnownPlayerAnimations() {
        long now = System.currentTimeMillis();
        if (cachedPlayerAnimations.length == 0 || now - lastAnimationFetch > 5000L) {
            cachedPlayerAnimations = fetchPlayerAnimations();
            lastAnimationFetch = now;
        }
        return cachedPlayerAnimations;
    }

    private String[] fetchPlayerAnimations() {
        try {
            Map<Identifier, ?> map = PlayerAnimResources.getAnimations();
            if (map != null && !map.isEmpty()) {
                return map.keySet().stream()
                        .map(Identifier::toString)
                        .sorted()
                        .toArray(String[]::new);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to fetch player animations", e);
        }
        return new String[0];
    }

    private boolean matchesHierarchyFilter(SceneObject object, String filter) {
        if (filter.isEmpty()) {
            return true;
        }
        String label = String.valueOf(object.getProperties().getOrDefault("label", object.getId())).toLowerCase();
        return label.contains(filter) || object.getId().toLowerCase().contains(filter);
    }

    private boolean matchesHierarchyFilterRecursive(SceneObject object, Map<String, List<SceneObject>> tree, String filter) {
        if (matchesHierarchyFilter(object, filter)) {
            return true;
        }
        for (SceneObject child : tree.getOrDefault(object.getId(), java.util.List.of())) {
            if (matchesHierarchyFilterRecursive(child, tree, filter)) {
                return true;
            }
        }
        return false;
    }

    private void finalizeGizmoChange() {
        if (!gizmoManipulating) {
            return;
        }
        history.commitContinuousChange();
        gizmoManipulating = false;
        gizmoObjectId = null;
    }

    private void updateRuntimeTransform(RuntimeObject runtimeObject, float[] translation, float[] rotation, float[] scale) {
        if (runtimeObject == null) {
            return;
        }

        com.moud.api.math.Vector3 position = new com.moud.api.math.Vector3(translation[0], translation[1], translation[2]);
        com.moud.api.math.Vector3 scaleVec = new com.moud.api.math.Vector3(scale[0], scale[1], scale[2]);

        com.moud.api.math.Quaternion quat = new com.moud.api.math.Quaternion(
                activeRotationQuat[0],
                activeRotationQuat[1],
                activeRotationQuat[2],
                activeRotationQuat[3]
        );

        if (runtimeObject.getType() == RuntimeObjectType.MODEL) {
            MoudPackets.UpdateRuntimeModelPacket packet = new MoudPackets.UpdateRuntimeModelPacket(
                    runtimeObject.getRuntimeId(),
                    position,
                    quat,
                    scaleVec
            );
            com.moud.client.network.ClientPacketWrapper.sendToServer(packet);
        } else if (runtimeObject.getType() == RuntimeObjectType.DISPLAY) {
            MoudPackets.UpdateRuntimeDisplayPacket packet = new MoudPackets.UpdateRuntimeDisplayPacket(
                    runtimeObject.getRuntimeId(),
                    position,
                    quat,
                    scaleVec
            );
            com.moud.client.network.ClientPacketWrapper.sendToServer(packet);
        } else if (runtimeObject.getType() == RuntimeObjectType.PLAYER && runtimeObject.getPlayerUuid() != null) {
            MoudPackets.UpdatePlayerTransformPacket packet = new MoudPackets.UpdatePlayerTransformPacket(
                    runtimeObject.getPlayerUuid(),
                    position,
                    null
            );
            com.moud.client.network.ClientPacketWrapper.sendToServer(packet);
        }
    }

    public void renderRuntimeList(String filterTerm) {
        renderRuntimeList(RuntimeObjectType.MODEL, "Models", filterTerm);
        renderRuntimeList(RuntimeObjectType.DISPLAY, "Displays", filterTerm);
        renderRuntimeList(RuntimeObjectType.PLAYER, "Players", filterTerm);
        renderRuntimeList(RuntimeObjectType.PLAYER_MODEL, "Fake Players", filterTerm);
        renderRuntimeList(RuntimeObjectType.PARTICLE_EMITTER, "Particle Emitters", filterTerm);
    }

}
