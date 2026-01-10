package com.moud.client.editor.ui.panel;

import com.moud.api.particle.RenderType;
import com.moud.api.particle.Billboarding;
import com.moud.api.particle.CollisionMode;
import com.moud.api.util.PathUtils;
import com.moud.client.animation.ClientFakePlayerManager;
import com.moud.client.animation.ClientPlayerModelManager;
import com.moud.client.animation.PlayerPartConfigManager;
import com.moud.client.editor.assets.ProjectFileIndex;
import com.moud.client.editor.plugin.EditorPluginHost;
import com.moud.client.editor.runtime.RuntimeObject;
import com.moud.client.editor.runtime.RuntimeObjectType;
import com.moud.client.editor.scene.SceneHistoryManager;
import com.moud.client.editor.scene.SceneObject;
import com.moud.client.editor.scene.SceneSessionManager;
import com.moud.client.editor.selection.SceneSelectionManager;
import com.moud.client.editor.ui.SceneEditorOverlay;
import com.moud.client.editor.ui.layout.EditorDockingLayout;
import com.moud.client.model.ClientModelManager;
import com.moud.client.model.RenderableModel;
import com.moud.client.model.gltf.GltfSkinnedModelLoader;
import com.moud.client.rendering.PostEffectUniformRegistry;
import com.moud.client.util.IdentifierUtils;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiTableFlags;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImBoolean;
import imgui.type.ImFloat;
import imgui.type.ImInt;
import imgui.type.ImString;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
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
    private final ImString modelAnimationClipBuffer = new ImString("", 128);
    private final ImBoolean modelAnimationPlaying = new ImBoolean(false);
    private final ImBoolean modelAnimationLoop = new ImBoolean(true);
    private final ImFloat modelAnimationSpeed = new ImFloat(1f);
    private final ImFloat modelAnimationTime = new ImFloat(0f);
    private final Map<String, List<RenderableModel.AnimationInfo>> glbAnimationCache = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<List<RenderableModel.AnimationInfo>>> glbAnimationLoads = new ConcurrentHashMap<>();
    private final Map<String, String> glbAnimationErrors = new ConcurrentHashMap<>();
    private final ImString modelFilePickerFilter = new ImString(128);
    private final ImString textureFilePickerFilter = new ImString(128);
    private final ImString displayFilePickerFilter = new ImString(128);
    private final ImBoolean displayPbrEnabled = new ImBoolean(false);
    private final ImString displayPbrBaseColor = new ImString("", 256);
    private final ImString displayPbrNormal = new ImString("", 256);
    private final ImString displayPbrMetallicRoughness = new ImString("", 256);
    private final ImString displayPbrEmissive = new ImString("", 256);
    private final ImString displayPbrOcclusion = new ImString("", 256);
    private final ImFloat displayPbrMetallicFactor = new ImFloat(0f);
    private final ImFloat displayPbrRoughnessFactor = new ImFloat(1f);
    private final float[] markerPositionBuffer = new float[]{0f, 0f, 0f};
    private final ImString playerAnimationOverride = new ImString("", 128);
    private final ImBoolean playerAutoAnimation = new ImBoolean(true);
    private final ImBoolean playerLoopAnimation = new ImBoolean(true);
    private final ImInt playerAnimationDurationMs = new ImInt(2000);
    private final ImString playerSkinBuffer = new ImString("", 256);
    private final float[] cameraPositionBuffer = new float[]{0f, 0f, 0f};
    private final float[] cameraRotationBuffer = new float[]{0f, 0f, 0f};
    private final ImFloat cameraFov = new ImFloat(70f);
    private final ImFloat cameraNear = new ImFloat(0.1f);
    private final ImFloat cameraFar = new ImFloat(128f);
    private final ImFloat fogStart = new ImFloat(-10f);
    private final ImFloat fogEnd = new ImFloat(120f);
    private final float[] fogColor = new float[]{0.2f, 0.4f, 0.6f, 1.0f};
    private final ImInt fogShape = new ImInt(0);
    private final ImFloat heightFogY = new ImFloat(64f);
    private final ImFloat heightFogThickness = new ImFloat(0.25f);
    private final float[] heightFogColor = new float[]{0.3f, 0.3f, 0.35f, 1.0f};
    private final ImFloat ssrStrength = new ImFloat(0.35f);
    private final ImFloat ssrMaxDistance = new ImFloat(32f);
    private final ImFloat ssrStepSize = new ImFloat(0.2f);
    private final ImFloat ssrThickness = new ImFloat(0.002f);
    private final ImFloat ssrEdgeFade = new ImFloat(0.12f);
    private static final String[] PRIMITIVE_TYPE_LABELS = {
            "Cube",
            "Sphere",
            "Cylinder",
            "Capsule",
            "Cone",
            "Plane"
    };

    private static final String[] PRIMITIVE_TYPE_VALUES = {
            "cube",
            "sphere",
            "cylinder",
            "capsule",
            "cone",
            "plane"
    };

    private record AnimationLookup(List<RenderableModel.AnimationInfo> animations, boolean loading, String error) {}

    private final ImInt primitiveTypeIndex = new ImInt(0);
    private final ImBoolean primitiveDynamic = new ImBoolean(false);
    private final ImFloat primitiveMass = new ImFloat(1.0f);
    private final ImBoolean primitiveUnlit = new ImBoolean(false);
    private final ImBoolean primitiveDoubleSided = new ImBoolean(false);
    private final ImBoolean primitiveRenderThroughBlocks = new ImBoolean(false);
    private final float[] primitiveColor = new float[]{1f, 1f, 1f, 1f};
    private final ImString primitiveTextureBuffer = new ImString("", 192);
    private final ImString primitiveTextureFilePickerFilter = new ImString(128);
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
        String normalized = PathUtils.normalizeSlashes(projectPath);
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

    private long parseModelId(String objId) {
        if (objId == null) return -1;
        int colon = objId.indexOf(':');
        if (colon >= 0 && colon < objId.length() - 1) {
            try {
                return Long.parseLong(objId.substring(colon + 1));
            } catch (NumberFormatException ignored) {
            }
        }
        return -1;
    }

    private String boneNameFromLimb(String limbKey) {
        if (limbKey == null) return null;
        if (limbKey.startsWith("player_model:")) {
            limbKey = limbKey.substring("player_model:".length());
        }
        return switch (limbKey) {
            case "left_arm" -> "left_arm";
            case "right_arm" -> "right_arm";
            case "left_leg" -> "left_leg";
            case "right_leg" -> "right_leg";
            case "head" -> "head";
            case "torso", "body" -> "body";
            default -> null;
        };
    }

    private static void renderRamp(Map<String, Object> props, SceneSessionManager session, SceneObject selected, String key, String label) {
        ImGui.separator();
        ImGui.textDisabled(label);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> ramp = props.get(key) instanceof List<?> l ? new java.util.ArrayList<>((List<Map<String, Object>>) l) : new java.util.ArrayList<>();
        int remove = -1;
        for (int i = 0; i < ramp.size(); i++) {
            Map<String, Object> kf = ramp.get(i);
            float t = toFloat(kf.getOrDefault("t", 0f));
            float v = toFloat(kf.getOrDefault("value", 0f));
            ImGui.pushID(label + i);
            float[] tBuf = new float[]{t};
            float[] vBuf = new float[]{v};
            ImGui.dragFloat("t##" + i, tBuf, 0.01f, 0f, 1f, "%.2f");
            ImGui.sameLine();
            ImGui.dragFloat("val##" + i, vBuf, 0.05f, -1000f, 1000f, "%.2f");
            ramp.set(i, Map.of("t", tBuf[0], "value", vBuf[0]));
            ImGui.sameLine();
            if (ImGui.button("X##" + i)) remove = i;
            ImGui.popID();
        }
        if (remove >= 0) {
            ramp.remove(remove);
        }
        if (ImGui.button("Add key##" + key)) {
            ramp.add(Map.of("t", 0f, "value", 0f));
        }
        props.put(key, ramp);
        session.submitPropertyUpdate(selected.getId(), props);
    }

    private static void renderColorRamp(Map<String, Object> props, SceneSessionManager session, SceneObject selected, String key, String label) {
        ImGui.separator();
        ImGui.textDisabled(label);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> ramp = props.get(key) instanceof List<?> l ? new java.util.ArrayList<>((List<Map<String, Object>>) l) : new java.util.ArrayList<>();
        int remove = -1;
        for (int i = 0; i < ramp.size(); i++) {
            Map<String, Object> kf = ramp.get(i);
            float t = toFloat(kf.getOrDefault("t", 0f));
            float r = toFloat(kf.getOrDefault("r", 1f));
            float g = toFloat(kf.getOrDefault("g", 1f));
            float b = toFloat(kf.getOrDefault("b", 1f));
            float a = toFloat(kf.getOrDefault("a", 1f));
            ImGui.pushID(label + i);
            float[] tBuf = new float[]{t};
            ImGui.dragFloat("t##" + i, tBuf, 0.01f, 0f, 1f, "%.2f");
            float[] color = new float[]{r, g, b, a};
            ImGui.colorEdit4("rgba##" + i, color);
            ramp.set(i, Map.of("t", tBuf[0], "r", color[0], "g", color[1], "b", color[2], "a", color[3]));
            ImGui.sameLine();
            if (ImGui.button("X##" + i)) remove = i;
            ImGui.popID();
        }
        if (remove >= 0) {
            ramp.remove(remove);
        }
        if (ImGui.button("Add color key##" + key)) {
            ramp.add(Map.of("t", 0f, "r", 1f, "g", 1f, "b", 1f, "a", 1f));
        }
        props.put(key, ramp);
        session.submitPropertyUpdate(selected.getId(), props);
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

    private static boolean bool(Object value, boolean def) {
        if (value instanceof Boolean b) return b;
        if (value instanceof Number n) return n.intValue() != 0;
        if (value != null) return Boolean.parseBoolean(String.valueOf(value));
        return def;
    }

    private static <E extends Enum<E>> E enumOrDefault(Object raw, E def, Class<E> type) {
        if (raw == null) return def;
        try {
            if (raw instanceof String s) {
                return Enum.valueOf(type, s.toUpperCase(Locale.ROOT));
            }
            if (raw instanceof Number n) {
                E[] values = type.getEnumConstants();
                int idx = n.intValue();
                if (idx >= 0 && idx < values.length) return values[idx];
            }
        } catch (Exception ignored) {
        }
        return def;
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
        String selectedLimb = overlay.getSelectedLimbType();

        if (runtimeObject != null && selectedLimb != null && runtimeObject.getType() == RuntimeObjectType.PLAYER_MODEL) {
            renderLimbInspector(runtimeObject, selectedLimb);
            ImGui.end();
            return;
        }
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

    private void renderLimbInspector(RuntimeObject runtime, String limb) {
        ImGui.textColored(ImGui.getColorU32(ImGuiCol.PlotHistogram), "LIMB MODE");
        ImGui.text("Bone: " + limb);
        ImGui.separator();

        long modelId = parseModelId(runtime.getObjectId());

        AbstractClientPlayerEntity playerEntity = null;

        OtherClientPlayerEntity fakePlayer = ClientFakePlayerManager.getInstance().getFakePlayer(modelId);
        if (fakePlayer != null) {
            playerEntity = fakePlayer;
        } else {
            var animModel = ClientPlayerModelManager.getInstance().getModel(modelId);
            if (animModel != null) {
                playerEntity = animModel.getEntity();
            }
        }

        if (playerEntity == null) {
            ImGui.textColored(0xFF0000FF, "Model data not found");
            return;
        }

        var uuid = playerEntity.getUuid();
        String boneName = boneNameFromLimb(limb);
        PlayerPartConfigManager.PartConfig config = boneName != null
                ? PlayerPartConfigManager.getInstance().getPartConfig(uuid, boneName)
                : null;

        float[] pos = {0, 0, 0};
        float[] rot = {0, 0, 0};
        float[] scale = {1, 1, 1};

        if (config != null) {
            if (config.position != null) {
                pos[0] = (float) config.position.x;
                pos[1] = (float) config.position.y;
                pos[2] = (float) config.position.z;
            }
            if (config.rotation != null) {
                rot[0] = (float) config.rotation.x;
                rot[1] = (float) config.rotation.y;
                rot[2] = (float) config.rotation.z;
            }
            if (config.scale != null) {
                scale[0] = (float) config.scale.x;
                scale[1] = (float) config.scale.y;
                scale[2] = (float) config.scale.z;
            }
        }

        boolean changed = false;
        ImGui.textDisabled("Offset Position (Pixels)");
        if (ImGui.dragFloat3("##limb_pos", pos, 0.1f)) changed = true;
        ImGui.textDisabled("Offset Rotation (Degrees)");
        if (ImGui.dragFloat3("##limb_rot", rot, 0.5f)) changed = true;
        ImGui.textDisabled("Scale");
        if (ImGui.dragFloat3("##limb_scale", scale, 0.01f)) changed = true;
        if (ImGui.button("Reset Limb")) {
            pos[0] = pos[1] = pos[2] = 0f;
            rot[0] = rot[1] = rot[2] = 0f;
            scale[0] = scale[1] = scale[2] = 1f;
            changed = true;
        }

        if (changed && boneName != null) {
            float[] t = {pos[0], pos[1], pos[2]};
            float[] r = {rot[0], rot[1], rot[2]};
            float[] s = {scale[0], scale[1], scale[2]};
            overlay.applyLimbTransform(runtime, limb, t, r, s);
        }
    }

    public void onSelectionChanged(SceneObject object) {
        currentObjectId = object != null ? object.getId() : null;
        if (object == null) {
            resetBuffers();
            return;
        }
        Map<String, Object> props = object.getProperties();
        String type = object.getType() == null ? "" : object.getType().toLowerCase(Locale.ROOT);
        inspectorLabel.set(String.valueOf(props.getOrDefault("label", object.getId())));
        markerLabelBuffer.set(inspectorLabel.get());
        modelPathBuffer.set(String.valueOf(props.getOrDefault("modelPath", "")));
        texturePathBuffer.set(String.valueOf(props.getOrDefault("texture", "")));
        modelAnimationClipBuffer.set(String.valueOf(props.getOrDefault("animationClip", "")));
        modelAnimationPlaying.set(boolValue(props.get("animationPlaying"), false));
        modelAnimationLoop.set(boolValue(props.get("animationLoop"), true));
        modelAnimationSpeed.set(toFloat(props.getOrDefault("animationSpeed", 1f)));
        modelAnimationTime.set(toFloat(props.getOrDefault("animationTime", 0f)));
        displayContentBuffer.set(String.valueOf(props.getOrDefault("displayContent", "")));
        displayTypeBuffer.set(String.valueOf(props.getOrDefault("displayType", "image")));
        displayLoopToggle.set(boolValue(props.get("loop"), true));
        displayPlayingToggle.set(boolValue(props.get("playing"), true));
        displayFrameRateValue.set(toFloat(props.getOrDefault("frameRate", 24f)));
        displayPbrEnabled.set(boolValue(props.get("pbrEnabled"), false));
        displayPbrBaseColor.set(String.valueOf(props.getOrDefault("pbrBaseColor", "")));
        displayPbrNormal.set(String.valueOf(props.getOrDefault("pbrNormal", "")));
        displayPbrMetallicRoughness.set(String.valueOf(props.getOrDefault("pbrMetallicRoughness", "")));
        displayPbrEmissive.set(String.valueOf(props.getOrDefault("pbrEmissive", "")));
        displayPbrOcclusion.set(String.valueOf(props.getOrDefault("pbrOcclusion", "")));
        displayPbrMetallicFactor.set(toFloat(props.getOrDefault("pbrMetallicFactor", 0f)));
        displayPbrRoughnessFactor.set(toFloat(props.getOrDefault("pbrRoughnessFactor", 1f)));
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
        playerSkinBuffer.set(String.valueOf(props.getOrDefault("skinUrl", overlay.getDefaultPlayerModelSkin())));
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

        if ("primitive".equals(type)) {
            primitiveTypeIndex.set(resolvePrimitiveTypeIndex(String.valueOf(props.getOrDefault("primitiveType", "cube"))));

            @SuppressWarnings("unchecked")
            Map<String, Object> material = props.get("material") instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
            primitiveColor[0] = material != null ? toFloat(material.getOrDefault("r", 1f)) : 1f;
            primitiveColor[1] = material != null ? toFloat(material.getOrDefault("g", 1f)) : 1f;
            primitiveColor[2] = material != null ? toFloat(material.getOrDefault("b", 1f)) : 1f;
            primitiveColor[3] = material != null ? toFloat(material.getOrDefault("a", 1f)) : 1f;
            primitiveUnlit.set(material != null && boolValue(material.get("unlit"), false));
            primitiveDoubleSided.set(material != null && boolValue(material.get("doubleSided"), false));
            primitiveRenderThroughBlocks.set(material != null && boolValue(material.get("renderThroughBlocks"), false));
            Object textureValue = material != null ? material.get("texture") : null;
            if (textureValue == null || String.valueOf(textureValue).isBlank()) {
                textureValue = props.get("texture");
            }
            primitiveTextureBuffer.set(textureValue != null ? String.valueOf(textureValue) : "");

            @SuppressWarnings("unchecked")
            Map<String, Object> physics = props.get("physics") instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
            primitiveDynamic.set(physics != null && boolValue(physics.get("dynamic"), false));
            primitiveMass.set(physics != null ? toFloat(physics.getOrDefault("mass", 1.0f)) : 1.0f);
        }
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
            case "particle_emitter" -> renderParticleEmitterProperties(session, selected, props);
            case "post_effect" -> renderPostEffectProperties(session, selected, props);
            case "zone" -> renderZoneProperties(session, selected, props);
            case "primitive" -> renderPrimitiveProperties(session, selected);
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

        renderModelAnimationSection(session, selected);
    }

    private void renderModelAnimationSection(SceneSessionManager session, SceneObject selected) {
        if (session == null || selected == null) {
            return;
        }

        if (!ImGui.collapsingHeader("Animation", ImGuiTreeNodeFlags.DefaultOpen)) {
            return;
        }

        Long modelId = SceneSelectionManager.getInstance().getBindingForObject(selected.getId());
        RenderableModel runtimeModel = modelId != null ? ClientModelManager.getInstance().getModel(modelId) : null;
        String modelPath = modelPathBuffer.get() != null ? modelPathBuffer.get().trim() : "";
        AnimationLookup animationsLookup = lookupAnimations(modelPath, runtimeModel);
        List<RenderableModel.AnimationInfo> animations = animationsLookup.animations();

        String clip = modelAnimationClipBuffer.get() != null ? modelAnimationClipBuffer.get() : "";
        String preview = clip.isBlank() ? "(none)" : clip;

        if (animationsLookup.loading()) {
            ImGui.textDisabled("Loading animations...");
        } else if (animationsLookup.error() != null && !animationsLookup.error().isBlank()) {
            ImGui.textColored(1f, 0.55f, 0.55f, 1f, "Failed to read animations: " + animationsLookup.error());
        }

        if (!modelPath.isBlank() && modelPath.toLowerCase(Locale.ROOT).endsWith(".glb")) {
            ImGui.sameLine();
            if (ImGui.smallButton("Refresh##glb_animations")) {
                glbAnimationCache.remove(modelPath);
                glbAnimationErrors.remove(modelPath);
                CompletableFuture<List<RenderableModel.AnimationInfo>> existing = glbAnimationLoads.remove(modelPath);
                if (existing != null) {
                    existing.cancel(true);
                }
                animationsLookup = lookupAnimations(modelPath, runtimeModel);
                animations = animationsLookup.animations();
            }
        }

        if (!animations.isEmpty()) {
            if (ImGui.beginCombo("Clip", preview)) {
                for (RenderableModel.AnimationInfo info : animations) {
                    if (info == null) {
                        continue;
                    }
                    String name = info.name();
                    boolean selectedClip = name != null && name.equalsIgnoreCase(clip);
                    if (ImGui.selectable(name, selectedClip)) {
                        modelAnimationClipBuffer.set(name);
                        modelAnimationTime.set(0f);
                        Map<String, Object> update = new ConcurrentHashMap<>();
                        update.put("animationClip", name);
                        update.put("animationTime", 0f);
                        session.submitPropertyUpdate(selected.getId(), update);
                    }
                    if (selectedClip) {
                        ImGui.setItemDefaultFocus();
                    }
                }
                ImGui.endCombo();
            }
        } else {
            ImGui.inputTextWithHint("Clip", "animation name", modelAnimationClipBuffer);
        }

        boolean hasClip = modelAnimationClipBuffer.get() != null && !modelAnimationClipBuffer.get().trim().isEmpty();
        if (ImGui.button(modelAnimationPlaying.get() ? "Pause##model_animation" : "Play##model_animation")) {
            boolean next = !modelAnimationPlaying.get();
            modelAnimationPlaying.set(next);
            Map<String, Object> update = new ConcurrentHashMap<>();
            update.put("animationPlaying", next);
            session.submitPropertyUpdate(selected.getId(), update);
        }
        ImGui.sameLine();
        if (ImGui.checkbox("Playing", modelAnimationPlaying)) {
            Map<String, Object> update = new ConcurrentHashMap<>();
            update.put("animationPlaying", modelAnimationPlaying.get());
            session.submitPropertyUpdate(selected.getId(), update);
        }
        if (ImGui.checkbox("Loop", modelAnimationLoop)) {
            Map<String, Object> update = new ConcurrentHashMap<>();
            update.put("animationLoop", modelAnimationLoop.get());
            session.submitPropertyUpdate(selected.getId(), update);
        }

        if (ImGui.dragFloat("Speed", modelAnimationSpeed.getData(), 0.05f, 0f, 8f, "%.2f")) {
            Map<String, Object> update = new ConcurrentHashMap<>();
            update.put("animationSpeed", modelAnimationSpeed.get());
            session.submitPropertyUpdate(selected.getId(), update);
        }

        float duration = 0f;
        if (hasClip && animations != null && !animations.isEmpty()) {
            for (RenderableModel.AnimationInfo info : animations) {
                if (info != null && info.name() != null && info.name().equalsIgnoreCase(modelAnimationClipBuffer.get())) {
                    duration = Math.max(0f, info.durationSeconds());
                    break;
                }
            }
        }
        float maxTime = duration > 0f ? duration : 10f;
        if (ImGui.dragFloat("Time (s)", modelAnimationTime.getData(), 0.02f, 0f, maxTime, "%.2f")) {
            Map<String, Object> update = new ConcurrentHashMap<>();
            update.put("animationTime", modelAnimationTime.get());
            session.submitPropertyUpdate(selected.getId(), update);
        }

        if (ImGui.button("Stop Animation")) {
            modelAnimationClipBuffer.set("");
            modelAnimationPlaying.set(false);
            modelAnimationLoop.set(true);
            modelAnimationSpeed.set(1f);
            modelAnimationTime.set(0f);
            Map<String, Object> update = new ConcurrentHashMap<>();
            update.put("animationClip", "");
            update.put("animationPlaying", false);
            update.put("animationLoop", true);
            update.put("animationSpeed", 1f);
            update.put("animationTime", 0f);
            session.submitPropertyUpdate(selected.getId(), update);
        }

        if (modelId == null) {
            ImGui.textDisabled("Tip: runtime model binding pending (spawn/sync)...");
        } else if (runtimeModel == null) {
            ImGui.textDisabled("Tip: runtime model not loaded on client yet.");
        } else if (runtimeModel.getModelPath() != null && !runtimeModel.getModelPath().toLowerCase(Locale.ROOT).endsWith(".glb")) {
            ImGui.textDisabled("Tip: only GLB models have animations.");
        } else if (runtimeModel.hasAnimations() && !hasClip) {
            ImGui.textDisabled("Tip: select a clip to scrub in-place.");
        }
    }

    private AnimationLookup lookupAnimations(String modelPath, RenderableModel runtimeModel) {
        if (runtimeModel != null) {
            List<RenderableModel.AnimationInfo> runtimeAnimations = runtimeModel.getAnimations();
            if (runtimeAnimations != null && !runtimeAnimations.isEmpty()) {
                return new AnimationLookup(runtimeAnimations, false, null);
            }
        }

        if (modelPath == null) {
            return new AnimationLookup(List.of(), false, null);
        }
        String trimmed = modelPath.trim();
        if (trimmed.isBlank() || !trimmed.toLowerCase(Locale.ROOT).endsWith(".glb")) {
            return new AnimationLookup(List.of(), false, null);
        }

        List<RenderableModel.AnimationInfo> cached = glbAnimationCache.get(trimmed);
        if (cached != null) {
            return new AnimationLookup(cached, false, glbAnimationErrors.get(trimmed));
        }

        CompletableFuture<List<RenderableModel.AnimationInfo>> inFlight = glbAnimationLoads.get(trimmed);
        if (inFlight == null) {
            glbAnimationLoads.computeIfAbsent(trimmed, key -> CompletableFuture.<List<RenderableModel.AnimationInfo>>supplyAsync(() -> {
                try {
                    glbAnimationErrors.remove(key);
                    return loadGlbAnimations(key);
                } catch (Exception e) {
                    glbAnimationErrors.put(key, e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
                    return List.<RenderableModel.AnimationInfo>of();
                }
            }).whenComplete((list, error) -> {
                if (error != null) {
                    glbAnimationErrors.put(trimmed, error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName());
                    glbAnimationCache.put(trimmed, List.<RenderableModel.AnimationInfo>of());
                } else {
                    glbAnimationCache.put(trimmed, list != null ? list : List.<RenderableModel.AnimationInfo>of());
                }
                glbAnimationLoads.remove(trimmed);
            }));
        }

        return new AnimationLookup(List.of(), true, glbAnimationErrors.get(trimmed));
    }

    private List<RenderableModel.AnimationInfo> loadGlbAnimations(String modelPath) throws Exception {
        Identifier identifier = IdentifierUtils.resolveModelIdentifier(modelPath);
        if (identifier == null) {
            return List.of();
        }
        Optional<Resource> resource = MinecraftClient.getInstance().getResourceManager().getResource(identifier);
        if (resource.isEmpty()) {
            return List.of();
        }

        List<RenderableModel.AnimationInfo> infos = new ArrayList<>();
        try (InputStream inputStream = resource.get().getInputStream()) {
            List<GltfSkinnedModelLoader.LoadedAnimation> animations = GltfSkinnedModelLoader.readAnimations(inputStream);
            if (animations == null || animations.isEmpty()) {
                return List.of();
            }
            for (GltfSkinnedModelLoader.LoadedAnimation anim : animations) {
                if (anim == null || anim.name() == null || anim.name().isBlank()) {
                    continue;
                }
                infos.add(new RenderableModel.AnimationInfo(anim.name(), anim.duration(), anim.channelCount()));
            }
        }
        return infos;
    }


    private void renderPostEffectProperties(SceneSessionManager session, SceneObject selected, Map<String, Object> props) {
        ImGui.textDisabled("Post Effect");
        ImGui.separator();

        String currentId = String.valueOf(props.getOrDefault("effectId", "veil:fog"));
        boolean isHeightFog = currentId.equalsIgnoreCase("veil:height_fog");
        boolean isSsr = currentId.equalsIgnoreCase("moud:ssr");
        boolean typeChanged = false;

        if (ImGui.beginCombo("Effect", currentId)) {
            if (ImGui.selectable("veil:fog", currentId.equalsIgnoreCase("veil:fog"))) {
                currentId = "veil:fog";
                typeChanged = true;
            }
            if (ImGui.selectable("veil:height_fog", currentId.equalsIgnoreCase("veil:height_fog"))) {
                currentId = "veil:height_fog";
                typeChanged = true;
            }
            if (ImGui.selectable("moud:ssr", currentId.equalsIgnoreCase("moud:ssr"))) {
                currentId = "moud:ssr";
                typeChanged = true;
            }
            ImGui.endCombo();
        }

        if (typeChanged) {
            props.put("effectId", currentId);
            Map<String, Object> defaults = currentId.equals("veil:height_fog")
                    ? PostEffectUniformRegistry.defaultHeightFog()
                    : (currentId.equals("moud:ssr") ? PostEffectUniformRegistry.defaultSsr() : PostEffectUniformRegistry.defaultFog());

            updateUniformBuffers(defaults, currentId);

            props.put("uniforms", defaults);
            session.submitPropertyUpdate(selected.getId(), props);
            return;
        }

        Map<String, Object> uniforms = props.get("uniforms") instanceof Map<?, ?> m
                ? new ConcurrentHashMap<>((Map<String, Object>) m)
                : new ConcurrentHashMap<>();

        updateUniformBuffers(uniforms, currentId);

        boolean valuesChanged = false;

        if (isSsr) {
            if (ImGui.dragFloat("Strength", ssrStrength.getData(), 0.01f, 0f, 2f, "%.2f")) {
                uniforms.put("SsrStrength", ssrStrength.get());
                valuesChanged = true;
            }
            if (ImGui.dragFloat("Max Distance", ssrMaxDistance.getData(), 0.5f, 1f, 256f, "%.1f")) {
                uniforms.put("SsrMaxDistance", ssrMaxDistance.get());
                valuesChanged = true;
            }
            if (ImGui.dragFloat("Step Size", ssrStepSize.getData(), 0.01f, 0.02f, 4f, "%.2f")) {
                uniforms.put("SsrStepSize", ssrStepSize.get());
                valuesChanged = true;
            }
            if (ImGui.dragFloat("Thickness", ssrThickness.getData(), 0.0002f, 0.0002f, 0.05f, "%.4f")) {
                uniforms.put("SsrThickness", ssrThickness.get());
                valuesChanged = true;
            }
            if (ImGui.dragFloat("Edge Fade", ssrEdgeFade.getData(), 0.01f, 0.01f, 0.5f, "%.2f")) {
                uniforms.put("SsrEdgeFade", ssrEdgeFade.get());
                valuesChanged = true;
            }
        } else if (!isHeightFog) {
            if (ImGui.dragFloat("Fog Start", fogStart.getData(), 1f)) {
                uniforms.put("FogStart", fogStart.get());
                valuesChanged = true;
            }
            if (ImGui.dragFloat("Fog End", fogEnd.getData(), 1f)) {
                uniforms.put("FogEnd", fogEnd.get());
                valuesChanged = true;
            }
            if (ImGui.sliderInt("Fog Shape (0=linear,1=exp)", fogShape.getData(), 0, 1)) {
                uniforms.put("FogShape", fogShape.get());
                valuesChanged = true;
            }
            if (ImGui.colorEdit4("Fog Color", fogColor)) {
                uniforms.put("FogColor", Map.of("r", fogColor[0], "g", fogColor[1], "b", fogColor[2], "a", fogColor[3]));
                valuesChanged = true;
            }
        } else {
            if (ImGui.dragFloat("Fog Height (FOG_Y)", heightFogY.getData(), 1f)) {
                uniforms.put("FOG_Y", heightFogY.get());
                valuesChanged = true;
            }
            if (ImGui.dragFloat("Thickness", heightFogThickness.getData(), 0.01f, 0.01f, 4f)) {
                uniforms.put("THICKNESS", heightFogThickness.get());
                valuesChanged = true;
            }
            if (ImGui.colorEdit4("Fog Color", heightFogColor)) {
                uniforms.put("FogColor", Map.of("r", heightFogColor[0], "g", heightFogColor[1], "b", heightFogColor[2], "a", heightFogColor[3]));
                valuesChanged = true;
            }
        }

        if (valuesChanged) {
            props.put("uniforms", uniforms);
            session.submitPropertyUpdate(selected.getId(), props);
        }
    }

    private void renderZoneProperties(SceneSessionManager session, SceneObject selected, Map<String, Object> props) {
        ImGui.textDisabled("Zone");
        ImGui.separator();
        float[] c1 = extractVector(props.get("corner1"), new float[]{0, 0, 0});
        float[] c2 = extractVector(props.get("corner2"), new float[]{1, 1, 1});
        boolean changed = false;
        if (ImGui.dragFloat3("Corner 1", c1, 0.1f)) {
            changed = true;
        }
        if (ImGui.dragFloat3("Corner 2", c2, 0.1f)) {
            changed = true;
        }
        if (changed) {
            Map<String, Object> update = new ConcurrentHashMap<>();
            update.put("corner1", vectorToMap(c1));
            update.put("corner2", vectorToMap(c2));
            session.submitPropertyUpdate(selected.getId(), update);
            // update active gizmo scale to match new size
            overlay.setActiveScale(new float[]{Math.abs(c1[0] - c2[0]), Math.abs(c1[1] - c2[1]), Math.abs(c1[2] - c2[2])});
        }
        Vec3d center = new Vec3d((c1[0] + c2[0]) * 0.5, (c1[1] + c2[1]) * 0.5, (c1[2] + c2[2]) * 0.5);
        Vec3d size = new Vec3d(Math.abs(c1[0] - c2[0]), Math.abs(c1[1] - c2[1]), Math.abs(c1[2] - c2[2]));
        ImGui.text(String.format(Locale.ROOT, "Center: (%.2f, %.2f, %.2f)", center.x, center.y, center.z));
        ImGui.text(String.format(Locale.ROOT, "Size: (%.2f, %.2f, %.2f)", size.x, size.y, size.z));
        float[] sizeArr = new float[]{(float) size.x, (float) size.y, (float) size.z};
        if (ImGui.dragFloat3("Size", sizeArr, 0.1f, 0.01f, 512f)) {
            float[] newC1 = new float[]{(float) (center.x - sizeArr[0] * 0.5f), (float) (center.y - sizeArr[1] * 0.5f), (float) (center.z - sizeArr[2] * 0.5f)};
            float[] newC2 = new float[]{(float) (center.x + sizeArr[0] * 0.5f), (float) (center.y + sizeArr[1] * 0.5f), (float) (center.z + sizeArr[2] * 0.5f)};
            Map<String, Object> update = new ConcurrentHashMap<>();
            update.put("corner1", vectorToMap(newC1));
            update.put("corner2", vectorToMap(newC2));
            session.submitPropertyUpdate(selected.getId(), update);
            overlay.setActiveScale(sizeArr);
        }
        ImBoolean snapToggle = overlay.getZoneSnapToggle();
        ImFloat snapValue = overlay.getZoneSnapValue();
        if (ImGui.checkbox("Snap zone placement", snapToggle)) {
            // no-op, persisted in overlay fields
        }
        ImGui.sameLine();
        if (ImGui.dragFloat("Snap step", snapValue.getData(), 0.1f, 0.05f, 50f, "%.2f")) {
            snapValue.set(Math.max(0.05f, snapValue.get()));
        }
    }

    private void renderPrimitiveProperties(SceneSessionManager session, SceneObject selected) {
        ImGui.textDisabled("Primitive");
        ImGui.separator();

        boolean[] changed = new boolean[]{false};
        if (ImGui.combo("Shape", primitiveTypeIndex, PRIMITIVE_TYPE_LABELS)) {
            changed[0] = true;
        }

        ImGui.separator();
        ImGui.textDisabled("Material");
        if (ImGui.colorEdit4("Color", primitiveColor)) {
            changed[0] = true;
        }
        if (ImGui.checkbox("Unlit", primitiveUnlit)) {
            changed[0] = true;
        }
        ImGui.sameLine();
        if (ImGui.checkbox("Double Sided", primitiveDoubleSided)) {
            changed[0] = true;
        }
        ImGui.sameLine();
        if (ImGui.checkbox("Render Through Blocks", primitiveRenderThroughBlocks)) {
            changed[0] = true;
        }

        if (ImGui.inputText("Texture", primitiveTextureBuffer)) {
            changed[0] = true;
        }
        ImGui.sameLine();
        if (ImGui.button("Pick##primitive_texture_picker")) {
            ImGui.openPopup("primitive_texture_picker");
        }
        String pickedPrimitiveTexture = renderProjectFilePickerPopup(
                "primitive_texture_picker",
                "Select Primitive Texture",
                primitiveTextureFilePickerFilter,
                node -> hasExtension(node.path(), ".png", ".jpg", ".jpeg")
        );
        if (pickedPrimitiveTexture != null) {
            primitiveTextureBuffer.set(toResourcePath(pickedPrimitiveTexture));
            changed[0] = true;
        }
        acceptProjectFileDrop(value -> {
            primitiveTextureBuffer.set(value);
            changed[0] = true;
        }, true);
        if (ImGui.button("Clear Texture##primitive_texture_clear")) {
            primitiveTextureBuffer.set("");
            changed[0] = true;
        }

        ImGui.separator();
        ImGui.textDisabled("Physics");
        if (ImGui.checkbox("Dynamic", primitiveDynamic)) {
            changed[0] = true;
        }
        if (primitiveDynamic.get()) {
            if (ImGui.dragFloat("Mass", primitiveMass.getData(), 0.05f, 0.05f, 1000f, "%.2f")) {
                primitiveMass.set(Math.max(0.05f, primitiveMass.get()));
                changed[0] = true;
            }
        }

        if (!changed[0]) {
            return;
        }

        Map<String, Object> before = history.snapshot(selected);
        Map<String, Object> patch = new ConcurrentHashMap<>();
        patch.put("primitiveType", resolvePrimitiveTypeValue(primitiveTypeIndex.get()));

        Map<String, Object> material = new ConcurrentHashMap<>();
        material.put("r", primitiveColor[0]);
        material.put("g", primitiveColor[1]);
        material.put("b", primitiveColor[2]);
        material.put("a", primitiveColor[3]);
        String texture = primitiveTextureBuffer.get();
        if (texture != null && !texture.isBlank()) {
            material.put("texture", texture);
        }
        material.put("unlit", primitiveUnlit.get());
        material.put("doubleSided", primitiveDoubleSided.get());
        material.put("renderThroughBlocks", primitiveRenderThroughBlocks.get());
        patch.put("material", material);

        Map<String, Object> physics = new ConcurrentHashMap<>();
        physics.put("dynamic", primitiveDynamic.get());
        physics.put("mass", primitiveMass.get());
        patch.put("physics", physics);

        session.submitPropertyUpdate(selected.getId(), patch);
        Map<String, Object> after = new ConcurrentHashMap<>(before);
        after.putAll(patch);
        history.recordDiscreteChange(selected.getId(), before, after);
    }

    private static int resolvePrimitiveTypeIndex(String token) {
        if (token == null) {
            return 0;
        }
        String normalized = token.trim().toLowerCase(Locale.ROOT);
        for (int i = 0; i < PRIMITIVE_TYPE_VALUES.length; i++) {
            if (PRIMITIVE_TYPE_VALUES[i].equals(normalized)) {
                return i;
            }
        }
        return 0;
    }

    private static String resolvePrimitiveTypeValue(int index) {
        if (index < 0 || index >= PRIMITIVE_TYPE_VALUES.length) {
            return PRIMITIVE_TYPE_VALUES[0];
        }
        return PRIMITIVE_TYPE_VALUES[index];
    }

    private void updateUniformBuffers(Map<String, Object> uniforms, String effectId) {
        boolean isHeightFog = effectId != null && effectId.equalsIgnoreCase("veil:height_fog");
        boolean isSsr = effectId != null && effectId.equalsIgnoreCase("moud:ssr");
        if (isSsr) {
            if (uniforms.containsKey("SsrStrength")) ssrStrength.set(toFloat(uniforms.get("SsrStrength")));
            if (uniforms.containsKey("SsrMaxDistance")) ssrMaxDistance.set(toFloat(uniforms.get("SsrMaxDistance")));
            if (uniforms.containsKey("SsrStepSize")) ssrStepSize.set(toFloat(uniforms.get("SsrStepSize")));
            if (uniforms.containsKey("SsrThickness")) ssrThickness.set(toFloat(uniforms.get("SsrThickness")));
            if (uniforms.containsKey("SsrEdgeFade")) ssrEdgeFade.set(toFloat(uniforms.get("SsrEdgeFade")));
            return;
        }
        if (!isHeightFog) {
            if (uniforms.containsKey("FogStart")) fogStart.set(toFloat(uniforms.get("FogStart")));
            if (uniforms.containsKey("FogEnd")) fogEnd.set(toFloat(uniforms.get("FogEnd")));
            if (uniforms.containsKey("FogColor")) extractColor(uniforms.get("FogColor"), fogColor);
            if (uniforms.containsKey("FogShape")) fogShape.set((int) toFloat(uniforms.get("FogShape")));
        } else {
            if (uniforms.containsKey("FOG_Y")) heightFogY.set(toFloat(uniforms.get("FOG_Y")));
            if (uniforms.containsKey("THICKNESS")) heightFogThickness.set(toFloat(uniforms.get("THICKNESS")));
            if (uniforms.containsKey("FogColor")) extractColor(uniforms.get("FogColor"), heightFogColor);
        }
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

        if (ImGui.collapsingHeader("PBR", ImGuiTreeNodeFlags.DefaultOpen)) {
            if (ImGui.checkbox("Enable PBR", displayPbrEnabled)) {
                Map<String, Object> update = new ConcurrentHashMap<>();
                update.put("pbrEnabled", displayPbrEnabled.get());
                session.submitPropertyUpdate(selected.getId(), update);
            }

            if (displayPbrEnabled.get()) {
                if (ImGui.inputText("Base Color", displayPbrBaseColor)) {
                    Map<String, Object> update = new ConcurrentHashMap<>();
                    update.put("pbrBaseColor", displayPbrBaseColor.get());
                    session.submitPropertyUpdate(selected.getId(), update);
                }
                if (ImGui.button("Pick##pbr_basecolor_picker")) {
                    ImGui.openPopup("pbr_basecolor_picker");
                }
                String picked = renderProjectFilePickerPopup(
                        "pbr_basecolor_picker",
                        "Select Base Color Texture",
                        textureFilePickerFilter,
                        node -> hasExtension(node.path(), ".png", ".jpg", ".jpeg")
                );
                if (picked != null) {
                    String value = toResourcePath(picked);
                    displayPbrBaseColor.set(value);
                    Map<String, Object> update = new ConcurrentHashMap<>();
                    update.put("pbrBaseColor", value);
                    session.submitPropertyUpdate(selected.getId(), update);
                }

                if (ImGui.inputText("Normal", displayPbrNormal)) {
                    Map<String, Object> update = new ConcurrentHashMap<>();
                    update.put("pbrNormal", displayPbrNormal.get());
                    session.submitPropertyUpdate(selected.getId(), update);
                }
                if (ImGui.button("Pick##pbr_normal_picker")) {
                    ImGui.openPopup("pbr_normal_picker");
                }
                picked = renderProjectFilePickerPopup(
                        "pbr_normal_picker",
                        "Select Normal Texture",
                        textureFilePickerFilter,
                        node -> hasExtension(node.path(), ".png", ".jpg", ".jpeg")
                );
                if (picked != null) {
                    String value = toResourcePath(picked);
                    displayPbrNormal.set(value);
                    Map<String, Object> update = new ConcurrentHashMap<>();
                    update.put("pbrNormal", value);
                    session.submitPropertyUpdate(selected.getId(), update);
                }

                if (ImGui.inputText("MetallicRoughness", displayPbrMetallicRoughness)) {
                    Map<String, Object> update = new ConcurrentHashMap<>();
                    update.put("pbrMetallicRoughness", displayPbrMetallicRoughness.get());
                    session.submitPropertyUpdate(selected.getId(), update);
                }
                if (ImGui.button("Pick##pbr_mr_picker")) {
                    ImGui.openPopup("pbr_mr_picker");
                }
                picked = renderProjectFilePickerPopup(
                        "pbr_mr_picker",
                        "Select Metallic/Roughness Texture",
                        textureFilePickerFilter,
                        node -> hasExtension(node.path(), ".png", ".jpg", ".jpeg")
                );
                if (picked != null) {
                    String value = toResourcePath(picked);
                    displayPbrMetallicRoughness.set(value);
                    Map<String, Object> update = new ConcurrentHashMap<>();
                    update.put("pbrMetallicRoughness", value);
                    session.submitPropertyUpdate(selected.getId(), update);
                }

                if (ImGui.inputText("Emissive", displayPbrEmissive)) {
                    Map<String, Object> update = new ConcurrentHashMap<>();
                    update.put("pbrEmissive", displayPbrEmissive.get());
                    session.submitPropertyUpdate(selected.getId(), update);
                }
                if (ImGui.button("Pick##pbr_emissive_picker")) {
                    ImGui.openPopup("pbr_emissive_picker");
                }
                picked = renderProjectFilePickerPopup(
                        "pbr_emissive_picker",
                        "Select Emissive Texture",
                        textureFilePickerFilter,
                        node -> hasExtension(node.path(), ".png", ".jpg", ".jpeg")
                );
                if (picked != null) {
                    String value = toResourcePath(picked);
                    displayPbrEmissive.set(value);
                    Map<String, Object> update = new ConcurrentHashMap<>();
                    update.put("pbrEmissive", value);
                    session.submitPropertyUpdate(selected.getId(), update);
                }

                if (ImGui.inputText("Occlusion", displayPbrOcclusion)) {
                    Map<String, Object> update = new ConcurrentHashMap<>();
                    update.put("pbrOcclusion", displayPbrOcclusion.get());
                    session.submitPropertyUpdate(selected.getId(), update);
                }
                if (ImGui.button("Pick##pbr_occlusion_picker")) {
                    ImGui.openPopup("pbr_occlusion_picker");
                }
                picked = renderProjectFilePickerPopup(
                        "pbr_occlusion_picker",
                        "Select Occlusion Texture",
                        textureFilePickerFilter,
                        node -> hasExtension(node.path(), ".png", ".jpg", ".jpeg")
                );
                if (picked != null) {
                    String value = toResourcePath(picked);
                    displayPbrOcclusion.set(value);
                    Map<String, Object> update = new ConcurrentHashMap<>();
                    update.put("pbrOcclusion", value);
                    session.submitPropertyUpdate(selected.getId(), update);
                }

                if (ImGui.sliderFloat("Metallic Factor", displayPbrMetallicFactor.getData(), 0f, 1f, "%.2f")) {
                    Map<String, Object> update = new ConcurrentHashMap<>();
                    update.put("pbrMetallicFactor", displayPbrMetallicFactor.get());
                    session.submitPropertyUpdate(selected.getId(), update);
                }
                if (ImGui.sliderFloat("Roughness Factor", displayPbrRoughnessFactor.getData(), 0f, 1f, "%.2f")) {
                    Map<String, Object> update = new ConcurrentHashMap<>();
                    update.put("pbrRoughnessFactor", displayPbrRoughnessFactor.get());
                    session.submitPropertyUpdate(selected.getId(), update);
                }
            }
        }
    }

    private void renderParticleEmitterProperties(SceneSessionManager session, SceneObject selected, Map<String, Object> props) {
        ImGui.textDisabled("Particle Emitter");
        ImGui.separator();

        String texture = String.valueOf(props.getOrDefault("texture", "minecraft:particle/generic_0"));
        ImString texBuf = new ImString(texture, 256);
        if (ImGui.inputText("Texture", texBuf)) {
            props.put("texture", texBuf.get());
            session.submitPropertyUpdate(selected.getId(), props);
        }

        if (ImGui.collapsingHeader("Texture Pool", ImGuiTreeNodeFlags.DefaultOpen)) {
            @SuppressWarnings("unchecked")
            List<String> pool = props.get("textures") instanceof List<?> l ? new java.util.ArrayList<>( (List<String>) l) : new java.util.ArrayList<>();
            ImGui.textDisabled("List of textures to pick per spawn");
            int removeIdx = -1;
            for (int i = 0; i < pool.size(); i++) {
                ImGui.pushID(i);
                ImString tex = new ImString(pool.get(i), 256);
                if (ImGui.inputText("##tex", tex)) {
                    pool.set(i, tex.get());
                }
                ImGui.sameLine();
                if (ImGui.button("Remove")) {
                    removeIdx = i;
                }
                ImGui.popID();
            }
            if (removeIdx >= 0) {
                pool.remove(removeIdx);
            }
            if (ImGui.button("Add Texture")) {
                pool.add("minecraft:particle/generic_0");
            }
            props.put("textures", pool);
            session.submitPropertyUpdate(selected.getId(), props);
        }

        float[] rateBuf = new float[]{toFloat(props.getOrDefault("rate", props.getOrDefault("spawnRate", 10f)))};
        if (ImGui.dragFloat("Rate (pps)", rateBuf, 1f, 0f, 100000f, "%.0f")) {
            props.put("rate", Math.max(0f, rateBuf[0]));
            session.submitPropertyUpdate(selected.getId(), props);
        }

        int maxParticles = (int) toFloat(props.getOrDefault("maxParticles", 1024));
        ImInt maxBuf = new ImInt(maxParticles);
        if (ImGui.inputInt("Max Particles", maxBuf)) {
            props.put("maxParticles", Math.max(0, maxBuf.get()));
            session.submitPropertyUpdate(selected.getId(), props);
        }

        boolean enabled = bool(props.getOrDefault("enabled", Boolean.TRUE), true);
        ImBoolean enabledBuf = new ImBoolean(enabled);
        if (ImGui.checkbox("Enabled", enabledBuf)) {
            props.put("enabled", enabledBuf.get());
            session.submitPropertyUpdate(selected.getId(), props);
        }

        boolean collidePlayers = bool(props.getOrDefault("collideWithPlayers", Boolean.FALSE), false);
        ImBoolean collideBuf = new ImBoolean(collidePlayers);
        if (ImGui.checkbox("Collide with Players", collideBuf)) {
            props.put("collideWithPlayers", collideBuf.get());
            session.submitPropertyUpdate(selected.getId(), props);
        }

        int impostorSlices = (int) toFloat(props.getOrDefault("impostorSlices", 1));
        ImInt sliceBuf = new ImInt(impostorSlices);
        if (ImGui.inputInt("Impostor Slices", sliceBuf)) {
            props.put("impostorSlices", Math.max(1, sliceBuf.get()));
            session.submitPropertyUpdate(selected.getId(), props);
        }

        RenderType currentRender = enumOrDefault(props.get("renderType"), RenderType.TRANSLUCENT, RenderType.class);
        if (ImGui.beginCombo("Render Type", currentRender.name())) {
            for (RenderType rt : RenderType.values()) {
                boolean sel = rt == currentRender;
                if (ImGui.selectable(rt.name(), sel)) {
                    props.put("renderType", rt.name().toLowerCase(Locale.ROOT));
                    session.submitPropertyUpdate(selected.getId(), props);
                }
            }
            ImGui.endCombo();
        }

        Billboarding currentBillboard = enumOrDefault(props.get("billboarding"), Billboarding.CAMERA_FACING, Billboarding.class);
        if (ImGui.beginCombo("Billboarding", currentBillboard.name())) {
            for (Billboarding bb : Billboarding.values()) {
                boolean sel = bb == currentBillboard;
                if (ImGui.selectable(bb.name(), sel)) {
                    props.put("billboarding", bb.name().toLowerCase(Locale.ROOT));
                    session.submitPropertyUpdate(selected.getId(), props);
                }
            }
            ImGui.endCombo();
        }

        CollisionMode currentCollision = enumOrDefault(props.get("collision"), CollisionMode.NONE, CollisionMode.class);
        if (ImGui.beginCombo("Collision", currentCollision.name())) {
            for (CollisionMode cm : CollisionMode.values()) {
                boolean sel = cm == currentCollision;
                if (ImGui.selectable(cm.name(), sel)) {
                    props.put("collision", cm.name().toLowerCase(Locale.ROOT));
                    session.submitPropertyUpdate(selected.getId(), props);
                }
            }
            ImGui.endCombo();
        }

        float[] lifetimeBuf = new float[]{toFloat(props.getOrDefault("lifetime", 1f))};
        if (ImGui.dragFloat("Lifetime", lifetimeBuf, 0.05f, 0.01f, 120f, "%.2f")) {
            props.put("lifetime", Math.max(0.01f, lifetimeBuf[0]));
            session.submitPropertyUpdate(selected.getId(), props);
        }

        float[] gravityBuf = new float[]{toFloat(props.getOrDefault("gravityMultiplier", 1f))};
        if (ImGui.dragFloat("Gravity Mult", gravityBuf, 0.05f, -10f, 10f, "%.2f")) {
            props.put("gravityMultiplier", gravityBuf[0]);
            session.submitPropertyUpdate(selected.getId(), props);
        }

        float[] dragBuf = new float[]{toFloat(props.getOrDefault("drag", 0f))};
        if (ImGui.dragFloat("Drag", dragBuf, 0.05f, 0f, 10f, "%.2f")) {
            props.put("drag", Math.max(0f, dragBuf[0]));
            session.submitPropertyUpdate(selected.getId(), props);
        }

        ImGui.separator();
        ImGui.textDisabled("Light");
        Map<String, Object> light = props.get("light") instanceof Map<?, ?> lm ? new ConcurrentHashMap<>((Map<String, Object>) lm) : new ConcurrentHashMap<>();
        ImInt blockBuf = new ImInt((int) toFloat(light.getOrDefault("block", 0f)));
        ImInt skyBuf = new ImInt((int) toFloat(light.getOrDefault("sky", 0f)));
        ImBoolean emissive = new ImBoolean(bool(light.getOrDefault("emissive", false), false));
        if (ImGui.inputInt("Block Light", blockBuf) | ImGui.inputInt("Sky Light", skyBuf) | ImGui.checkbox("Emissive", emissive)) {
            light.put("block", Math.max(0, Math.min(15, blockBuf.get())));
            light.put("sky", Math.max(0, Math.min(15, skyBuf.get())));
            light.put("emissive", emissive.get());
            props.put("light", light);
            session.submitPropertyUpdate(selected.getId(), props);
        }

        ImGui.separator();
        ImGui.textDisabled("UV Region");
        Map<String, Object> uv = props.get("uvRegion") instanceof Map<?, ?> um ? new ConcurrentHashMap<>((Map<String, Object>) um) : new ConcurrentHashMap<>();
        float[] uvVals = new float[]{
                toFloat(uv.getOrDefault("u0", 0f)),
                toFloat(uv.getOrDefault("v0", 0f)),
                toFloat(uv.getOrDefault("u1", 1f)),
                toFloat(uv.getOrDefault("v1", 1f))
        };
        if (ImGui.dragFloat4("u0/v0/u1/v1", uvVals, 0.005f, 0f, 1f, "%.3f")) {
            uv.put("u0", uvVals[0]);
            uv.put("v0", uvVals[1]);
            uv.put("u1", uvVals[2]);
            uv.put("v1", uvVals[3]);
            props.put("uvRegion", uv);
            session.submitPropertyUpdate(selected.getId(), props);
        }

        ImGui.separator();
        ImGui.textDisabled("Frame Animation");
        Map<String, Object> frame = props.get("frameAnimation") instanceof Map<?, ?> fm ? new ConcurrentHashMap<>((Map<String, Object>) fm) : new ConcurrentHashMap<>();
        float[] frameVals = new float[]{
                toFloat(frame.getOrDefault("frames", 1f)),
                toFloat(frame.getOrDefault("fps", 0f)),
                toFloat(frame.getOrDefault("startFrame", 0f))
        };
        ImBoolean loopFrames = new ImBoolean(bool(frame.getOrDefault("loop", false), false));
        ImBoolean pingPong = new ImBoolean(bool(frame.getOrDefault("pingPong", false), false));
        if (ImGui.dragFloat3("Frames/FPS/Start", frameVals, 1f, 0f, 512f, "%.0f") | ImGui.checkbox("Loop##frame_loop", loopFrames) | ImGui.checkbox("PingPong##frame_ping", pingPong)) {
            frame.put("frames", Math.max(1, (int) frameVals[0]));
            frame.put("fps", Math.max(0f, frameVals[1]));
            frame.put("startFrame", Math.max(0, (int) frameVals[2]));
            frame.put("loop", loopFrames.get());
            frame.put("pingPong", pingPong.get());
            props.put("frameAnimation", frame);
            session.submitPropertyUpdate(selected.getId(), props);
        }

        ImGui.separator();
        ImGui.textDisabled("Behaviors");
        List<String> behaviors = props.get("behaviors") instanceof List<?> bl ? new java.util.ArrayList<>((List<String>) bl) : new java.util.ArrayList<>();
        int removeBeh = -1;
        for (int i = 0; i < behaviors.size(); i++) {
            ImGui.pushID("beh" + i);
            ImString bbuf = new ImString(behaviors.get(i), 128);
            if (ImGui.inputText("Behavior##" + i, bbuf)) {
                behaviors.set(i, bbuf.get());
            }
            ImGui.sameLine();
            if (ImGui.button("X##behdel" + i)) removeBeh = i;
            ImGui.popID();
        }
        if (removeBeh >= 0) behaviors.remove(removeBeh);
        if (ImGui.button("Add Behavior")) behaviors.add("example");
        props.put("behaviors", behaviors);
        session.submitPropertyUpdate(selected.getId(), props);

        ImGui.textDisabled("Behavior Payload (JSON)");
        String payloadStr = props.get("behaviorPayload") instanceof Map<?, ?> ? new com.google.gson.Gson().toJson(props.get("behaviorPayload")) : String.valueOf(props.getOrDefault("behaviorPayload", "{}"));
        ImString payloadBuf = new ImString(payloadStr, 512);
        if (ImGui.inputTextMultiline("##behaviorPayload", payloadBuf, 400, 80)) {
            try {
                Map<?, ?> json = new com.google.gson.Gson().fromJson(payloadBuf.get(), Map.class);
                if (json != null) {
                    props.put("behaviorPayload", json);
                    session.submitPropertyUpdate(selected.getId(), props);
                }
            } catch (Exception e) {
                ImGui.textColored(0xFFAA0000, "Invalid JSON");
            }
        }

        float[] posJitter = extractVector(props.getOrDefault("positionJitter", Map.of("x", 0f, "y", 0f, "z", 0f)), new float[]{0f, 0f, 0f});
        if (ImGui.dragFloat3("Position Jitter", posJitter, 0.01f)) {
            props.put("positionJitter", vectorToMap(posJitter));
            session.submitPropertyUpdate(selected.getId(), props);
        }

        float[] velJitter = extractVector(props.getOrDefault("velocityJitter", Map.of("x", 0f, "y", 0f, "z", 0f)), new float[]{0f, 0f, 0f});
        if (ImGui.dragFloat3("Velocity Jitter", velJitter, 0.01f)) {
            props.put("velocityJitter", vectorToMap(velJitter));
            session.submitPropertyUpdate(selected.getId(), props);
        }

        float[] lifetimeJitter = new float[]{toFloat(props.getOrDefault("lifetimeJitter", 0f))};
        if (ImGui.dragFloat("Lifetime Jitter", lifetimeJitter, 0.01f, 0f, 10f, "%.2f")) {
            props.put("lifetimeJitter", Math.max(0f, lifetimeJitter[0]));
            session.submitPropertyUpdate(selected.getId(), props);
        }

        float[] acceleration = extractVector(props.getOrDefault("acceleration", Map.of("x", 0f, "y", 0f, "z", 0f)), new float[]{0f, 0f, 0f});
        if (ImGui.dragFloat3("Acceleration", acceleration, 0.01f)) {
            props.put("acceleration", vectorToMap(acceleration));
            session.submitPropertyUpdate(selected.getId(), props);
        }

        renderRamp(props, session, selected, "sizeOverLife", "Size Over Life");
        renderRamp(props, session, selected, "rotationOverLife", "Rotation Over Life");
        renderRamp(props, session, selected, "alphaOverLife", "Alpha Over Life");
        renderColorRamp(props, session, selected, "colorOverLife", "Color Over Life");
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
        ImGui.textDisabled("Player Model");

        // hydrate buffers from props
        inspectorLabel.set(String.valueOf(props.getOrDefault("label", inspectorLabel.get())));
        playerSkinBuffer.set(String.valueOf(props.getOrDefault("skinUrl", overlay.getDefaultPlayerModelSkin())));
        playerAutoAnimation.set(boolValue(props.get("autoAnimation"), true));
        playerLoopAnimation.set(boolValue(props.get("loopAnimation"), true));
        Object duration = props.get("animationDuration");
        if (duration instanceof Number n) {
            playerAnimationDurationMs.set(n.intValue());
        }
        playerAnimationOverride.set(String.valueOf(props.getOrDefault("animationOverride", playerAnimationOverride.get())));

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
        playerSkinBuffer.set(overlay.getDefaultPlayerModelSkin());
        playerAnimationOverride.set("");
        playerAutoAnimation.set(true);
        playerLoopAnimation.set(true);
        playerAnimationDurationMs.set(2000);
        cameraPositionBuffer[0] = cameraPositionBuffer[1] = cameraPositionBuffer[2] = 0f;
        cameraRotationBuffer[0] = cameraRotationBuffer[1] = cameraRotationBuffer[2] = 0f;
        cameraFov.set(70f);
        cameraNear.set(0.1f);
        cameraFar.set(128f);
        primitiveTypeIndex.set(0);
        primitiveDynamic.set(false);
        primitiveMass.set(1.0f);
        primitiveUnlit.set(false);
        primitiveDoubleSided.set(false);
        primitiveRenderThroughBlocks.set(false);
        primitiveColor[0] = 1f;
        primitiveColor[1] = 1f;
        primitiveColor[2] = 1f;
        primitiveColor[3] = 1f;
        primitiveTextureBuffer.set("");
    }
}
