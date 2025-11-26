package com.moud.client.editor.ui;

import com.moud.client.editor.EditorModeManager;
import com.moud.client.editor.assets.EditorAssetCatalog;
import com.moud.client.editor.camera.EditorCameraController;
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
import com.moud.client.editor.ui.panel.BlueprintToolsPanel;
import com.moud.client.editor.ui.panel.DiagnosticsPanel;
import com.moud.client.editor.ui.panel.ExplorerPanel;
import com.moud.client.editor.ui.panel.InspectorPanel;
import com.moud.client.editor.ui.panel.RibbonBar;
import com.moud.client.editor.ui.panel.ScriptViewerPanel;
import com.moud.client.editor.ui.panel.ViewportToolbar;
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
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

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
    private final float[] newObjectPosition = new float[]{0f, 65f, 0f};
    private final float[] newObjectScale = new float[]{1f, 1f, 1f};
    private String selectedSceneObjectId;
    private String selectedRuntimeId;
    private final float[] activeMatrix = identity();
    private final float[] activeTranslation = new float[3];
    private final float[] activeRotation = new float[3];
    private final float[] activeScale = new float[]{1f, 1f, 1f};
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
    private boolean showDiagnostics;
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
            if (selected != null) {
                updateTransform(session, selected, activeTranslation, activeRotation, activeScale, false);
            } else if (selectedRuntime != null) {
                updateRuntimeTransform(selectedRuntime, activeTranslation, activeRotation, activeScale);
            }
        } else if (gizmoManipulating) {
            finalizeGizmoChange();
        }
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
        RaycastPicker.getInstance().clearSelection();
        Map<String, Object> props = object.getProperties();
        extractVector(props.getOrDefault("position", null), activeTranslation);
        extractVector(props.getOrDefault("scale", null), activeScale);
        extractEuler(props.getOrDefault("rotation", null), activeRotation);
        composeMatrix(activeTranslation, activeRotation, activeScale, activeMatrix);
        EditorCameraController.getInstance().initialSelectionChanged(object, false);
        inspectorPanel.onSelectionChanged(object);
    }

    public void selectRuntimeObject(RuntimeObject runtimeObject) {
        if (runtimeObject == null) {
            return;
        }
        finalizeGizmoChange();
        selectedSceneObjectId = null;
        selectedRuntimeId = runtimeObject.getObjectId();
        RaycastPicker.getInstance().setSelectedObject(runtimeObject);
        Vec3d pos = runtimeObject.getPosition();
        activeTranslation[0] = (float) pos.x;
        activeTranslation[1] = (float) pos.y;
        activeTranslation[2] = (float) pos.z;
        Vec3d rot = runtimeObject.getRotation();
        activeRotation[0] = (float) rot.x;
        activeRotation[1] = (float) rot.y;
        activeRotation[2] = (float) rot.z;
        Vec3d scl = runtimeObject.getScale();
        activeScale[0] = (float) scl.x;
        activeScale[1] = (float) scl.y;
        activeScale[2] = (float) scl.z;
        composeMatrix(activeTranslation, activeRotation, activeScale, activeMatrix);
        inspectorPanel.onRuntimeSelection(runtimeObject);
    }

    public void updateTransform(SceneSessionManager session, SceneObject selected, float[] translation, float[] rotation, float[] scale, boolean discreteChange) {
        if (selected == null) {
            return;
        }
        composeMatrix(translation, rotation, scale, activeMatrix);
        if (discreteChange) {
            Map<String, Object> before = history.snapshot(selected);
            session.submitTransformUpdate(selected.getId(), translation, rotation, scale);
            Map<String, Object> after = new ConcurrentHashMap<>(before);
            after.put("position", vectorToMap(translation));
            after.put("rotation", rotationMap(rotation));
            after.put("scale", vectorToMap(scale));
            history.recordDiscreteChange(selected.getId(), before, after);
        } else {
            session.submitTransformUpdate(selected.getId(), translation, rotation, scale);
            history.updateContinuousChange(selected);
        }
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

    private static void composeMatrix(float[] translation, float[] rotationDegrees, float[] scale, float[] outMatrix) {
        Matrix4f matrix = new Matrix4f()
                .translation(translation[0], translation[1], translation[2])
                .rotateXYZ(
                        (float) Math.toRadians(rotationDegrees[0]),
                        (float) Math.toRadians(rotationDegrees[1]),
                        (float) Math.toRadians(rotationDegrees[2])
                )
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

    private static float[] extractEuler(Object value, float[] fallback) {
        if (value instanceof Map<?,?> map) {
            Object pitch = map.get("pitch");
            Object yawObj = map.get("yaw");
            Object roll = map.get("roll");
            if (pitch != null) fallback[0] = toFloat(pitch);
            if (yawObj != null) fallback[1] = toFloat(yawObj);
            if (roll != null) fallback[2] = toFloat(roll);
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

    private void spawnMarker(SceneSessionManager session, String parentId) {
        String label = markerNameBuffer.get().isBlank() ? generateMarkerName() : markerNameBuffer.get();
        float[] position = resolveSpawnPosition(newObjectPosition);
        if (markerSnapToggle.get()) {
            float step = Math.max(0.05f, markerSnapValue.get());
            position[0] = Math.round(position[0] / step) * step;
            position[1] = Math.round(position[1] / step) * step;
            position[2] = Math.round(position[2] / step) * step;
        }
        Map<String, Object> payload = new ConcurrentHashMap<>();
        payload.put("type", "marker");
        Map<String, Object> props = new ConcurrentHashMap<>();
        props.put("label", label);
        props.put("name", label);
        props.put("position", vectorToMap(position));
        if (parentId != null && !parentId.isEmpty()) {
            props.put("parent", parentId);
        }
        payload.put("properties", props);
        session.submitEdit("create", payload);
        SceneEditorDiagnostics.log("Created marker '" + label + "'");
    }

    public void spawnFakePlayer(SceneSessionManager session, String parentId) {
        Map<String, Object> payload = new ConcurrentHashMap<>();
        payload.put("type", "player_model");
        Map<String, Object> props = new ConcurrentHashMap<>();
        String label = fakePlayerLabelBuffer.get().isBlank() ? "Fake Player" : fakePlayerLabelBuffer.get();
        String skin = fakePlayerSkinBuffer.get().isBlank() ? DEFAULT_FAKE_PLAYER_SKIN : fakePlayerSkinBuffer.get();
        props.put("label", label);
        props.put("skinUrl", skin);
        props.put("autoAnimation", true);
        props.put("loopAnimation", true);
        props.put("animationDuration", 2000);
        props.put("animationOverride", "");
        props.put("position", vectorToMap(resolveSpawnPosition(newObjectPosition)));
        props.putIfAbsent("rotation", rotationMap(activeRotation));
        props.putIfAbsent("scale", vectorToMap(newObjectScale));
        if (parentId != null && !parentId.isEmpty()) {
            props.put("parent", parentId);
        }
        payload.put("properties", props);
        session.submitEdit("create", payload);
        SceneEditorDiagnostics.log("Spawned fake player '" + label + "'");
    }

    public String generateMarkerName() {
        return "Marker " + markerCounter++;
    }

    public String getDefaultFakePlayerSkin() {
        return DEFAULT_FAKE_PLAYER_SKIN;
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

        float pitch = (float) Math.toRadians(rotation[0]);
        float yaw = (float) Math.toRadians(rotation[1]);
        float roll = (float) Math.toRadians(rotation[2]);
        com.moud.api.math.Quaternion quat = com.moud.api.math.Quaternion.fromEuler(pitch, yaw, roll);

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
    }

}
