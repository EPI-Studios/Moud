package com.moud.client.editor.ui.panel;

import com.moud.client.editor.assets.ProjectFileIndex;
import com.moud.client.editor.plugin.EditorPluginHost;
import com.moud.client.editor.runtime.RuntimeObject;
import com.moud.client.editor.runtime.RuntimeObjectType;
import com.moud.client.editor.scene.SceneHistoryManager;
import com.moud.client.editor.scene.SceneObject;
import com.moud.client.editor.scene.SceneSessionManager;
import com.moud.client.editor.ui.SceneEditorOverlay;
import com.moud.client.editor.ui.layout.EditorDockingLayout;
import imgui.ImGui;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiTableFlags;
import imgui.type.ImBoolean;
import imgui.type.ImFloat;
import imgui.type.ImInt;
import imgui.type.ImString;
import net.minecraft.util.math.Vec3d;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class InspectorPanel {
    private final SceneEditorOverlay overlay;
    private final SceneHistoryManager history = SceneHistoryManager.getInstance();
    private final ImString inspectorLabel = new ImString("", 128);
    private final ImString markerLabelBuffer = new ImString("Marker", 64);
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
    private final ImString modelPathBuffer = new ImString("", 192);
    private final ImString texturePathBuffer = new ImString("", 192);
    private final ImString modelFilePickerFilter = new ImString(128);
    private final ImString textureFilePickerFilter = new ImString(128);
    private final ImString displayFilePickerFilter = new ImString(128);
    private final float[] markerPositionBuffer = new float[]{0f, 0f, 0f};
    private final ImString playerAnimationOverride = new ImString("", 128);
    private final ImBoolean playerAutoAnimation = new ImBoolean(true);
    private final ImBoolean playerLoopAnimation = new ImBoolean(true);
    private final ImInt playerAnimationDurationMs = new ImInt(2000);
    private final ImString playerSkinBuffer = new ImString("", 256);
    private final ImBoolean playerSneaking = new ImBoolean(false);
    private final ImBoolean playerSprinting = new ImBoolean(false);
    private final ImBoolean playerSwinging = new ImBoolean(false);
    private final ImBoolean playerUsingItem = new ImBoolean(false);
    private final float[] cameraPositionBuffer = new float[]{0f, 0f, 0f};
    private final float[] cameraRotationBuffer = new float[]{0f, 0f, 0f};
    private final ImFloat cameraFov = new ImFloat(70f);
    private final ImFloat cameraNear = new ImFloat(0.1f);
    private final ImFloat cameraFar = new ImFloat(128f);
    private String currentObjectId;

    public InspectorPanel(SceneEditorOverlay overlay) {
        this.overlay = overlay;
    }

    private static boolean hasExtension(String path, String... extensions) {
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

    private static String toResourcePath(String projectPath) {
        if (projectPath == null || projectPath.isBlank()) {
            return projectPath;
        }
        String normalized = projectPath.replace('\\', '/');
        if (!normalized.startsWith("assets/")) {
            return normalized;
        }
        String withoutAssets = normalized.substring("assets/".length());
        int slash = withoutAssets.indexOf('/');
        if (slash < 0) {
            return normalized;
        }
        String namespace = withoutAssets.substring(0, slash);
        String relative = withoutAssets.substring(slash + 1);
        return namespace + ":" + relative;
    }

    private static void extractBool(Object value, ImBoolean target) {
        if (value instanceof Boolean b) {
            target.set(b);
        } else if (value instanceof Number n) {
            target.set(n.intValue() != 0);
        } else {
            target.set(false);
        }
    }

    private static void extractColor(Object value, float[] target) {
        if (!(value instanceof Map<?, ?> map)) {
            Arrays.fill(target, 1f);
            return;
        }
        target[0] = map.containsKey("r") ? toFloat(map.get("r")) : 1f;
        target[1] = map.containsKey("g") ? toFloat(map.get("g")) : 1f;
        target[2] = map.containsKey("b") ? toFloat(map.get("b")) : 1f;
    }

    private static void extractDirection(Object value, float[] target) {
        if (!(value instanceof Map<?, ?> map)) {
            target[0] = 0f;
            target[1] = -1f;
            target[2] = 0f;
            return;
        }
        target[0] = map.containsKey("x") ? toFloat(map.get("x")) : target[0];
        target[1] = map.containsKey("y") ? toFloat(map.get("y")) : target[1];
        target[2] = map.containsKey("z") ? toFloat(map.get("z")) : target[2];
    }

    private static float[] extractVector(Object value, float[] fallback) {
        if (value instanceof Map<?, ?> map) {
            Object x = map.get("x");
            Object y = map.get("y");
            Object z = map.get("z");
            if (x != null) fallback[0] = toFloat(x);
            if (y != null) fallback[1] = toFloat(y);
            if (z != null) fallback[2] = toFloat(z);
        } else if (value instanceof List<?> list && list.size() >= 3) {
            fallback[0] = toFloat(list.get(0));
            fallback[1] = toFloat(list.get(1));
            fallback[2] = toFloat(list.get(2));
        }
        return fallback;
    }

    private static float[] extractEuler(Object value, float[] fallback) {
        if (value instanceof Map<?, ?> map) {
            boolean hasEuler = map.containsKey("pitch") || map.containsKey("yaw") || map.containsKey("roll");
            boolean hasAxis = map.containsKey("x") || map.containsKey("y") || map.containsKey("z");
            if (hasEuler) {
                Object pitch = map.get("pitch");
                Object yaw = map.get("yaw");
                Object roll = map.get("roll");
                if (pitch != null) fallback[0] = toFloat(pitch);
                if (yaw != null) fallback[1] = toFloat(yaw);
                if (roll != null) fallback[2] = toFloat(roll);
            } else if (hasAxis) {
                Object pitch = map.get("x");
                Object yaw = map.get("y");
                Object roll = map.get("z");
                if (pitch != null) fallback[0] = toFloat(pitch);
                if (yaw != null) fallback[1] = toFloat(yaw);
                if (roll != null) fallback[2] = toFloat(roll);
            }
        } else if (value instanceof List<?> list && list.size() >= 3) {
            fallback[0] = toFloat(list.get(0));
            fallback[1] = toFloat(list.get(1));
            fallback[2] = toFloat(list.get(2));
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

    private static Map<String, Object> eulerToMap(float[] euler) {
        Map<String, Object> map = new ConcurrentHashMap<>();
        map.put("pitch", euler[0]);
        map.put("yaw", euler[1]);
        map.put("roll", euler[2]);
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

    public void render(SceneSessionManager session) {
        if (!overlay.beginDockedPanel(EditorDockingLayout.Region.INSPECTOR, "Inspector")) {
            ImGui.end();
            return;
        }
        SceneObject selected = overlay.getSelectedObject();
        RuntimeObject runtimeObject = overlay.getSelectedRuntime();
        if (selected == null && runtimeObject == null) {
            ImGui.textDisabled("Select an object to inspect its properties.");
            ImGui.end();
            return;
        }

        if (selected != null && !selected.getId().equals(currentObjectId)) {
            onSelectionChanged(selected);
        }

        if (selected == null) {
            renderRuntimeInspector(runtimeObject);
            ImGui.end();
            return;
        }

        renderSceneObjectInspector(session, selected);
        ImGui.end();
    }

    public void onSelectionChanged(SceneObject object) {
        currentObjectId = object != null ? object.getId() : null;
        if (object == null) {
            resetBuffers();
            return;
        }
        Map<String, Object> props = object.getProperties();
        inspectorLabel.set(String.valueOf(props.getOrDefault("label", object.getId())));
        markerLabelBuffer.set(inspectorLabel.get());
        modelPathBuffer.set(String.valueOf(props.getOrDefault("modelPath", "")));
        texturePathBuffer.set(String.valueOf(props.getOrDefault("texture", "")));
        displayContentBuffer.set(String.valueOf(props.getOrDefault("displayContent", "")));
        displayTypeBuffer.set(String.valueOf(props.getOrDefault("displayType", "image")));
        displayLoopToggle.set(boolValue(props.get("loop"), true));
        displayPlayingToggle.set(boolValue(props.get("playing"), true));
        displayFrameRateValue.set(toFloat(props.getOrDefault("frameRate", 24f)));
        Object lightType = props.getOrDefault("lightType", "point");
        lightTypeIndex.set("area".equalsIgnoreCase(String.valueOf(lightType)) ? 1 : 0);
        lightBrightnessValue.set(toFloat(props.getOrDefault("brightness", 1f)));
        lightRadiusValue.set(toFloat(props.getOrDefault("radius", 6f)));
        lightWidthValue.set(toFloat(props.getOrDefault("width", 4f)));
        lightHeightValue.set(toFloat(props.getOrDefault("height", 4f)));
        lightDistanceValue.set(toFloat(props.getOrDefault("distance", 8f)));
        lightAngleValue.set(toFloat(props.getOrDefault("angle", 45f)));
        extractColor(props.get("color"), lightColorValue);
        extractDirection(props.get("direction"), lightDirectionValue);
        extractVector(props.getOrDefault("position", null), markerPositionBuffer);
        playerSkinBuffer.set(String.valueOf(props.getOrDefault("skinUrl", overlay.getDefaultFakePlayerSkin())));
        playerAutoAnimation.set(boolValue(props.get("autoAnimation"), true));
        playerLoopAnimation.set(boolValue(props.get("loopAnimation"), true));
        Object durationValue = props.getOrDefault("animationDuration", 2000);
        if (durationValue instanceof Number durationNumber) {
            playerAnimationDurationMs.set(Math.max(100, durationNumber.intValue()));
        } else {
            try {
                playerAnimationDurationMs.set(Math.max(100, Integer.parseInt(String.valueOf(durationValue))));
            } catch (Exception ignored) {
                playerAnimationDurationMs.set(2000);
            }
        }
        playerAnimationOverride.set(String.valueOf(props.getOrDefault("animationOverride", "")));
    }

    public void onRuntimeSelection(RuntimeObject runtimeObject) {
        currentObjectId = null;
        resetBuffers();
    }

    private void renderSceneObjectInspector(SceneSessionManager session, SceneObject selected) {
        ImGui.text("Object ID: " + selected.getId());
        ImGui.sameLine();
        if (ImGui.button("Focus##focus_object")) {
            overlay.focusSelection(selected);
        }
        ImGui.text("Type: " + selected.getType());
        String parent = overlay.parentIdOf(selected);
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
        float[] translation = extractVector(props.get("position"), overlay.getActiveTranslation());
        if (ImGui.dragFloat3("Position", translation, 0.1f)) {
            overlay.updateTransform(session, selected, translation, overlay.getActiveRotation(), overlay.getActiveScale(), true);
        }
        if (ImGui.dragFloat3("Rotation", overlay.getActiveRotation(), 0.5f)) {
            overlay.updateTransform(session, selected, overlay.getActiveTranslation(), overlay.getActiveRotation(), overlay.getActiveScale(), true);
        }
        float[] scale = extractVector(props.get("scale"), overlay.getActiveScale());
        if (ImGui.dragFloat3("Scale", scale, 0.1f)) {
            overlay.updateTransform(session, selected, overlay.getActiveTranslation(), overlay.getActiveRotation(), scale, true);
        }

        String type = selected.getType() == null ? "" : selected.getType().toLowerCase(Locale.ROOT);
        switch (type) {
            case "model" -> renderModelProperties(session, selected);
            case "display" -> renderDisplayProperties(session, selected);
            case "light" -> renderLightProperties(session, selected);
            case "marker" -> renderMarkerProperties(session, selected, props);
            case "player_model" -> renderPlayerModelProperties(session, selected);
            case "camera" -> renderCameraProperties(session, selected);
            default -> {
            }
        }
        renderAllProperties(props);
        EditorPluginHost.getInstance().renderInspectorExtras(selected);
    }

    private void renderModelProperties(SceneSessionManager session, SceneObject selected) {
        ImGui.textDisabled("Model");
        ImGui.separator();
        if (ImGui.inputText("Model Path", modelPathBuffer)) {
            Map<String, Object> update = new ConcurrentHashMap<>();
            update.put("modelPath", modelPathBuffer.get());
            session.submitPropertyUpdate(selected.getId(), update);
        }
        if (ImGui.button("Pick##model_path_picker")) {
            ImGui.openPopup("model_path_picker");
        }
        String pickedModelFile = renderProjectFilePickerPopup(
                "model_path_picker",
                "Select Model File",
                modelFilePickerFilter,
                node -> hasExtension(node.path(), ".gltf", ".glb")
        );
        if (pickedModelFile != null) {
            String value = toResourcePath(pickedModelFile);
            modelPathBuffer.set(value);
            Map<String, Object> update = new ConcurrentHashMap<>();
            update.put("modelPath", value);
            session.submitPropertyUpdate(selected.getId(), update);
        }
        acceptProjectFileDrop(value -> {
            modelPathBuffer.set(value);
            Map<String, Object> update = new ConcurrentHashMap<>();
            update.put("modelPath", value);
            session.submitPropertyUpdate(selected.getId(), update);
        }, true);

        if (ImGui.inputText("Texture", texturePathBuffer)) {
            Map<String, Object> update = new ConcurrentHashMap<>();
            update.put("texture", texturePathBuffer.get());
            session.submitPropertyUpdate(selected.getId(), update);
        }
        if (ImGui.button("Pick##texture_picker")) {
            ImGui.openPopup("texture_picker");
        }
        String pickedTexture = renderProjectFilePickerPopup(
                "texture_picker",
                "Select Texture File",
                textureFilePickerFilter,
                node -> hasExtension(node.path(), ".png", ".jpg", ".jpeg")
        );
        if (pickedTexture != null) {
            String value = toResourcePath(pickedTexture);
            texturePathBuffer.set(value);
            Map<String, Object> update = new ConcurrentHashMap<>();
            update.put("texture", value);
            session.submitPropertyUpdate(selected.getId(), update);
        }
        acceptProjectFileDrop(value -> {
            texturePathBuffer.set(value);
            Map<String, Object> update = new ConcurrentHashMap<>();
            update.put("texture", value);
            session.submitPropertyUpdate(selected.getId(), update);
        }, true);
    }

    private void renderDisplayProperties(SceneSessionManager session, SceneObject selected) {
        ImGui.textDisabled("Display");
        ImGui.separator();
        if (ImGui.beginCombo("Content Type", displayTypeBuffer.get())) {
            for (String option : SceneEditorOverlay.DISPLAY_CONTENT_TYPES) {
                boolean active = displayTypeBuffer.get().equalsIgnoreCase(option);
                if (ImGui.selectable(option, active)) {
                    displayTypeBuffer.set(option);
                    Map<String, Object> update = new ConcurrentHashMap<>();
                    update.put("displayType", option);
                    session.submitPropertyUpdate(selected.getId(), update);
                }
                if (active) {
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
        if (ImGui.button("Pick##display_source_picker")) {
            ImGui.openPopup("display_source_picker");
        }
        String pickedDisplay = renderProjectFilePickerPopup(
                "display_source_picker",
                "Select Display Content",
                displayFilePickerFilter,
                node -> hasExtension(node.path(), ".png", ".jpg", ".jpeg", ".mp4", ".webm")
        );
        if (pickedDisplay != null) {
            String value = toResourcePath(pickedDisplay);
            displayContentBuffer.set(value);
            Map<String, Object> update = new ConcurrentHashMap<>();
            update.put("displayContent", value);
            session.submitPropertyUpdate(selected.getId(), update);
        }
        acceptProjectFileDrop(value -> {
            displayContentBuffer.set(value);
            Map<String, Object> update = new ConcurrentHashMap<>();
            update.put("displayContent", value);
            session.submitPropertyUpdate(selected.getId(), update);
        }, true);
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
        ImGui.separator();
        int typeIndex = Math.max(0, Math.min(lightTypeIndex.get(), SceneEditorOverlay.LIGHT_TYPE_LABELS.length - 1));
        if (ImGui.beginCombo("Light Type", SceneEditorOverlay.LIGHT_TYPE_LABELS[typeIndex])) {
            for (int i = 0; i < SceneEditorOverlay.LIGHT_TYPE_LABELS.length; i++) {
                boolean active = lightTypeIndex.get() == i;
                if (ImGui.selectable(SceneEditorOverlay.LIGHT_TYPE_LABELS[i], active)) {
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

    private void renderMarkerProperties(SceneSessionManager session, SceneObject selected, Map<String, Object> props) {
        ImGui.textDisabled("Marker");
        ImGui.separator();
        if (ImGui.inputText("Marker Name", markerLabelBuffer)) {
            Map<String, Object> update = new ConcurrentHashMap<>();
            update.put("label", markerLabelBuffer.get());
            update.put("name", markerLabelBuffer.get());
            session.submitPropertyUpdate(selected.getId(), update);
        }
        extractVector(props.getOrDefault("position", null), markerPositionBuffer);
        if (ImGui.dragFloat3("Marker Position", markerPositionBuffer, 0.05f)) {
            Map<String, Object> update = new ConcurrentHashMap<>();
            update.put("position", vectorToMap(markerPositionBuffer));
            session.submitPropertyUpdate(selected.getId(), update);
        }
    }

    private void renderPlayerModelProperties(SceneSessionManager session, SceneObject selected) {
        Map<String, Object> props = selected.getProperties();
        ImGui.textDisabled("Fake Player");

        // hydrate buffers from props
        inspectorLabel.set(String.valueOf(props.getOrDefault("label", inspectorLabel.get())));
        playerSkinBuffer.set(String.valueOf(props.getOrDefault("skinUrl", overlay.getDefaultFakePlayerSkin())));
        playerAutoAnimation.set(boolValue(props.get("autoAnimation"), true));
        playerLoopAnimation.set(boolValue(props.get("loopAnimation"), true));
        Object duration = props.get("animationDuration");
        if (duration instanceof Number n) {
            playerAnimationDurationMs.set(n.intValue());
        }
        playerAnimationOverride.set(String.valueOf(props.getOrDefault("animationOverride", playerAnimationOverride.get())));
        extractBool(props.getOrDefault("sneaking", false), playerSneaking);
        extractBool(props.getOrDefault("sprinting", false), playerSprinting);
        extractBool(props.getOrDefault("swinging", false), playerSwinging);
        extractBool(props.getOrDefault("usingItem", false), playerUsingItem);

        if (ImGui.inputText("Label", inspectorLabel)) {
            Map<String, Object> update = new ConcurrentHashMap<>();
            update.put("label", inspectorLabel.get());
            session.submitPropertyUpdate(selected.getId(), update);
        }
        if (ImGui.inputText("Skin URL", playerSkinBuffer)) {
            Map<String, Object> update = new ConcurrentHashMap<>();
            update.put("skinUrl", playerSkinBuffer.get());
            session.submitPropertyUpdate(selected.getId(), update);
        }
        if (ImGui.checkbox("Auto Animation", playerAutoAnimation)) {
            Map<String, Object> update = new ConcurrentHashMap<>();
            update.put("autoAnimation", playerAutoAnimation.get());
            session.submitPropertyUpdate(selected.getId(), update);
        }
        if (ImGui.checkbox("Loop Override Animation", playerLoopAnimation)) {
            Map<String, Object> update = new ConcurrentHashMap<>();
            update.put("loopAnimation", playerLoopAnimation.get());
            session.submitPropertyUpdate(selected.getId(), update);
        }
        if (!playerLoopAnimation.get()) {
            int minMs = 250;
            int maxMs = 20000;
            if (ImGui.dragInt("One-shot Duration (ms)", playerAnimationDurationMs.getData(), 50, minMs, maxMs)) {
                playerAnimationDurationMs.set(Math.max(minMs, Math.min(maxMs, playerAnimationDurationMs.get())));
                Map<String, Object> update = new ConcurrentHashMap<>();
                update.put("animationDuration", playerAnimationDurationMs.get());
                session.submitPropertyUpdate(selected.getId(), update);
            }
        }
        String current = playerAnimationOverride.get().isBlank() ? "None" : playerAnimationOverride.get();
        if (ImGui.beginCombo("Animation Override", current)) {
            if (ImGui.selectable("None", playerAnimationOverride.get().isBlank())) {
                playerAnimationOverride.set("");
                Map<String, Object> update = new ConcurrentHashMap<>();
                update.put("animationOverride", "");
                session.submitPropertyUpdate(selected.getId(), update);
            }
            String[] animations = overlay.getKnownPlayerAnimations();
            if (animations.length == 0) {
                ImGui.textDisabled("No animations detected in loaded packs.");
            } else {
                for (String option : animations) {
                    boolean selectedOption = option.equals(playerAnimationOverride.get());
                    if (ImGui.selectable(option, selectedOption)) {
                        playerAnimationOverride.set(option);
                        Map<String, Object> update = new ConcurrentHashMap<>();
                        update.put("animationOverride", option);
                        session.submitPropertyUpdate(selected.getId(), update);
                    }
                    if (selectedOption) ImGui.setItemDefaultFocus();
                }
            }
            ImGui.endCombo();
        }
        if (ImGui.inputText("Custom Animation", playerAnimationOverride)) {
            Map<String, Object> update = new ConcurrentHashMap<>();
            update.put("animationOverride", playerAnimationOverride.get());
            session.submitPropertyUpdate(selected.getId(), update);
        }
        if (ImGui.button("Clear Override")) {
            playerAnimationOverride.set("");
            Map<String, Object> update = new ConcurrentHashMap<>();
            update.put("animationOverride", "");
            session.submitPropertyUpdate(selected.getId(), update);
        }

        // Basic pose toggles
        extractBool(props.getOrDefault("sneaking", false), playerSneaking);
        extractBool(props.getOrDefault("sprinting", false), playerSprinting);
        extractBool(props.getOrDefault("swinging", false), playerSwinging);
        extractBool(props.getOrDefault("usingItem", false), playerUsingItem);
        if (ImGui.checkbox("Sneaking", playerSneaking)) {
            Map<String, Object> update = new ConcurrentHashMap<>();
            update.put("sneaking", playerSneaking.get());
            session.submitPropertyUpdate(selected.getId(), update);
        }
        if (ImGui.checkbox("Sprinting", playerSprinting)) {
            Map<String, Object> update = new ConcurrentHashMap<>();
            update.put("sprinting", playerSprinting.get());
            session.submitPropertyUpdate(selected.getId(), update);
        }
        if (ImGui.checkbox("Swinging", playerSwinging)) {
            Map<String, Object> update = new ConcurrentHashMap<>();
            update.put("swinging", playerSwinging.get());
            session.submitPropertyUpdate(selected.getId(), update);
        }
        if (ImGui.checkbox("Using Item", playerUsingItem)) {
            Map<String, Object> update = new ConcurrentHashMap<>();
            update.put("usingItem", playerUsingItem.get());
            session.submitPropertyUpdate(selected.getId(), update);
        }
    }

    private void renderCameraProperties(SceneSessionManager session, SceneObject selected) {
        Map<String, Object> props = selected.getProperties();
        ImGui.textDisabled("Camera");
        inspectorLabel.set(String.valueOf(props.getOrDefault("label", inspectorLabel.get())));
        extractVector(props.get("position"), cameraPositionBuffer);
        extractEuler(props.get("rotation"), cameraRotationBuffer);
        cameraFov.set(toFloat(props.getOrDefault("fov", cameraFov.get())));
        cameraNear.set(toFloat(props.getOrDefault("near", cameraNear.get())));
        cameraFar.set(toFloat(props.getOrDefault("far", cameraFar.get())));

        if (ImGui.inputText("Label", inspectorLabel)) {
            Map<String, Object> update = new ConcurrentHashMap<>();
            update.put("label", inspectorLabel.get());
            session.submitPropertyUpdate(selected.getId(), update);
        }
        if (ImGui.dragFloat3("Position", cameraPositionBuffer, 0.05f)) {
            Map<String, Object> update = new ConcurrentHashMap<>();
            update.put("position", vectorToMap(cameraPositionBuffer));
            session.submitPropertyUpdate(selected.getId(), update);
        }
        if (ImGui.dragFloat3("Rotation (pitch/yaw/roll)", cameraRotationBuffer, 0.5f)) {
            Map<String, Object> update = new ConcurrentHashMap<>();
            update.put("rotation", eulerToMap(cameraRotationBuffer));
            session.submitPropertyUpdate(selected.getId(), update);
        }
        if (ImGui.dragFloat("FOV", cameraFov.getData(), 1f, 10f, 170f, "%.1f")) {
            Map<String, Object> update = new ConcurrentHashMap<>();
            update.put("fov", cameraFov.get());
            session.submitPropertyUpdate(selected.getId(), update);
        }
        if (ImGui.dragFloat("Near", cameraNear.getData(), 0.01f, 0.01f, 10f, "%.2f")) {
            Map<String, Object> update = new ConcurrentHashMap<>();
            update.put("near", cameraNear.get());
            session.submitPropertyUpdate(selected.getId(), update);
        }
        if (ImGui.dragFloat("Far", cameraFar.getData(), 1f, 1f, 2048f, "%.1f")) {
            Map<String, Object> update = new ConcurrentHashMap<>();
            update.put("far", cameraFar.get());
            session.submitPropertyUpdate(selected.getId(), update);
        }
    }

    private void renderRuntimeInspector(RuntimeObject runtimeObject) {
        if (runtimeObject == null) {
            ImGui.textDisabled("No runtime object selected.");
            return;
        }
        ImGui.text("Runtime Object");
        ImGui.text("ID: " + runtimeObject.getObjectId());
        ImGui.text("Type: " + runtimeObject.getType());
        ImGui.textDisabled("Read-only - runtime objects are controlled by scripts");
        ImGui.separator();

        Vec3d pos = runtimeObject.getPosition();
        float[] position = new float[]{(float) pos.x, (float) pos.y, (float) pos.z};
        ImGui.inputFloat3("Position", position, "%.3f", ImGuiInputTextFlags.ReadOnly);

        Vec3d rot = runtimeObject.getRotation();
        float[] rotation = new float[]{(float) rot.x, (float) rot.y, (float) rot.z};
        ImGui.inputFloat3("Rotation", rotation, "%.3f", ImGuiInputTextFlags.ReadOnly);

        Vec3d scl = runtimeObject.getScale();
        float[] scale = new float[]{(float) scl.x, (float) scl.y, (float) scl.z};
        ImGui.inputFloat3("Scale", scale, "%.3f", ImGuiInputTextFlags.ReadOnly);

        if (runtimeObject.getType() == RuntimeObjectType.MODEL) {
            ImGui.separator();
            ImGui.textDisabled("Model Properties");
            if (runtimeObject.getModelPath() != null) {
                ImGui.text("Model: " + runtimeObject.getModelPath());
            }
            if (runtimeObject.getTexturePath() != null && !runtimeObject.getTexturePath().isEmpty()) {
                ImGui.text("Texture: " + runtimeObject.getTexturePath());
            }
        }
    }

    private void renderAllProperties(Map<String, Object> props) {
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

    private void acceptProjectFileDrop(Consumer<String> consumer, boolean convertToResource) {
        if (consumer == null) {
            return;
        }
        if (ImGui.beginDragDropTarget()) {
            byte[] payload = ImGui.acceptDragDropPayload(SceneEditorOverlay.PAYLOAD_PROJECT_FILE);
            if (payload != null) {
                String rawPath = new String(payload, StandardCharsets.UTF_8);
                consumer.accept(convertToResource ? toResourcePath(rawPath) : rawPath);
            }
            ImGui.endDragDropTarget();
        }
    }

    private String renderProjectFilePickerPopup(String popupId, String title, ImString search, Predicate<ProjectFileIndex.Node> filter) {
        String selected = null;
        if (ImGui.beginPopup(popupId)) {
            ProjectFileIndex index = ProjectFileIndex.getInstance();
            index.requestSyncIfNeeded();
            ImGui.text(title);
            ImGui.separator();
            ImGui.setNextItemWidth(-1);
            ImGui.inputTextWithHint("##" + popupId + "_search", "Search...", search);
            ImGui.beginChild(popupId + "_scroll", 400, 300, true);
            List<ProjectFileIndex.Node> files = index.listFiles();
            String filterText = search.get().toLowerCase(Locale.ROOT);
            int shown = 0;
            for (ProjectFileIndex.Node node : files) {
                if (filter != null && !filter.test(node)) {
                    continue;
                }
                String pathLower = node.path().toLowerCase(Locale.ROOT);
                if (!filterText.isBlank() && !pathLower.contains(filterText)) {
                    continue;
                }
                shown++;
                if (ImGui.selectable(node.path())) {
                    selected = node.path();
                    ImGui.closeCurrentPopup();
                    break;
                }
            }
            if (shown == 0) {
                ImGui.textDisabled("No matching files.");
            }
            ImGui.endChild();
            ImGui.endPopup();
        }
        return selected;
    }

    private void resetBuffers() {
        inspectorLabel.set("");
        markerLabelBuffer.set("Marker");
        modelPathBuffer.set("");
        texturePathBuffer.set("");
        displayContentBuffer.set("");
        displayTypeBuffer.set("image");
        displayLoopToggle.set(true);
        displayPlayingToggle.set(true);
        displayFrameRateValue.set(24f);
        lightTypeIndex.set(0);
        lightBrightnessValue.set(1f);
        lightRadiusValue.set(6f);
        lightWidthValue.set(4f);
        lightHeightValue.set(4f);
        lightDistanceValue.set(8f);
        lightAngleValue.set(45f);
        Arrays.fill(markerPositionBuffer, 0f);
        lightColorValue[0] = lightColorValue[1] = lightColorValue[2] = 1f;
        lightDirectionValue[0] = 0f;
        lightDirectionValue[1] = -1f;
        lightDirectionValue[2] = 0f;
        playerSkinBuffer.set(overlay.getDefaultFakePlayerSkin());
        playerAnimationOverride.set("");
        playerAutoAnimation.set(true);
        playerLoopAnimation.set(true);
        playerAnimationDurationMs.set(2000);
        playerSneaking.set(false);
        playerSprinting.set(false);
        playerSwinging.set(false);
        playerUsingItem.set(false);
        cameraPositionBuffer[0] = cameraPositionBuffer[1] = cameraPositionBuffer[2] = 0f;
        cameraRotationBuffer[0] = cameraRotationBuffer[1] = cameraRotationBuffer[2] = 0f;
        cameraFov.set(70f);
        cameraNear.set(0.1f);
        cameraFar.set(128f);
    }
}
