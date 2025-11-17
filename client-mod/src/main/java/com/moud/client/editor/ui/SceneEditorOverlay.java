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
import com.moud.network.MoudPackets;
import imgui.ImGui;
import imgui.extension.imguizmo.ImGuizmo;
import imgui.extension.imguizmo.flag.Mode;
import imgui.extension.imguizmo.flag.Operation;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiTableFlags;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImFloat;
import imgui.type.ImInt;
import imgui.type.ImString;
import com.moud.client.editor.ui.WorldViewCapture;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
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
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class SceneEditorOverlay {
    private static final SceneEditorOverlay INSTANCE = new SceneEditorOverlay();
    private static final long MAX_BLOCK_CAPTURE_BLOCKS = 10000;

    private final ImString inspectorLabel = new ImString("", 128);
    private final ImString markerNameBuffer = new ImString("Marker", 64);
    private final float[] newObjectPosition = new float[]{0f, 65f, 0f};
    private final float[] newObjectScale = new float[]{1f, 1f, 1f};
    private final ImString displayContentBuffer = new ImString("", 256);
    private final ImString displayTypeBuffer = new ImString("image", 16);
    private final ImBoolean displayLoopToggle = new ImBoolean(true);
    private final ImBoolean displayPlayingToggle = new ImBoolean(true);
    private final ImFloat displayFrameRateValue = new ImFloat(24f);
    private final ImInt lightTypeIndex = new ImInt(0);
    private final ImFloat lightBrightnessValue = new ImFloat(1f);
    private final ImFloat lightRadiusValue = new ImFloat(6f);
    private final ImFloat lightWidthValue = new ImFloat(4f);
    private final ImFloat lightHeightValue = new ImFloat(4f);
    private final ImFloat lightDistanceValue = new ImFloat(8f);
    private final ImFloat lightAngleValue = new ImFloat(45f);
    private final float[] lightColorValue = new float[]{1f, 1f, 1f};
    private final float[] lightDirectionValue = new float[]{0f, -1f, 0f};

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
    private final ImBoolean snapCheckbox = new ImBoolean(false);
    private final float[] snapValues = new float[]{0.5f, 0.5f, 0.5f};
    private boolean gizmoManipulating;
    private String gizmoObjectId;

    private static final String PAYLOAD_HIERARCHY = "MoudHierarchyObject";
    private static final String PAYLOAD_ASSET = "MoudAssetDefinition";
    private static final String[] DISPLAY_CONTENT_TYPES = {"image", "video", "sequence"};
    private static final String[] LIGHT_TYPE_LABELS = {"Point", "Area"};
    private final ImString hierarchyFilter = new ImString(64);
    private final ImString runtimeFilter = new ImString(64);
    private final ImString assetFilter = new ImString(64);
    private final ImString modelPopupFilter = new ImString(64);
    private final ImString displayPopupFilter = new ImString(64);
    private final ImString lightPopupFilter = new ImString(64);
    private final ImString modelPathBuffer = new ImString("", 192);
    private final ImString texturePathBuffer = new ImString("", 192);
    private final float[] markerPositionBuffer = new float[]{0f, 0f, 0f};
    private final ImBoolean markerSnapToggle = new ImBoolean(false);
    private final ImFloat markerSnapValue = new ImFloat(0.5f);

    private Map<String, List<SceneObject>> cachedHierarchy = new ConcurrentHashMap<>();
    private long lastHierarchyBuild = 0;
    private static final long HIERARCHY_CACHE_MS = 100;

    private boolean showHierarchy = true;
    private boolean showInspector = true;
    private boolean showAssets = true;
    private boolean showConsole = true;
    private boolean showBlueprints = true;

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

    private SceneEditorOverlay() {}

    public static SceneEditorOverlay getInstance() {
        return INSTANCE;
    }

    public void render() {
        if (!EditorModeManager.getInstance().isActive()) {
            return;
        }
        SceneSessionManager session = SceneSessionManager.getInstance();
        float displayWidth = ImGui.getIO().getDisplaySizeX();
        float displayHeight = ImGui.getIO().getDisplaySizeY();
        float hierarchyWidth = displayWidth * 0.2f;
        float inspectorWidth = displayWidth * 0.25f;
        float bottomHeight = displayHeight * 0.25f;
        float mainHeight = displayHeight - bottomHeight;

        if (showHierarchy) {
            renderHierarchyWindow(session, 0f, 0f, hierarchyWidth, mainHeight);
        }
        if (showInspector) {
            renderInspectorWindow(session, displayWidth - inspectorWidth, 0f, inspectorWidth, mainHeight);
        }
        float assetsWidth = displayWidth * 0.35f;
        float consoleWidth = displayWidth * 0.35f;
        float blueprintWidth = displayWidth - assetsWidth - consoleWidth;
        if (showAssets) {
            renderAssetsWindow(0f, mainHeight, assetsWidth, bottomHeight);
        }
        if (showBlueprints) {
            renderBlueprintWindow(session, assetsWidth, mainHeight, blueprintWidth, bottomHeight);
        }
        if (showConsole) {
            renderConsoleWindow(assetsWidth + blueprintWidth, mainHeight, consoleWidth, bottomHeight);
        }
        renderTransformToolbar(hierarchyWidth + 16f, 16f);
    }

    private void renderHierarchyWindow(SceneSessionManager session, float x, float y, float width, float height) {
        if (beginAnchoredWindow("Hierarchy", x, y, width, height)) {
            renderHierarchyToolbar(session);
            ImGui.separator();
            if (ImGui.beginTabBar("hierarchy-tabs")) {
                if (ImGui.beginTabItem("Scene")) {
                    renderSceneHierarchy(session);
                    ImGui.endTabItem();
                }
                if (ImGui.beginTabItem("Runtime")) {
                    renderRuntimeHierarchy();
                    ImGui.endTabItem();
                }
                ImGui.endTabBar();
            }
        }
        ImGui.end();
    }

    private void renderHierarchyToolbar(SceneSessionManager session) {
        if (ImGui.button("+ Empty")) {
            spawnEmptyObject(session, null);
        }
        ImGui.sameLine();
        boolean haveModels = hasAssetsOfType("model");
        if (!haveModels) ImGui.beginDisabled();
        if (ImGui.button("Add Model")) {
            ImGui.openPopup("hierarchy_add_model_popup");
        }
        if (!haveModels) ImGui.endDisabled();
        ImGui.sameLine();
        boolean haveDisplays = hasAssetsOfType("display");
        if (!haveDisplays) ImGui.beginDisabled();
        if (ImGui.button("Add Display")) {
            ImGui.openPopup("hierarchy_add_display_popup");
        }
        if (!haveDisplays) ImGui.endDisabled();
        ImGui.sameLine();
        boolean haveLights = hasAssetsOfType("light");
        if (!haveLights) ImGui.beginDisabled();
        if (ImGui.button("Add Light")) {
            ImGui.openPopup("hierarchy_add_light_popup");
        }
        if (!haveLights) ImGui.endDisabled();
        ImGui.sameLine();
        if (ImGui.button("Add Marker")) {
            markerNameBuffer.set(generateMarkerName());
            ImGui.openPopup("hierarchy_add_marker_popup");
        }
        ImGui.sameLine();
        if (ImGui.button("Refresh")) {
            session.forceRefresh();
            EditorAssetCatalog.getInstance().forceRefresh();
        }
        ImGui.sameLine();
        ImGui.textDisabled("Drag assets into the tree to parent");
        renderAssetPopup("hierarchy_add_model_popup", "model", modelPopupFilter, null);
        renderAssetPopup("hierarchy_add_display_popup", "display", displayPopupFilter, null);
        renderAssetPopup("hierarchy_add_light_popup", "light", lightPopupFilter, null);
        renderMarkerPopup("hierarchy_add_marker_popup", session, null);
    }

    private void renderSceneHierarchy(SceneSessionManager session) {
        ImGui.setNextItemWidth(-1);
        ImGui.inputTextWithHint("##hierarchy_search", "Search scene...", hierarchyFilter);
        ImGui.beginChild("hierarchy-scroll", 0, ImGui.getContentRegionAvailY(), true);
        Map<String, List<SceneObject>> tree = getCachedHierarchy(session);
        List<SceneObject> roots = tree.getOrDefault("", List.of());
        for (SceneObject root : roots) {
            renderHierarchyNode(session, root, tree);
        }
        if (ImGui.beginDragDropTarget()) {
            handleHierarchyDrop(null);
            ImGui.endDragDropTarget();
        }
        ImGui.endChild();
    }

    private void renderRuntimeHierarchy() {
        ImGui.setNextItemWidth(-1);
        ImGui.inputTextWithHint("##runtime_search", "Search runtime objects...", runtimeFilter);
        ImGui.beginChild("runtime-scroll", 0, ImGui.getContentRegionAvailY(), true);
        String filter = runtimeFilter.get();
        renderRuntimeList(RuntimeObjectType.MODEL, "Models", filter);
        renderRuntimeList(RuntimeObjectType.DISPLAY, "Displays", filter);
        renderRuntimeList(RuntimeObjectType.PLAYER, "Players", filter);
        ImGui.endChild();
    }

    private void renderInspectorWindow(SceneSessionManager session, float x, float y, float width, float height) {
        if (beginAnchoredWindow("Inspector", x, y, width, height)) {
            SceneObject selected = getSelected(session);
            RuntimeObject selectedRuntime = getSelectedRuntime();

            if (selected == null && selectedRuntime == null) {
                ImGui.textDisabled("Select an object to inspect its properties.");
                ImGui.end();
                return;
            }

            if (selectedRuntime != null) {
                renderRuntimeObjectInspector(selectedRuntime);
                ImGui.end();
                return;
            }
            ImGui.text("Object ID: " + selected.getId());
            ImGui.sameLine();
            if (ImGui.button("Focus##focus_object")) {
                EditorCameraController.getInstance().focusSelection(selected);
            }
            ImGui.text("Type: " + selected.getType());
            String parent = parentIdOf(selected);
            ImGui.text(parent.isEmpty() ? "Parent: <scene root>" : "Parent: " + parent);
            Map<String, Object> props = new ConcurrentHashMap<>(selected.getProperties());

            if (ImGui.inputText("Label", inspectorLabel)) {
                Map<String, Object> before = history.snapshot(selected);
                props.put("label", inspectorLabel.get());
                session.submitPropertyUpdate(selected.getId(), props);
                history.recordDiscreteChange(selected.getId(), before, new ConcurrentHashMap<>(props));
            }

            ImGui.textDisabled("Transform");
            ImGui.separator();
            float[] position = extractVector(props.get("position"), activeTranslation);
            if (ImGui.dragFloat3("Position", position, 0.1f)) {
                updateTransform(session, selected, position, activeRotation, activeScale, true);
            }

            if (ImGui.dragFloat3("Rotation", activeRotation, 0.5f)) {
                updateTransform(session, selected, activeTranslation, activeRotation, activeScale, true);
            }

            float[] scale = extractVector(props.get("scale"), activeScale);
            if (ImGui.dragFloat3("Scale", scale, 0.1f)) {
                updateTransform(session, selected, activeTranslation, activeRotation, scale, true);
            }

            if ("model".equalsIgnoreCase(selected.getType())) {
                ImGui.textDisabled("Model");
                ImGui.separator();
                if (ImGui.inputText("Model Path", modelPathBuffer)) {
                    Map<String, Object> update = new ConcurrentHashMap<>();
                    update.put("modelPath", modelPathBuffer.get());
                    session.submitPropertyUpdate(selected.getId(), update);
                }
                if (ImGui.inputText("Texture", texturePathBuffer)) {
                    Map<String, Object> update = new ConcurrentHashMap<>();
                    update.put("texture", texturePathBuffer.get());
                    session.submitPropertyUpdate(selected.getId(), update);
                }
            } else if ("display".equalsIgnoreCase(selected.getType())) {
                renderDisplayProperties(session, selected);
            } else if ("light".equalsIgnoreCase(selected.getType())) {
                renderLightProperties(session, selected);
            } else if ("marker".equalsIgnoreCase(selected.getType())) {
                renderMarkerProperties(session, selected);
            }

            ImGui.separator();
            ImGui.textDisabled("All Properties");
            if (ImGui.beginTable("inspector_props", 2, ImGuiTableFlags.RowBg | ImGuiTableFlags.BordersOuter)) {
                ImGui.tableSetupColumn("Key");
                ImGui.tableSetupColumn("Value");
                for (Map.Entry<String, Object> entry : props.entrySet()) {
                    ImGui.tableNextRow();
                    ImGui.tableSetColumnIndex(0);
                    ImGui.textUnformatted(entry.getKey());
                    ImGui.tableSetColumnIndex(1);
                    ImGui.textWrapped(String.valueOf(entry.getValue()));
                }
                ImGui.endTable();
            }
        }
        ImGui.end();
    }

    private void renderAssetsWindow(float x, float y, float width, float height) {
        if (beginAnchoredWindow("Assets", x, y, width, height)) {
            List<MoudPackets.EditorAssetDefinition> assets = EditorAssetCatalog.getInstance().getAssets();
            ImGui.setNextItemWidth(-1);
            ImGui.inputTextWithHint("##asset_search", "Search assets...", assetFilter);
            ImGui.separator();
            if (assets.isEmpty()) {
                ImGui.textDisabled("No assets available. Trigger a server rescan.");
            } else {
                Map<String, List<MoudPackets.EditorAssetDefinition>> groups = groupAssets(assets);
                for (Map.Entry<String, List<MoudPackets.EditorAssetDefinition>> group : groups.entrySet()) {
                    if (ImGui.collapsingHeader(group.getKey(), ImGuiTreeNodeFlags.DefaultOpen)) {
                        for (MoudPackets.EditorAssetDefinition entry : group.getValue()) {
                            if (!assetMatchesFilter(entry)) {
                                continue;
                            }
                            if (ImGui.selectable(entry.label() + "##asset_" + entry.id())) {
                                spawnAsset(entry);
                            }
                            if (ImGui.beginDragDropSource()) {
                                byte[] idBytes = entry.id().getBytes(java.nio.charset.StandardCharsets.UTF_8);
                                ImGui.setDragDropPayload(PAYLOAD_ASSET, idBytes);
                                ImGui.text("Place " + entry.label());
                                ImGui.endDragDropSource();
                            }
                        }
                    }
                }
            }
        }
        ImGui.end();
    }

    private void renderDisplayProperties(SceneSessionManager session, SceneObject selected) {
        ImGui.textDisabled("Display");
        if (ImGui.beginCombo("Content Type", displayTypeBuffer.get())) {
            for (String option : DISPLAY_CONTENT_TYPES) {
                boolean selectedType = displayTypeBuffer.get().equalsIgnoreCase(option);
                if (ImGui.selectable(option, selectedType)) {
                    displayTypeBuffer.set(option);
                    Map<String, Object> update = new ConcurrentHashMap<>();
                    update.put("displayType", option);
                    session.submitPropertyUpdate(selected.getId(), update);
                }
                if (selectedType) {
                    ImGui.setItemDefaultFocus();
                }
            }
            ImGui.endCombo();
        }
        if (ImGui.inputText("Source", displayContentBuffer)) {
            Map<String, Object> update = new ConcurrentHashMap<>();
            update.put("displayContent", displayContentBuffer.get());
            session.submitPropertyUpdate(selected.getId(), update);
        }
        if (ImGui.checkbox("Loop Playback", displayLoopToggle)) {
            Map<String, Object> update = new ConcurrentHashMap<>();
            update.put("loop", displayLoopToggle.get());
            session.submitPropertyUpdate(selected.getId(), update);
        }
        if (ImGui.checkbox("Auto Play", displayPlayingToggle)) {
            Map<String, Object> update = new ConcurrentHashMap<>();
            update.put("playing", displayPlayingToggle.get());
            session.submitPropertyUpdate(selected.getId(), update);
        }
        if (ImGui.dragFloat("Frame Rate", displayFrameRateValue.getData(), 0.5f, 0f, 120f)) {
            Map<String, Object> update = new ConcurrentHashMap<>();
            update.put("frameRate", displayFrameRateValue.get());
            session.submitPropertyUpdate(selected.getId(), update);
        }
    }

    private void renderLightProperties(SceneSessionManager session, SceneObject selected) {
        ImGui.textDisabled("Light");
        int typeIndex = Math.max(0, Math.min(lightTypeIndex.get(), LIGHT_TYPE_LABELS.length - 1));
        if (ImGui.beginCombo("Light Type", LIGHT_TYPE_LABELS[typeIndex])) {
            for (int i = 0; i < LIGHT_TYPE_LABELS.length; i++) {
                boolean active = lightTypeIndex.get() == i;
                if (ImGui.selectable(LIGHT_TYPE_LABELS[i], active)) {
                    lightTypeIndex.set(i);
                    Map<String, Object> update = new ConcurrentHashMap<>();
                    update.put("lightType", i == 1 ? "area" : "point");
                    session.submitPropertyUpdate(selected.getId(), update);
                }
                if (active) {
                    ImGui.setItemDefaultFocus();
                }
            }
            ImGui.endCombo();
        }
        if (ImGui.colorEdit3("Color", lightColorValue)) {
            Map<String, Object> update = new ConcurrentHashMap<>();
            update.put("color", colorToMap(lightColorValue));
            session.submitPropertyUpdate(selected.getId(), update);
        }
        if (ImGui.dragFloat("Brightness", lightBrightnessValue.getData(), 0.05f, 0f, 10f)) {
            Map<String, Object> update = new ConcurrentHashMap<>();
            update.put("brightness", lightBrightnessValue.get());
            session.submitPropertyUpdate(selected.getId(), update);
        }
        if (lightTypeIndex.get() == 0) {
            if (ImGui.dragFloat("Radius", lightRadiusValue.getData(), 0.1f, 0.1f, 64f)) {
                Map<String, Object> update = new ConcurrentHashMap<>();
                update.put("radius", lightRadiusValue.get());
                session.submitPropertyUpdate(selected.getId(), update);
            }
        } else {
            if (ImGui.dragFloat("Width", lightWidthValue.getData(), 0.1f, 0.1f, 32f)) {
                Map<String, Object> update = new ConcurrentHashMap<>();
                update.put("width", lightWidthValue.get());
                session.submitPropertyUpdate(selected.getId(), update);
            }
            if (ImGui.dragFloat("Height", lightHeightValue.getData(), 0.1f, 0.1f, 32f)) {
                Map<String, Object> update = new ConcurrentHashMap<>();
                update.put("height", lightHeightValue.get());
                session.submitPropertyUpdate(selected.getId(), update);
            }
            if (ImGui.dragFloat("Distance", lightDistanceValue.getData(), 0.1f, 0.1f, 64f)) {
                Map<String, Object> update = new ConcurrentHashMap<>();
                update.put("distance", lightDistanceValue.get());
                session.submitPropertyUpdate(selected.getId(), update);
            }
            if (ImGui.dragFloat("Angle", lightAngleValue.getData(), 0.5f, 1f, 120f)) {
                Map<String, Object> update = new ConcurrentHashMap<>();
                update.put("angle", lightAngleValue.get());
                session.submitPropertyUpdate(selected.getId(), update);
            }
            if (ImGui.dragFloat3("Direction", lightDirectionValue, 0.05f)) {
                Map<String, Object> update = new ConcurrentHashMap<>();
                update.put("direction", directionToMap(lightDirectionValue));
                session.submitPropertyUpdate(selected.getId(), update);
            }
        }
    }

    private void renderMarkerProperties(SceneSessionManager session, SceneObject selected) {
        Map<String, Object> props = new ConcurrentHashMap<>(selected.getProperties());
        Object label = props.getOrDefault("label", selected.getId());
        markerNameBuffer.set(String.valueOf(label));
        if (ImGui.inputText("Marker Name", markerNameBuffer)) {
            Map<String, Object> update = new ConcurrentHashMap<>();
            update.put("label", markerNameBuffer.get());
            update.put("name", markerNameBuffer.get());
            session.submitPropertyUpdate(selected.getId(), update);
        }
        extractVector(props.getOrDefault("position", null), markerPositionBuffer);
        if (ImGui.dragFloat3("Marker Position", markerPositionBuffer, 0.05f)) {
            Map<String, Object> update = new ConcurrentHashMap<>();
            update.put("position", vectorToMap(markerPositionBuffer));
            session.submitPropertyUpdate(selected.getId(), update);
        }
    }

    private void renderConsoleWindow(float x, float y, float width, float height) {
        if (beginAnchoredWindow("Console", x, y, width, height)) {
            if (ImGui.button("Clear")) {
                SceneEditorDiagnostics.clear();
            }
            ImGui.sameLine();
            ImGui.textDisabled("Latest events");
            ImGui.separator();
            ImGui.beginChild("console-scroll", 0, 0, true);
            List<String> lines = SceneEditorDiagnostics.snapshot();
            for (String line : lines) {
                ImGui.textUnformatted(line);
            }
            if (ImGui.getScrollY() >= ImGui.getScrollMaxY() - 4f) {
                ImGui.setScrollHereY(1.0f);
            }
            ImGui.endChild();
        }
        ImGui.end();
    }

    private void renderBlueprintWindow(SceneSessionManager session, float x, float y, float width, float height) {
        if (!beginAnchoredWindow("Blueprints", x, y, width, height)) {
            ImGui.end();
            return;
        }
        ImGui.textDisabled("Region Selection");
        ImGui.separator();
        if (ImGui.button("Pick Corner A")) {
            BlueprintCornerSelector.getInstance().beginSelection(BlueprintCornerSelector.Corner.A, pos -> applyPickedCorner(BlueprintCornerSelector.Corner.A, pos));
        }
        ImGui.sameLine();
        if (ImGui.button("Pick Corner B")) {
            BlueprintCornerSelector.getInstance().beginSelection(BlueprintCornerSelector.Corner.B, pos -> applyPickedCorner(BlueprintCornerSelector.Corner.B, pos));
        }
        ImGui.sameLine();
        if (ImGui.button("Clear Region")) {
            regionASet = false;
            regionBSet = false;
            BlueprintCornerSelector.getInstance().cancel();
        }
        if (BlueprintCornerSelector.getInstance().isPicking()) {
            ImGui.sameLine();
            ImGui.textColored(1f, 0.85f, 0.35f, 1f, "Picking corner " + BlueprintCornerSelector.getInstance().getPendingCorner());
        }
        if (regionASet) {
            ImGui.dragFloat3("Corner A", regionCornerA, 0.1f);
        } else {
            ImGui.textDisabled("Corner A not set");
        }
        if (regionBSet) {
            ImGui.dragFloat3("Corner B", regionCornerB, 0.1f);
        } else {
            ImGui.textDisabled("Corner B not set");
        }
        Box regionBox = buildRegionBox();
        if (regionBox != null) {
            double lenX = regionBox.maxX - regionBox.minX;
            double lenY = regionBox.maxY - regionBox.minY;
            double lenZ = regionBox.maxZ - regionBox.minZ;
            ImGui.text("Size: %.2f x %.2f x %.2f".formatted(lenX, lenY, lenZ));
            int objectCount = countObjectsInRegion(regionBox, false);
            int markerCount = countObjectsInRegion(regionBox, true);
            ImGui.text("Objects: " + objectCount + "  Markers: " + markerCount);
            ImGui.inputText("Blueprint Name", blueprintNameBuffer);
            if (ImGui.button("Export Blueprint")) {
                exportBlueprint(session, regionBox, blueprintNameBuffer.get());
            }
        } else {
            ImGui.textDisabled("Select two corners to enable export.");
        }
        ImGui.separator();
        ImGui.textDisabled("Preview");
        ImGui.inputTextWithHint("Blueprint File", "name without .json", blueprintPreviewName);
        if (ImGui.button("Load Preview")) {
            loadBlueprintPreview(blueprintPreviewName.get(), previewPosition);
        }
        ImGui.sameLine();
        if (ImGui.button("Hide Preview")) {
            BlueprintPreviewManager.getInstance().clear();
        }
        BlueprintPreviewManager.PreviewState preview = BlueprintPreviewManager.getInstance().getCurrent();
        if (preview != null) {
            if (ImGui.dragFloat3("Preview Position", previewPosition, 0.1f)) {
                BlueprintPreviewManager.getInstance().move(previewPosition);
                composeMatrix(previewPosition, previewRotation, previewScale, previewMatrix);
            }
            if (!previewGizmoActive) {
                if (ImGui.button("Attach Preview Gizmo")) {
                    previewGizmoActive = true;
                    composeMatrix(previewPosition, previewRotation, previewScale, previewMatrix);
                }
            } else {
                if (ImGui.button("Detach Preview Gizmo")) {
                    previewGizmoActive = false;
                }
                if (ImGui.radioButton("Move", previewGizmoOperation == Operation.TRANSLATE)) {
                    previewGizmoOperation = Operation.TRANSLATE;
                }
                ImGui.sameLine();
                if (ImGui.radioButton("Rotate", previewGizmoOperation == Operation.ROTATE)) {
                    previewGizmoOperation = Operation.ROTATE;
                }
            }
            if (previewGizmoActive) {
                renderPreviewGizmo();
            }
        } else {
            ImGui.textDisabled("No preview loaded.");
            previewGizmoActive = false;
        }
        ImGui.end();
    }

    private void renderTransformToolbar(float x, float y) {
        ImGui.setNextWindowPos(x, y, ImGuiCond.Always);
        ImGui.setNextWindowSize(400, 150, ImGuiCond.Always);
        int flags = ImGuiWindowFlags.NoDecoration | ImGuiWindowFlags.NoMove | ImGuiWindowFlags.NoResize;
        if (ImGui.begin("Gizmo Controls", flags)) {

            if (ImGui.checkbox("Hierarchy", new imgui.type.ImBoolean(showHierarchy))) {
                showHierarchy = !showHierarchy;
            }
            ImGui.sameLine();
            if (ImGui.checkbox("Inspector", new imgui.type.ImBoolean(showInspector))) {
                showInspector = !showInspector;
            }
            ImGui.sameLine();
            if (ImGui.checkbox("Assets", new imgui.type.ImBoolean(showAssets))) {
                showAssets = !showAssets;
            }
            ImGui.sameLine();
            if (ImGui.checkbox("Console", new imgui.type.ImBoolean(showConsole))) {
                showConsole = !showConsole;
            }
            ImGui.sameLine();
            if (ImGui.checkbox("Blueprints", new imgui.type.ImBoolean(showBlueprints))) {
                showBlueprints = !showBlueprints;
            }
            ImGui.separator();
            if (ImGui.radioButton("Translate", currentOperation == Operation.TRANSLATE)) {
                currentOperation = Operation.TRANSLATE;
            }
            ImGui.sameLine();
            if (ImGui.radioButton("Rotate", currentOperation == Operation.ROTATE)) {
                currentOperation = Operation.ROTATE;
            }
            ImGui.sameLine();
            if (ImGui.radioButton("Scale", currentOperation == Operation.SCALE)) {
                currentOperation = Operation.SCALE;
            }
            if (currentOperation != Operation.SCALE) {
                if (ImGui.radioButton("Local", gizmoMode == Mode.LOCAL)) {
                    gizmoMode = Mode.LOCAL;
                }
                ImGui.sameLine();
                if (ImGui.radioButton("World", gizmoMode == Mode.WORLD)) {
                    gizmoMode = Mode.WORLD;
                }
            }
            snapCheckbox.set(useSnap);
            if (ImGui.checkbox("Snap", snapCheckbox)) {
                useSnap = snapCheckbox.get();
            }
            if (useSnap) {
                if (currentOperation == Operation.ROTATE) {
                    ImFloat angle = new ImFloat(snapValues[0]);
                    if (ImGui.inputFloat("Angle Snap", angle)) {
                        float value = Math.max(1f, angle.get());
                        snapValues[0] = snapValues[1] = snapValues[2] = value;
                    }
                } else {
                    if (ImGui.inputFloat3("Snap XYZ", snapValues)) {
                        snapValues[0] = Math.max(0.1f, snapValues[0]);
                        snapValues[1] = Math.max(0.1f, snapValues[1]);
                        snapValues[2] = Math.max(0.1f, snapValues[2]);
                    }
                }
            }

            ImGui.separator();
            boolean undoDisabled = !history.canUndo();
            if (undoDisabled) ImGui.beginDisabled();
            if (ImGui.button("Undo  Ctrl+Z")) {
                history.undo();
            }
            if (undoDisabled) ImGui.endDisabled();

            ImGui.sameLine();
            boolean redoDisabled = !history.canRedo();
            if (redoDisabled) ImGui.beginDisabled();
            if (ImGui.button("Redo  Ctrl+Y")) {
                history.redo();
            }
            if (redoDisabled) ImGui.endDisabled();

            ImGui.textDisabled("Hold F to fly the camera");
        }
        ImGui.end();
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

    private boolean beginAnchoredWindow(String title, float x, float y, float width, float height) {
        ImGui.setNextWindowPos(x, y, ImGuiCond.Always);
        ImGui.setNextWindowSize(width, height, ImGuiCond.Always);
        int flags = ImGuiWindowFlags.NoCollapse | ImGuiWindowFlags.NoMove | ImGuiWindowFlags.NoResize;
        return ImGui.begin(title, flags);
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

    private void selectObject(SceneObject object) {
        finalizeGizmoChange();
        selectedSceneObjectId = object.getId();
        selectedRuntimeId = null;
        RaycastPicker.getInstance().clearSelection();
        Map<String, Object> props = object.getProperties();
        extractVector(props.getOrDefault("position", null), activeTranslation);
        extractVector(props.getOrDefault("scale", null), activeScale);
        extractEuler(props.getOrDefault("rotation", null), activeRotation);
        Object label = props.getOrDefault("label", object.getId());
        inspectorLabel.set(String.valueOf(label));
        Object modelPath = props.get("modelPath");
        modelPathBuffer.set(modelPath != null ? String.valueOf(modelPath) : "");
        Object texturePath = props.getOrDefault("texture", "");
        texturePathBuffer.set(String.valueOf(texturePath));
        composeMatrix(activeTranslation, activeRotation, activeScale, activeMatrix);
        EditorCameraController.getInstance().initialSelectionChanged(object, false);
        String type = object.getType() != null ? object.getType().toLowerCase() : "";
        if ("display".equals(type)) {
            displayContentBuffer.set(String.valueOf(props.getOrDefault("displayContent", "")));
            displayTypeBuffer.set(String.valueOf(props.getOrDefault("displayType", "image")));
            displayLoopToggle.set(boolValue(props.get("loop"), true));
            displayPlayingToggle.set(boolValue(props.get("playing"), true));
            displayFrameRateValue.set(toFloat(props.getOrDefault("frameRate", 24f)));
        } else if ("light".equals(type)) {
            lightTypeIndex.set("area".equalsIgnoreCase(String.valueOf(props.getOrDefault("lightType", "point"))) ? 1 : 0);
            lightBrightnessValue.set(toFloat(props.getOrDefault("brightness", 1f)));
            lightRadiusValue.set(toFloat(props.getOrDefault("radius", 6f)));
            lightWidthValue.set(toFloat(props.getOrDefault("width", 4f)));
            lightHeightValue.set(toFloat(props.getOrDefault("height", 4f)));
            lightDistanceValue.set(toFloat(props.getOrDefault("distance", 8f)));
            lightAngleValue.set(toFloat(props.getOrDefault("angle", 45f)));
            extractColor(props.get("color"), lightColorValue);
            extractDirection(props.get("direction"), lightDirectionValue);
        } else if ("marker".equals(type)) {
            markerNameBuffer.set(String.valueOf(label));
            extractVector(props.getOrDefault("position", null), markerPositionBuffer);
        }
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
    }

    private void updateTransform(SceneSessionManager session, SceneObject selected, float[] translation, float[] rotation, float[] scale, boolean discreteChange) {
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

    private RuntimeObject getSelectedRuntime() {

        RaycastPicker picker = RaycastPicker.getInstance();
        if (picker.hasSelection()) {
            return picker.getSelectedObject();
        }

        if (selectedRuntimeId == null) {
            return null;
        }
        return RuntimeObjectRegistry.getInstance().getById(selectedRuntimeId);
    }

    public RuntimeObject getHoveredRuntime() {
        return RaycastPicker.getInstance().getHoveredObject();
    }

    private Map<String, List<SceneObject>> getCachedHierarchy(SceneSessionManager session) {
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

    private void renderHierarchyNode(SceneSessionManager session, SceneObject object, Map<String, List<SceneObject>> tree) {
        java.util.List<SceneObject> children = tree.getOrDefault(object.getId(), java.util.List.of());
        String filter = hierarchyFilter.get().trim().toLowerCase();
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
                renderHierarchyNode(session, child, tree);
            }
            ImGui.treePop();
        }
    }

    private void handleHierarchyDrop(String newParent) {
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

    private void spawnEmptyObject(SceneSessionManager session, String parentId) {
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

    private boolean hasAssetsOfType(String assetType) {
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

    private int countObjectsInRegion(Box region, boolean markersOnly) {
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

    private void exportBlueprint(SceneSessionManager session, Box region, String requestedName) {
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

    private void loadBlueprintPreview(String fileName, float[] position) {
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

    private void renderPreviewGizmo() {
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

    private static void extractColor(Object value, float[] target) {
        if (!(value instanceof Map<?,?> map)) {
            target[0] = target[1] = target[2] = 1f;
            return;
        }
        target[0] = map.containsKey("r") ? toFloat(map.get("r")) : 1f;
        target[1] = map.containsKey("g") ? toFloat(map.get("g")) : 1f;
        target[2] = map.containsKey("b") ? toFloat(map.get("b")) : 1f;
    }

    private static void extractDirection(Object value, float[] target) {
        if (!(value instanceof Map<?,?> map)) {
            target[0] = 0f;
            target[1] = -1f;
            target[2] = 0f;
            return;
        }
        target[0] = map.containsKey("x") ? toFloat(map.get("x")) : target[0];
        target[1] = map.containsKey("y") ? toFloat(map.get("y")) : target[1];
        target[2] = map.containsKey("z") ? toFloat(map.get("z")) : target[2];
    }

    private static Map<String, Object> vectorToMap(float[] vector) {
        Map<String, Object> map = new ConcurrentHashMap<>();
        map.put("x", vector[0]);
        map.put("y", vector[1]);
        map.put("z", vector[2]);
        return map;
    }

    private static Map<String, Object> colorToMap(float[] color) {
        Map<String, Object> map = new ConcurrentHashMap<>();
        map.put("r", color[0]);
        map.put("g", color[1]);
        map.put("b", color[2]);
        return map;
    }

    private static Map<String, Object> directionToMap(float[] direction) {
        Map<String, Object> map = new ConcurrentHashMap<>();
        map.put("x", direction[0]);
        map.put("y", direction[1]);
        map.put("z", direction[2]);
        return map;
    }

    private String parentIdOf(SceneObject object) {
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

    private static boolean boolValue(Object value, boolean fallback) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        if (value != null) {
            return Boolean.parseBoolean(value.toString());
        }
        return fallback;
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

    private void spawnAsset(MoudPackets.EditorAssetDefinition entry) {
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

    private String generateMarkerName() {
        return "Marker " + markerCounter++;
    }

    private Map<String, List<MoudPackets.EditorAssetDefinition>> groupAssets(List<MoudPackets.EditorAssetDefinition> assets) {
        Map<String, List<MoudPackets.EditorAssetDefinition>> groups = new java.util.TreeMap<>();
        for (MoudPackets.EditorAssetDefinition asset : assets) {
            String group = "General";
            String id = asset.id();
            int slash = id.lastIndexOf('/');
            if (slash > 0) {
                group = id.substring(0, slash);
            }
            groups.computeIfAbsent(group, k -> new java.util.ArrayList<>()).add(asset);
        }
        return groups;
    }

    private boolean assetMatchesFilter(MoudPackets.EditorAssetDefinition entry) {
        String filter = assetFilter.get().trim().toLowerCase();
        if (filter.isEmpty()) {
            return true;
        }
        return entry.label().toLowerCase().contains(filter) || entry.id().toLowerCase().contains(filter);
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

    private void renderRuntimeObjectInspector(RuntimeObject runtimeObject) {
        ImGui.text("Runtime Object: " + runtimeObject.getObjectId());
        ImGui.text("Type: " + runtimeObject.getType());
        ImGui.textDisabled("Read-only - runtime objects are controlled by scripts");
        ImGui.separator();

        ImGui.textDisabled("Transform");
        ImGui.separator();
        Vec3d pos = runtimeObject.getPosition();
        float[] position = new float[]{(float) pos.x, (float) pos.y, (float) pos.z};
        ImGui.inputFloat3("Position", position, "%.3f", imgui.flag.ImGuiInputTextFlags.ReadOnly);

        Vec3d rot = runtimeObject.getRotation();
        float[] rotation = new float[]{(float) rot.x, (float) rot.y, (float) rot.z};
        ImGui.inputFloat3("Rotation", rotation, "%.3f", imgui.flag.ImGuiInputTextFlags.ReadOnly);

        Vec3d scl = runtimeObject.getScale();
        float[] scale = new float[]{(float) scl.x, (float) scl.y, (float) scl.z};
        ImGui.inputFloat3("Scale", scale, "%.3f", imgui.flag.ImGuiInputTextFlags.ReadOnly);

        if (runtimeObject.getType() == RuntimeObjectType.MODEL) {
            ImGui.separator();
            ImGui.textDisabled("Model Properties");
            ImGui.separator();
            String modelPath = runtimeObject.getModelPath();
            if (modelPath != null) {
                ImGui.text("Model: " + modelPath);
            }
            String texturePath = runtimeObject.getTexturePath();
            if (texturePath != null && !texturePath.isEmpty()) {
                ImGui.text("Texture: " + texturePath);
            }
        }

        if (runtimeObject.getType() == RuntimeObjectType.DISPLAY) {
            RuntimeObject.DisplayState displayState = runtimeObject.getDisplayState();
            if (displayState != null) {
                ImGui.separator();
                ImGui.textDisabled("Display Properties");
                ImGui.separator();
                if (displayState.getContentType() != null) {
                    ImGui.text("Content Type: " + displayState.getContentType());
                }
                if (displayState.getPrimarySource() != null) {
                    ImGui.text("Source: " + displayState.getPrimarySource());
                }
                ImGui.text("Loop: " + (displayState.isLoop() ? "Yes" : "No"));
                ImGui.text("Frame Rate: " + displayState.getFrameRate());
            }
        } else if (runtimeObject.getType() == RuntimeObjectType.PLAYER) {
            ImGui.separator();
            ImGui.textDisabled("Player");
            if (runtimeObject.getPlayerUuid() != null) {
                ImGui.text("UUID: " + runtimeObject.getPlayerUuid());
            }
            ImGui.textDisabled("Use the gizmo to teleport this player.");
        }
    }
}