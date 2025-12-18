package com.moud.client.init;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.moud.client.MoudClientMod;
import com.moud.client.animation.AnimatedPlayerModel;
import com.moud.client.animation.ClientPlayerModelManager;
import com.moud.client.animation.ExternalPartConfigLayer;
import com.moud.client.animation.IAnimatedPlayer;
import com.moud.client.animation.PlayerPartConfigManager;
import com.moud.client.api.service.ClientAPIService;
import com.moud.client.collision.ClientCollisionManager;
import com.moud.client.collision.ModelCollisionManager;
import com.moud.client.display.ClientDisplayManager;
import com.moud.client.editor.runtime.RuntimeObjectRegistry;
import com.moud.client.editor.scene.SceneSessionManager;
import com.moud.client.editor.scene.blueprint.ClientBlueprintNetwork;
import com.moud.client.editor.ui.SceneEditorOverlay;
import com.moud.client.ik.ClientIKManager;
import com.moud.client.model.ClientModelManager;
import com.moud.client.model.RenderableModel;
import com.moud.client.network.ClientPacketReceiver;
import com.moud.client.network.ClientPacketWrapper;
import com.moud.client.network.DataPayload;
import com.moud.client.network.MoudPayload;
import com.moud.client.player.PlayerStateManager;
import com.moud.client.runtime.ClientScriptingRuntime;
import com.moud.client.shared.SharedValueManager;
import com.moud.client.ui.ServerUIOverlayManager;
import com.moud.client.util.WindowAnimator;
import com.moud.client.primitives.ClientPrimitiveManager;
import com.moud.network.MoudPackets;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ClientNetworkRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientNetworkRegistry.class);
    private static final Identifier DEFAULT_MODEL_TEXTURE = Identifier.of("minecraft", "textures/block/white_concrete.png");
    private final Gson builtinEventParser = new Gson();

    public void registerPackets(MoudClientMod mod, ClientServiceManager services, ScriptBundleLoader loader) {
        PayloadTypeRegistry.playS2C().register(DataPayload.ID, DataPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(MoudPayload.ID, MoudPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(DataPayload.ID, DataPayload.CODEC);

        ClientPacketReceiver.registerS2CPackets();

        ClientPacketWrapper.registerHandler(MoudPackets.S2C_PlayPlayerAnimationPacket.class, (player, packet) -> handlePlayPlayerAnimation(packet));
        ClientPacketWrapper.registerHandler(MoudPackets.S2C_PlayModelAnimationPacket.class, (player, packet) -> handlePlayModelAnimation(packet));
        ClientPacketWrapper.registerHandler(MoudPackets.SyncClientScriptsPacket.class, (player, packet) -> loader.handleCompleteBundle(packet, mod, services));
        ClientPacketWrapper.registerHandler(MoudPackets.SyncClientScriptsChunkPacket.class, (player, packet) -> loader.handleChunk(packet, mod, services));
        ClientPacketWrapper.registerHandler(MoudPackets.ClientboundScriptEventPacket.class, (player, packet) -> handleScriptEvent(packet, services));
        ClientPacketWrapper.registerHandler(MoudPackets.VoiceStreamChunkPacket.class, (player, packet) -> handleVoiceStreamChunk(packet, services));
        ClientPacketWrapper.registerHandler(MoudPackets.CameraLockPacket.class, (player, packet) -> handleCameraLock(packet, services));
        ClientPacketWrapper.registerHandler(MoudPackets.PlayerStatePacket.class, (player, packet) -> handlePlayerState(packet, services));
        ClientPacketWrapper.registerHandler(MoudPackets.ExtendedPlayerStatePacket.class, (player, packet) -> handleExtendedPlayerState(packet, services));
        ClientPacketWrapper.registerHandler(MoudPackets.UIOverlayUpsertPacket.class, (player, packet) -> handleUiOverlayUpsert(packet));
        ClientPacketWrapper.registerHandler(MoudPackets.UIOverlayRemovePacket.class, (player, packet) -> handleUiOverlayRemove(packet));
        ClientPacketWrapper.registerHandler(MoudPackets.UIOverlayClearPacket.class, (player, packet) -> handleUiOverlayClear());
        ClientPacketWrapper.registerHandler(MoudPackets.ParticleBatchPacket.class, (player, packet) -> handleParticleBatch(packet, services));
        ClientPacketWrapper.registerHandler(MoudPackets.ParticleEmitterUpsertPacket.class, (player, packet) -> handleParticleEmitterUpsert(packet, services));
        ClientPacketWrapper.registerHandler(MoudPackets.ParticleEmitterRemovePacket.class, (player, packet) -> handleParticleEmitterRemove(packet, services));
        ClientPacketWrapper.registerHandler(MoudPackets.SyncSharedValuesPacket.class, (player, packet) -> handleSharedValueSync(packet, services));
        ClientPacketWrapper.registerHandler(MoudPackets.PlayerModelCreatePacket.class, (player, packet) -> handlePlayerModelCreate(packet));
        ClientPacketWrapper.registerHandler(MoudPackets.PlayerModelUpdatePacket.class, (player, packet) -> handlePlayerModelUpdate(packet));
        ClientPacketWrapper.registerHandler(MoudPackets.PlayerModelSkinPacket.class, (player, packet) -> handlePlayerModelSkin(packet));
        ClientPacketWrapper.registerHandler(MoudPackets.PlayerModelRemovePacket.class, (player, packet) -> handlePlayerModelRemove(packet));
        ClientPacketWrapper.registerHandler(MoudPackets.S2C_CreateDisplayPacket.class, (player, packet) -> handleCreateDisplay(packet));
        ClientPacketWrapper.registerHandler(MoudPackets.S2C_UpdateDisplayTransformPacket.class, (player, packet) -> handleUpdateDisplayTransform(packet));
        ClientPacketWrapper.registerHandler(MoudPackets.S2C_UpdateDisplayAnchorPacket.class, (player, packet) -> handleUpdateDisplayAnchor(packet));
        ClientPacketWrapper.registerHandler(MoudPackets.S2C_UpdateDisplayContentPacket.class, (player, packet) -> handleUpdateDisplayContent(packet));
        ClientPacketWrapper.registerHandler(MoudPackets.S2C_UpdateDisplayPlaybackPacket.class, (player, packet) -> handleUpdateDisplayPlayback(packet));
        ClientPacketWrapper.registerHandler(MoudPackets.S2C_RemoveDisplayPacket.class, (player, packet) -> handleRemoveDisplay(packet));
        ClientPacketWrapper.registerHandler(MoudPackets.S2C_PrimitiveCreatePacket.class, (player, packet) -> MinecraftClient.getInstance().execute(() -> ClientPrimitiveManager.getInstance().handleCreate(packet)));
        ClientPacketWrapper.registerHandler(MoudPackets.S2C_PrimitiveBatchCreatePacket.class, (player, packet) -> MinecraftClient.getInstance().execute(() -> ClientPrimitiveManager.getInstance().handleBatchCreate(packet)));
        ClientPacketWrapper.registerHandler(MoudPackets.S2C_PrimitiveTransformPacket.class, (player, packet) -> MinecraftClient.getInstance().execute(() -> ClientPrimitiveManager.getInstance().handleTransform(packet)));
        ClientPacketWrapper.registerHandler(MoudPackets.S2C_PrimitiveBatchTransformPacket.class, (player, packet) -> MinecraftClient.getInstance().execute(() -> ClientPrimitiveManager.getInstance().handleBatchTransform(packet)));
        ClientPacketWrapper.registerHandler(MoudPackets.S2C_PrimitiveMaterialPacket.class, (player, packet) -> MinecraftClient.getInstance().execute(() -> ClientPrimitiveManager.getInstance().handleMaterial(packet)));
        ClientPacketWrapper.registerHandler(MoudPackets.S2C_PrimitiveVerticesPacket.class, (player, packet) -> MinecraftClient.getInstance().execute(() -> ClientPrimitiveManager.getInstance().handleVertices(packet)));
        ClientPacketWrapper.registerHandler(MoudPackets.S2C_PrimitiveRemovePacket.class, (player, packet) -> MinecraftClient.getInstance().execute(() -> ClientPrimitiveManager.getInstance().handleRemove(packet)));
        ClientPacketWrapper.registerHandler(MoudPackets.S2C_PrimitiveRemoveGroupPacket.class, (player, packet) -> MinecraftClient.getInstance().execute(() -> ClientPrimitiveManager.getInstance().handleRemoveGroup(packet)));
        ClientPacketWrapper.registerHandler(MoudPackets.AdvancedCameraLockPacket.class, (player, packet) -> handleAdvancedCameraLock(packet, services));
        ClientPacketWrapper.registerHandler(MoudPackets.CameraUpdatePacket.class, (player, packet) -> handleCameraUpdate(packet, services));
        ClientPacketWrapper.registerHandler(MoudPackets.CameraReleasePacket.class, (player, packet) -> handleCameraRelease(packet, services));
        ClientPacketWrapper.registerHandler(MoudPackets.S2C_ManageWindowPacket.class, (player, packet) -> handleManageWindow(packet));
        ClientPacketWrapper.registerHandler(MoudPackets.S2C_TransitionWindowPacket.class, (player, packet) -> handleTransitionWindow(packet));
        ClientPacketWrapper.registerHandler(MoudPackets.S2C_PlayPlayerAnimationPacket.class, (player, packet) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null) {
                IAnimatedPlayer animatedPlayer = (IAnimatedPlayer) client.player;
                animatedPlayer.getAnimationPlayer().playAnimation(packet.animationId());
            }
        });
        ClientPacketWrapper.registerHandler(MoudPackets.S2C_PlayPlayerAnimationWithFadePacket.class, (player, packet) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null) {
                IAnimatedPlayer animatedPlayer = (IAnimatedPlayer) client.player;
                animatedPlayer.getAnimationPlayer().playAnimationWithFade(packet.animationId(), packet.durationTicks());
            }
        });
        ClientPacketWrapper.registerHandler(MoudPackets.S2C_RestoreWindowPacket.class, (player, packet) -> MinecraftClient.getInstance().execute(() -> WindowAnimator.restore(packet.duration(), packet.easing())));
        ClientPacketWrapper.registerHandler(MoudPackets.S2C_WindowSequencePacket.class, (player, packet) -> MinecraftClient.getInstance().execute(() -> WindowAnimator.startSequence(packet.steps())));
        ClientPacketWrapper.registerHandler(MoudPackets.CursorPositionUpdatePacket.class, (player, packet) -> {
            if (services.getCursorManager() != null) {
                services.getCursorManager().handlePositionUpdates(packet.updates());
            }
        });
        ClientPacketWrapper.registerHandler(MoudPackets.SceneStatePacket.class, (player, packet) -> SceneSessionManager.getInstance().handleSceneState(packet));
        ClientPacketWrapper.registerHandler(MoudPackets.SceneEditAckPacket.class, (player, packet) -> SceneSessionManager.getInstance().handleEditAck(packet));
        ClientPacketWrapper.registerHandler(MoudPackets.AnimationEventPacket.class, (player, packet) -> {
            com.moud.client.editor.scene.SceneEditorDiagnostics.log("Animation event " + packet.eventName() + " on " + packet.objectId() + " payload=" + packet.payload());
            SceneEditorOverlay.getInstance().getTimelinePanel().pushEventIndicator(packet.eventName());
        });
        ClientPacketWrapper.registerHandler(MoudPackets.AnimationPropertyUpdatePacket.class, (player, packet) -> SceneSessionManager.getInstance().mergeAnimationProperty(packet.sceneId(), packet.objectId(), packet.propertyKey(), packet.propertyType(), packet.value(), packet.payload()));
        ClientPacketWrapper.registerHandler(MoudPackets.AnimationTransformUpdatePacket.class, (player, packet) -> SceneSessionManager.getInstance().mergeAnimationTransform(packet.sceneId(), packet.objectId(), packet.position(), packet.rotationEuler(), packet.rotationQuat(), packet.scale(), packet.properties()));
        ClientPacketWrapper.registerHandler(MoudPackets.EditorAssetListPacket.class, (player, packet) -> com.moud.client.editor.assets.EditorAssetCatalog.getInstance().handleAssetList(packet));
        ClientPacketWrapper.registerHandler(MoudPackets.ProjectMapPacket.class, (player, packet) -> com.moud.client.editor.assets.ProjectFileIndex.getInstance().handleProjectMap(packet));
        ClientPacketWrapper.registerHandler(MoudPackets.ProjectFileContentPacket.class, (player, packet) -> com.moud.client.editor.assets.ProjectFileContentCache.getInstance().handleContent(packet));
        ClientPacketWrapper.registerHandler(MoudPackets.SceneBindingPacket.class, (player, packet) -> com.moud.client.editor.selection.SceneSelectionManager.getInstance().handleBindingPacket(packet));
        ClientPacketWrapper.registerHandler(MoudPackets.BlueprintSaveAckPacket.class, (player, packet) -> ClientBlueprintNetwork.getInstance().handleSaveAck(packet));
        ClientPacketWrapper.registerHandler(MoudPackets.BlueprintDataPacket.class, (player, packet) -> ClientBlueprintNetwork.getInstance().handleBlueprintData(packet));
        ClientPacketWrapper.registerHandler(MoudPackets.AnimationLoadResponsePacket.class, (player, packet) -> SceneEditorOverlay.getInstance().handleAnimationLoadResponse(packet));
        ClientPacketWrapper.registerHandler(MoudPackets.AnimationListResponsePacket.class, (player, packet) -> SceneEditorOverlay.getInstance().handleAnimationListResponse(packet));
        ClientPacketWrapper.registerHandler(MoudPackets.CursorAppearancePacket.class, (player, packet) -> {
            if (services.getCursorManager() != null) {
                services.getCursorManager().handleAppearanceUpdate(packet.playerId(), packet.texture(), packet.color(), packet.scale(), packet.renderMode());
            }
        });
        ClientPacketWrapper.registerHandler(MoudPackets.CursorVisibilityPacket.class, (player, packet) -> {
            if (services.getCursorManager() != null) {
                services.getCursorManager().handleVisibilityUpdate(packet.playerId(), packet.visible());
            }
        });
        ClientPacketWrapper.registerHandler(MoudPackets.RemoveCursorsPacket.class, (player, packet) -> {
            if (services.getCursorManager() != null) {
                services.getCursorManager().handleRemoveCursors(packet.playerIds());
            }
        });
        ClientPacketWrapper.registerHandler(MoudPackets.S2C_SetPlayerPartConfigPacket.class, (player, packet) -> PlayerPartConfigManager.getInstance().updatePartConfig(packet.playerId(), packet.partName(), packet.properties()));
        ClientPacketWrapper.registerHandler(MoudPackets.InterpolationSettingsPacket.class, (player, packet) -> handleInterpolationSettings(packet));
        ClientPacketWrapper.registerHandler(MoudPackets.FirstPersonConfigPacket.class, (player, packet) -> handleFirstPersonConfig(packet));
        ClientPacketWrapper.registerHandler(MoudPackets.CameraControlPacket.class, (player, packet) -> handleCameraControl(packet, services));
        ClientPacketWrapper.registerHandler(MoudPackets.S2C_PlayModelAnimationWithFadePacket.class, (player, packet) -> {
            MinecraftClient.getInstance().execute(() -> {
                AnimatedPlayerModel model = ClientPlayerModelManager.getInstance().getModel(packet.modelId());
                if (model != null) {
                    model.playAnimationWithFade(packet.animationId(), packet.durationTicks());
                    LOGGER.info("Playing animation '{}' with fade on model {}.", packet.animationId(), packet.modelId());
                } else {
                    LOGGER.warn("Received faded animation for unknown model ID: {}", packet.modelId());
                }
            });
        });
        ClientPacketWrapper.registerHandler(MoudPackets.S2C_CreateModelPacket.class, (player, packet) -> handleCreateModel(packet));
        ClientPacketWrapper.registerHandler(MoudPackets.S2C_UpdateModelAnchorPacket.class, (player, packet) -> handleUpdateModelAnchor(packet));
        ClientPacketWrapper.registerHandler(MoudPackets.S2C_UpdateModelTransformPacket.class, (player, packet) -> handleUpdateModelTransform(packet));
        ClientPacketWrapper.registerHandler(MoudPackets.S2C_UpdateModelTexturePacket.class, (player, packet) -> handleUpdateModelTexture(packet));
        ClientPacketWrapper.registerHandler(MoudPackets.S2C_UpdateModelCollisionPacket.class, (player, packet) -> handleUpdateModelCollision(packet));
        ClientPacketWrapper.registerHandler(MoudPackets.S2C_SyncModelCollisionBoxesPacket.class, (player, packet) -> handleSyncModelCollisionBoxes(packet));
        ClientPacketWrapper.registerHandler(MoudPackets.S2C_RemoveModelPacket.class, (player, packet) -> handleRemoveModel(packet));

        ClientPacketWrapper.registerHandler(MoudPackets.S2C_IKCreateChainPacket.class, (player, packet) -> MinecraftClient.getInstance().execute(() -> ClientIKManager.getInstance().handleCreate(packet)));
        ClientPacketWrapper.registerHandler(MoudPackets.S2C_IKUpdateChainPacket.class, (player, packet) -> MinecraftClient.getInstance().execute(() -> ClientIKManager.getInstance().handleUpdate(packet)));
        ClientPacketWrapper.registerHandler(MoudPackets.S2C_IKBatchUpdatePacket.class, (player, packet) -> MinecraftClient.getInstance().execute(() -> ClientIKManager.getInstance().handleBatchUpdate(packet)));
        ClientPacketWrapper.registerHandler(MoudPackets.S2C_IKUpdateTargetPacket.class, (player, packet) -> MinecraftClient.getInstance().execute(() -> ClientIKManager.getInstance().handleUpdateTarget(packet)));
        ClientPacketWrapper.registerHandler(MoudPackets.S2C_IKUpdateRootPacket.class, (player, packet) -> MinecraftClient.getInstance().execute(() -> ClientIKManager.getInstance().handleUpdateRoot(packet)));
        ClientPacketWrapper.registerHandler(MoudPackets.S2C_IKAttachPacket.class, (player, packet) -> MinecraftClient.getInstance().execute(() -> ClientIKManager.getInstance().handleAttach(packet)));
        ClientPacketWrapper.registerHandler(MoudPackets.S2C_IKDetachPacket.class, (player, packet) -> MinecraftClient.getInstance().execute(() -> ClientIKManager.getInstance().handleDetach(packet)));
        ClientPacketWrapper.registerHandler(MoudPackets.S2C_IKRemoveChainPacket.class, (player, packet) -> MinecraftClient.getInstance().execute(() -> ClientIKManager.getInstance().handleRemove(packet)));


        LOGGER.info("Internal packet handlers registered.");
    }

    private void handleInterpolationSettings(MoudPackets.InterpolationSettingsPacket packet) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && client.player.getUuid().equals(packet.playerId())) {
            PlayerPartConfigManager.InterpolationSettings settings = new PlayerPartConfigManager.InterpolationSettings();

            Map<String, Object> data = packet.settings();
            if (data.containsKey("enabled")) {
                settings.enabled = (Boolean) data.get("enabled");
            }
            if (data.containsKey("duration")) {
                settings.duration = ((Number) data.get("duration")).longValue();
            }
            if (data.containsKey("easing")) {
                String easingStr = (String) data.get("easing");
                try {
                    settings.easing = PlayerPartConfigManager.EasingType.valueOf(easingStr.toUpperCase());
                } catch (IllegalArgumentException e) {
                    LOGGER.warn("Unknown easing type: {}", easingStr);
                }
            }
            if (data.containsKey("speed")) {
                settings.speed = ((Number) data.get("speed")).floatValue();
            }

            PlayerPartConfigManager.getInstance().setPlayerInterpolationSettings(packet.playerId(), settings);
        }
    }

    private void handleManageWindow(MoudPackets.S2C_ManageWindowPacket packet) {
        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> {
            long handle = client.getWindow().getHandle();
            switch (packet.action()) {
                case SET_SIZE -> org.lwjgl.glfw.GLFW.glfwSetWindowSize(handle, packet.int1(), packet.int2());
                case SET_POSITION -> org.lwjgl.glfw.GLFW.glfwSetWindowPos(handle, packet.int1(), packet.int2());
                case SET_TITLE -> org.lwjgl.glfw.GLFW.glfwSetWindowTitle(handle, packet.string1());
                case SET_BORDERLESS ->
                        org.lwjgl.glfw.GLFW.glfwSetWindowAttrib(handle, org.lwjgl.glfw.GLFW.GLFW_DECORATED, packet.bool1() ? org.lwjgl.glfw.GLFW.GLFW_FALSE : org.lwjgl.glfw.GLFW.GLFW_TRUE);
                case MAXIMIZE -> org.lwjgl.glfw.GLFW.glfwMaximizeWindow(handle);
                case MINIMIZE -> org.lwjgl.glfw.GLFW.glfwIconifyWindow(handle);
                case RESTORE -> org.lwjgl.glfw.GLFW.glfwRestoreWindow(handle);
            }
        });
    }

    private void handleTransitionWindow(MoudPackets.S2C_TransitionWindowPacket packet) {
        MinecraftClient.getInstance().execute(() -> WindowAnimator.startAnimation(packet.targetX(), packet.targetY(), packet.targetWidth(), packet.targetHeight(), packet.duration(), packet.easing()));
    }

    private void handleFirstPersonConfig(MoudPackets.FirstPersonConfigPacket packet) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && client.player.getUuid().equals(packet.playerId())) {
            ExternalPartConfigLayer.updateFirstPersonConfig(packet.playerId(), packet.config());
        }
    }

    private void handleExtendedPlayerState(MoudPackets.ExtendedPlayerStatePacket packet, ClientServiceManager services) {
        PlayerStateManager stateManager = services.getPlayerStateManager();
        if (stateManager != null) {
            stateManager.updateExtendedPlayerState(packet.hideHotbar(), packet.hideHand(), packet.hideExperience(), packet.hideHealth(), packet.hideFood(), packet.hideCrosshair(), packet.hideChat(), packet.hidePlayerList(), packet.hideScoreboard());
        }
    }

    private void handleUiOverlayUpsert(MoudPackets.UIOverlayUpsertPacket packet) {
        ServerUIOverlayManager.getInstance().upsert(packet.elements());
    }

    private void handleUiOverlayRemove(MoudPackets.UIOverlayRemovePacket packet) {
        ServerUIOverlayManager.getInstance().remove(packet.elementIds());
    }

    private void handleUiOverlayClear() {
        ServerUIOverlayManager.getInstance().clear();
    }

    private void handleCameraControl(MoudPackets.CameraControlPacket packet, ClientServiceManager services) {
        ClientAPIService apiService = services.getApiService();
        if (apiService == null || apiService.camera == null) return;

        switch (packet.action()) {
            case ENABLE -> apiService.camera.enableCustomCamera(packet.cameraId());
            case DISABLE -> apiService.camera.disableCustomCamera();
            case TRANSITION_TO -> {
                if (packet.options() != null) {
                    apiService.camera.transitionToFromMap(packet.options());
                }
            }
            case SNAP_TO -> {
                if (packet.options() != null) {
                    apiService.camera.snapToFromMap(packet.options());
                }
            }
            case FOLLOW_TO -> {
                if (packet.options() != null) {
                    apiService.camera.followToFromMap(packet.options());
                }
            }
            case FOLLOW_PATH -> {
                Map<String, Object> options = packet.options();
                if (options != null && options.containsKey("points")) {
                    List<Object> points = normalizeArrayPayload(options.get("points"));
                    Value pointsValue = toValue(points);
                    long duration = toLong(options.get("duration"), 0L);
                    boolean loop = toBoolean(options.get("loop"), false);
                    if (pointsValue != null && pointsValue.hasArrayElements()) {
                        apiService.camera.followPath(pointsValue, duration, loop);
                    } else if (points != null && !points.isEmpty()) {
                        LOGGER.warn("CameraControl FOLLOW_PATH payload not an array. rawClass={}, normalizedSize={}, valueHasArray={}, memberKeys={}",
                                options.get("points") != null ? options.get("points").getClass().getSimpleName() : "null",
                                points.size(),
                                pointsValue != null && pointsValue.hasArrayElements(),
                                pointsValue != null && pointsValue.hasMembers() ? pointsValue.getMemberKeys() : null);
                        apiService.camera.followPathFromList(points, duration, loop);
                    }
                }
            }
            case STOP_PATH -> apiService.camera.stopPath();
            case CREATE_CINEMATIC -> {
                Map<String, Object> options = packet.options();
                if (options != null && options.containsKey("keyframes")) {
                    List<Object> keyframes = normalizeArrayPayload(options.get("keyframes"));
                    if (keyframes == null && options.get("keyframes") != null) {
                        keyframes = new ArrayList<>();
                        keyframes.add(options.get("keyframes"));
                    }
                    Value kfValue = toValue(keyframes);
                    boolean hasArray = kfValue != null && kfValue.hasArrayElements();
                    if (!hasArray) {
                        LOGGER.warn("CameraControl CREATE_CINEMATIC payload not an array. rawClass={}, normalizedSize={}, kfValueClass={}, memberKeys={}",
                                options.get("keyframes") != null ? options.get("keyframes").getClass().getSimpleName() : "null",
                                keyframes != null ? keyframes.size() : null,
                                kfValue != null ? kfValue.getClass().getSimpleName() : "null",
                                kfValue != null && kfValue.hasMembers() ? kfValue.getMemberKeys() : null);
                        if (keyframes != null && !keyframes.isEmpty()) {
                            apiService.camera.createCinematicFromList(keyframes);
                        }
                    }
                    if (kfValue != null && hasArray) {
                        apiService.camera.createCinematic(kfValue);
                    }
                }
            }
            case STOP_CINEMATIC -> apiService.camera.stopCinematic();
            case LOOK_AT -> {
                Map<String, Object> options = packet.options();
                if (options != null && !options.isEmpty()) {
                    Value target = toValue(options);
                    if (target != null) {
                        apiService.camera.lookAt(target);
                    }
                }
            }
            case CLEAR_LOOK_AT -> apiService.camera.clearLookAt();
            case DOLLY_ZOOM -> {
                Map<String, Object> options = packet.options();
                if (options != null) {
                    apiService.camera.dollyZoomFromMap(options);
                } else {
                    double fallbackFov = apiService.camera.getFovInternal() != null ? apiService.camera.getFovInternal() : 70.0;
                    apiService.camera.dollyZoom(fallbackFov, 1000L);
                }
            }
        }
    }

    private Value toValue(Object payload) {
        if (payload == null) {
            return null;
        }
        Object proxyReady = toProxyCompatible(payload);
        return proxyReady != null ? Value.asValue(proxyReady) : null;
    }

    private Object toProxyCompatible(Object payload) {
        if (payload instanceof Map<?, ?> map) {
            Map<String, Object> converted = new HashMap<>();
            map.forEach((k, v) -> {
                if (k != null) {
                    converted.put(k.toString(), toProxyCompatible(v));
                }
            });
            return ProxyObject.fromMap(converted);
        }
        if (payload instanceof List<?> list) {
            List<Object> converted = new ArrayList<>();
            for (Object element : list) {
                converted.add(toProxyCompatible(element));
            }
            return ProxyArray.fromList(converted);
        }
        return payload;
    }

    private List<Object> normalizeArrayPayload(Object payload) {
        if (payload instanceof List<?>) {
            return new ArrayList<>((List<?>) payload);
        }
        if (payload instanceof Map<?, ?> map) {
            // Attempt to preserve ordering if numeric keys are present (e.g., {"0": {...}, "1": {...}})
            List<Map.Entry<String, Object>> entries = new ArrayList<>();
            map.forEach((k, v) -> {
                if (k != null) {
                    entries.add(Map.entry(k.toString(), v));
                }
            });
            entries.sort((a, b) -> {
                try {
                    return Integer.compare(Integer.parseInt(a.getKey()), Integer.parseInt(b.getKey()));
                } catch (NumberFormatException ignored) {
                    return a.getKey().compareTo(b.getKey());
                }
            });
            List<Object> ordered = new ArrayList<>();
            for (Map.Entry<String, Object> entry : entries) {
                ordered.add(entry.getValue());
            }
            return ordered;
        }
        if (payload != null) {
            LOGGER.warn("normalizeArrayPayload expected List/Map but got {} ({})", payload.getClass().getSimpleName(), payload);
        }
        return null;
    }

    private long toLong(Object value, long fallback) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return fallback;
    }

    private double toDouble(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return fallback;
    }

    private boolean toBoolean(Object value, boolean fallback) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return fallback;
    }

    private void handleParticleBatch(MoudPackets.ParticleBatchPacket packet, ClientServiceManager services) {
        if (services.getParticleSystem() != null) {
            services.getParticleSystem().spawnBatch(packet.particles());
        }
    }

    private void handleParticleEmitterUpsert(MoudPackets.ParticleEmitterUpsertPacket packet, ClientServiceManager services) {
        if (services.getParticleEmitterSystem() != null) {
            services.getParticleEmitterSystem().upsert(packet.emitters());
        }
    }

    private void handleParticleEmitterRemove(MoudPackets.ParticleEmitterRemovePacket packet, ClientServiceManager services) {
        if (services.getParticleEmitterSystem() != null) {
            services.getParticleEmitterSystem().remove(packet.ids());
        }
    }

    private void handleCameraLock(MoudPackets.CameraLockPacket packet, ClientServiceManager services) {
        ClientAPIService apiService = services.getApiService();
        if (apiService != null && apiService.camera != null) {
            if (packet.isLocked()) {
                apiService.camera.enableCustomCamera();
            } else {
                apiService.camera.disableCustomCamera();
            }
        }
    }

    private void handleAdvancedCameraLock(MoudPackets.AdvancedCameraLockPacket packet, ClientServiceManager services) {
        ClientAPIService apiService = services.getApiService();
        if (apiService != null && apiService.camera != null) {
            if (packet.isLocked()) {
                apiService.camera.enableCustomCamera();
                Value options = Value.asValue(ProxyObject.fromMap(Map.of(
                        "position", packet.position(),
                        "yaw", packet.rotation().x,
                        "pitch", packet.rotation().y,
                        "roll", packet.rotation().z
                )));
                apiService.camera.snapTo(options);
            } else {
                apiService.camera.disableCustomCamera();
            }
        }
    }

    private void handleCameraUpdate(MoudPackets.CameraUpdatePacket packet, ClientServiceManager services) {
        ClientAPIService apiService = services.getApiService();
        if (apiService != null && apiService.camera != null && apiService.camera.isCustomCameraActive()) {
            Value options = Value.asValue(ProxyObject.fromMap(Map.of(
                    "position", packet.position(),
                    "yaw", packet.rotation().x,
                    "pitch", packet.rotation().y,
                    "roll", packet.rotation().z
            )));
            apiService.camera.snapTo(options);
        }
    }

    private void handleCameraRelease(MoudPackets.CameraReleasePacket packet, ClientServiceManager services) {
        ClientAPIService apiService = services.getApiService();
        if (apiService != null && apiService.camera != null) {
            apiService.camera.disableCustomCamera();
        }
    }

    private void handlePlayerState(MoudPackets.PlayerStatePacket packet, ClientServiceManager services) {
        PlayerStateManager stateManager = services.getPlayerStateManager();
        if (stateManager != null) {
            stateManager.updatePlayerState(packet.hideHotbar(), packet.hideHand(), packet.hideExperience());
        }
    }

    private void handleSharedValueSync(MoudPackets.SyncSharedValuesPacket packet, ClientServiceManager services) {
        SharedValueManager sharedValueManager = services.getSharedValueManager();
        if (sharedValueManager != null) {
            sharedValueManager.handleServerSync(packet);
        }
    }

    private void handlePlayPlayerAnimation(MoudPackets.S2C_PlayPlayerAnimationPacket packet) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            IAnimatedPlayer animatedPlayer = (IAnimatedPlayer) client.player;
            animatedPlayer.getAnimationPlayer().playAnimation(packet.animationId());
        }
    }

    private void handlePlayModelAnimation(MoudPackets.S2C_PlayModelAnimationPacket packet) {
        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> {
            AnimatedPlayerModel model = ClientPlayerModelManager.getInstance().getModel(packet.modelId());
            if (model != null) {
                model.playAnimation(packet.animationId());
                LOGGER.info("Playing animation '{}' on model {}.", packet.animationId(), packet.modelId());
            } else {
                LOGGER.warn("Received animation for unknown model ID: {}", packet.modelId());
            }
        });
    }

    private void handlePlayerModelCreate(MoudPackets.PlayerModelCreatePacket packet) {
        LOGGER.info("RECEIVED PlayerModelCreatePacket for model ID: {} at position: {}", packet.modelId(), packet.position());
        MinecraftClient.getInstance().execute(() -> {
            LOGGER.info("Executing PlayerModelCreate on main thread for model {}", packet.modelId());
            AnimatedPlayerModel model = ClientPlayerModelManager.getInstance().createModel(packet.modelId());
            if (model == null) {
                LOGGER.warn("Failed to create player model {} because the client world is unavailable", packet.modelId());
                return;
            }

            LOGGER.info("Model created successfully, updating position to {}", packet.position());
            model.updatePositionAndRotation(packet.position(), 0, 0);
            if (packet.skinUrl() != null && !packet.skinUrl().isEmpty()) {
                model.updateSkin(packet.skinUrl());
            }
            RuntimeObjectRegistry.getInstance().syncPlayerModel(packet.modelId(),
                    new net.minecraft.util.math.Vec3d(packet.position().x, packet.position().y, packet.position().z),
                    new net.minecraft.util.math.Vec3d(0, 0, 0));
            SceneSessionManager.getInstance().restoreLimbPropertiesForModel(packet.modelId(), model);
            LOGGER.info("Created player model with ID: {} at position: {}", packet.modelId(), packet.position());
        });
    }

    private void handlePlayerModelRemove(MoudPackets.PlayerModelRemovePacket packet) {
        MinecraftClient.getInstance().execute(() -> {
            ClientPlayerModelManager.getInstance().removeModel(packet.modelId());
            RuntimeObjectRegistry.getInstance().removePlayerModel(packet.modelId());
            LOGGER.info("Removed player model with ID: {}", packet.modelId());
        });
    }

    private void handlePlayerModelUpdate(MoudPackets.PlayerModelUpdatePacket packet) {
        MinecraftClient.getInstance().execute(() -> {
            AnimatedPlayerModel model = ClientPlayerModelManager.getInstance().getModel(packet.modelId());
            if (model != null) {
                model.updatePositionAndRotation(packet.position(), packet.yaw(), packet.pitch());
                RuntimeObjectRegistry.getInstance().syncPlayerModel(packet.modelId(),
                        new net.minecraft.util.math.Vec3d(packet.position().x, packet.position().y, packet.position().z),
                        new net.minecraft.util.math.Vec3d(packet.pitch(), packet.yaw(), 0));
            } else {
                LOGGER.warn("Received update for unknown model ID: {}", packet.modelId());
            }
        });
    }

    private void handlePlayerModelSkin(MoudPackets.PlayerModelSkinPacket packet) {
        MinecraftClient.getInstance().execute(() -> {
            AnimatedPlayerModel model = ClientPlayerModelManager.getInstance().getModel(packet.modelId());
            if (model != null) {
                model.updateSkin(packet.skinUrl());
            } else {
                LOGGER.warn("Received skin update for unknown model ID: {}", packet.modelId());
            }
        });
    }

    private void handleCreateModel(MoudPackets.S2C_CreateModelPacket packet) {
        MinecraftClient.getInstance().execute(() -> {
            ClientModelManager.getInstance().createModel(packet.modelId(), packet.modelPath());
            RenderableModel model = ClientModelManager.getInstance().getModel(packet.modelId());
            if (model != null) {
                model.updateTransform(packet.position(), packet.rotation(), packet.scale());
                model.updateCollisionBox(packet.collisionWidth(), packet.collisionHeight(), packet.collisionDepth());
                if (packet.collisionBoxes() != null && !packet.collisionBoxes().isEmpty()) {
                    List<com.moud.api.collision.OBB> mapped = mapCollisionBoxes(packet.collisionBoxes());
                    LOGGER.info("Model {} created with {} collision boxes from server", packet.modelId(), mapped.size());
                    model.setCollisionBoxes(mapped);
                }
                applyModelTexture(model, packet.texturePath(), packet.modelId());
                ModelCollisionManager.getInstance().sync(model);
                RuntimeObjectRegistry.getInstance().syncModel(model);
            }
            int vLen = packet.compressedMeshVertices() != null ? packet.compressedMeshVertices().length : -1;
            int iLen = packet.compressedMeshIndices() != null ? packet.compressedMeshIndices().length : -1;
            MoudPackets.CollisionMode mode = packet.collisionMode();
            if ((mode == null || mode == MoudPackets.CollisionMode.BOX) && vLen > 0 && iLen > 0) {
                mode = MoudPackets.CollisionMode.MESH;
            }
            LOGGER.info("CreateModel collision payload: mode={}, vertsBytes={}, indicesBytes={}", mode, vLen, iLen);
            ClientCollisionManager.registerModel(packet.modelId(), mode, packet.compressedMeshVertices(), packet.compressedMeshIndices(), packet.position(), packet.rotation(), packet.scale());
        });
    }

    private void handleUpdateModelTransform(MoudPackets.S2C_UpdateModelTransformPacket packet) {
        MinecraftClient.getInstance().execute(() -> {
            RenderableModel model = ClientModelManager.getInstance().getModel(packet.modelId());
            if (model != null) {
                model.updateTransform(packet.position(), packet.rotation(), packet.scale());
                ModelCollisionManager.getInstance().sync(model);
                RuntimeObjectRegistry.getInstance().syncModel(model);
                ClientCollisionManager.updateTransform(packet.modelId(), model.getPosition(), model.getRotation(), model.getScale());
                return;
            }
            ClientCollisionManager.updateTransform(packet.modelId(), packet.position(), packet.rotation(), packet.scale());
        });
    }

    private void handleUpdateModelAnchor(MoudPackets.S2C_UpdateModelAnchorPacket packet) {
        MinecraftClient.getInstance().execute(() -> {
            RenderableModel model = ClientModelManager.getInstance().getModel(packet.modelId());
            if (model != null) {
                model.updateAnchor(packet.anchorType(),
                        packet.anchorEntityUuid(),
                        packet.anchorModelId(),
                        packet.anchorBlockPosition(),
                        packet.localPosition(),
                        packet.localRotation(),
                        packet.localScale(),
                        packet.localSpace(),
                        packet.inheritRotation(),
                        packet.inheritScale(),
                        packet.includePitch());
                ModelCollisionManager.getInstance().sync(model);
                RuntimeObjectRegistry.getInstance().syncModel(model);
            } else {
                LOGGER.warn("Received anchor update for unknown model ID: {}", packet.modelId());
            }
        });
    }

    private void handleUpdateModelTexture(MoudPackets.S2C_UpdateModelTexturePacket packet) {
        MinecraftClient.getInstance().execute(() -> {
            RenderableModel model = ClientModelManager.getInstance().getModel(packet.modelId());
            if (model != null) {
                if (applyModelTexture(model, packet.texturePath(), packet.modelId())) {
                    RuntimeObjectRegistry.getInstance().syncModel(model);
                }
            }
        });
    }

    private void handleUpdateModelCollision(MoudPackets.S2C_UpdateModelCollisionPacket packet) {
        MinecraftClient.getInstance().execute(() -> {
            RenderableModel model = ClientModelManager.getInstance().getModel(packet.modelId());
            if (model != null) {
                model.updateCollisionBox(packet.collisionWidth(), packet.collisionHeight(), packet.collisionDepth());
                ModelCollisionManager.getInstance().sync(model);
                RuntimeObjectRegistry.getInstance().syncModel(model);
            }
        });
    }

    private void handleSyncModelCollisionBoxes(MoudPackets.S2C_SyncModelCollisionBoxesPacket packet) {
        MinecraftClient.getInstance().execute(() -> {
            RenderableModel model = ClientModelManager.getInstance().getModel(packet.modelId());
            if (model != null) {
                List<com.moud.api.collision.OBB> mapped = mapCollisionBoxes(packet.collisionBoxes());
                LOGGER.info("Model {} received {} collision boxes from server", packet.modelId(), mapped.size());
                model.setCollisionBoxes(mapped);
                ModelCollisionManager.getInstance().sync(model);
            }
        });
    }

    private List<com.moud.api.collision.OBB> mapCollisionBoxes(List<MoudPackets.CollisionBoxData> packetBoxes) {
        List<com.moud.api.collision.OBB> collisionBoxes = new ArrayList<>();
        if (packetBoxes == null) {
            return collisionBoxes;
        }
        for (MoudPackets.CollisionBoxData boxData : packetBoxes) {
            if (boxData == null) {
                continue;
            }
            collisionBoxes.add(new com.moud.api.collision.OBB(
                    boxData.center(),
                    boxData.halfExtents(),
                    boxData.rotation()
            ));
        }
        return collisionBoxes;
    }

    private void handleRemoveModel(MoudPackets.S2C_RemoveModelPacket packet) {
        MinecraftClient.getInstance().execute(() -> {
            ClientModelManager.getInstance().removeModel(packet.modelId());
        });
    }

    private void handleCreateDisplay(MoudPackets.S2C_CreateDisplayPacket packet) {
        MinecraftClient.getInstance().execute(() -> ClientDisplayManager.getInstance().handleCreate(packet));
    }

    private void handleUpdateDisplayTransform(MoudPackets.S2C_UpdateDisplayTransformPacket packet) {
        MinecraftClient.getInstance().execute(() -> ClientDisplayManager.getInstance().handleTransform(packet));
    }

    private void handleUpdateDisplayAnchor(MoudPackets.S2C_UpdateDisplayAnchorPacket packet) {
        MinecraftClient.getInstance().execute(() -> ClientDisplayManager.getInstance().handleAnchor(packet));
    }

    private void handleUpdateDisplayContent(MoudPackets.S2C_UpdateDisplayContentPacket packet) {
        MinecraftClient.getInstance().execute(() -> ClientDisplayManager.getInstance().handleContent(packet));
    }

    private void handleUpdateDisplayPlayback(MoudPackets.S2C_UpdateDisplayPlaybackPacket packet) {
        MinecraftClient.getInstance().execute(() -> ClientDisplayManager.getInstance().handlePlayback(packet));
    }

    private void handleRemoveDisplay(MoudPackets.S2C_RemoveDisplayPacket packet) {
        MinecraftClient.getInstance().execute(() -> ClientDisplayManager.getInstance().remove(packet.displayId()));
    }

    private Identifier parseTextureId(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return null;
        }
        String normalized = rawPath.trim().replace('\\', '/');
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.startsWith("assets/")) {
            normalized = normalized.substring("assets/".length());
        }
        if (normalized.startsWith("moud:moud/")) {
            normalized = "moud:" + normalized.substring("moud:moud/".length());
        }
        if (!normalized.contains(":")) {
            String effective = normalized;
            if (normalized.contains("/")) {
                int firstSlash = normalized.indexOf('/');
                String maybeNamespace = normalized.substring(0, firstSlash);
                String remainder = normalized.substring(firstSlash + 1);
                if (!maybeNamespace.isBlank() && !remainder.isBlank()) {
                    effective = maybeNamespace + ":" + remainder;
                }
            }
            if (!effective.contains(":")) {
                effective = effective.startsWith("moud/") ? "moud:" + effective.substring(5) : "moud:" + effective;
            }
            normalized = effective;
        }
        Identifier parsed = Identifier.tryParse(normalized);
        if (parsed != null && "moud".equals(parsed.getNamespace())) {
            String path = parsed.getPath();
            if (path.startsWith("moud/") && path.length() > 5) {
                return Identifier.of("moud", path.substring(5));
            }
        }
        return parsed;
    }

    private boolean applyModelTexture(RenderableModel model, String texturePath, long modelId) {
        if (model == null) {
            return false;
        }
        if (texturePath == null || texturePath.isBlank()) {
            LOGGER.info("Applying default texture to model {} (blank/cleared input)", modelId);
            model.setTexture(DEFAULT_MODEL_TEXTURE);
            return true;
        }
        Identifier textureId = parseTextureId(texturePath);
        if (textureId != null) {
            LOGGER.info("Applying texture '{}' to model {}", textureId, modelId);
            model.setTexture(textureId);
            return true;
        }
        LOGGER.warn("Received invalid texture identifier '{}' for model {}", texturePath, modelId);
        return false;
    }

    private void handleScriptEvent(MoudPackets.ClientboundScriptEventPacket packet, ClientServiceManager services) {
        if (handleBuiltinScriptEvent(packet.eventName(), packet.eventData(), services.getApiService())) {
            return;
        }
        ClientScriptingRuntime runtime = services.getRuntime();
        if (runtime != null && runtime.isInitialized()) {
            runtime.triggerNetworkEvent(packet.eventName(), packet.eventData());
        }
    }

    private void handleVoiceStreamChunk(MoudPackets.VoiceStreamChunkPacket packet, ClientServiceManager services) {
        ClientAPIService api = services.getApiService();
        if (api == null || api.audio == null) {
            return;
        }
        api.audio.handleVoiceStreamChunk(packet);
    }

    private boolean handleBuiltinScriptEvent(String eventName, String payload, ClientAPIService apiService) {
        if (apiService == null) {
            return false;
        }
        try {
            switch (eventName) {
                case "rendering:post:apply" -> {
                    JsonObject json = builtinEventParser.fromJson(payload, JsonObject.class);
                    if (json != null && json.has("id")) {
                        apiService.rendering.applyPostEffect(json.get("id").getAsString());
                    }
                    return true;
                }
                case "rendering:post:remove" -> {
                    JsonObject json = builtinEventParser.fromJson(payload, JsonObject.class);
                    if (json != null && json.has("id")) {
                        apiService.rendering.removePostEffect(json.get("id").getAsString());
                    }
                    return true;
                }
                case "rendering:post:set_uniforms" -> {
                    JsonObject json = builtinEventParser.fromJson(payload, JsonObject.class);
                    if (json != null && json.has("id") && json.has("uniforms")) {
                        String id = json.get("id").getAsString();
                        JsonObject uniformsJson = json.getAsJsonObject("uniforms");
                        Map<String, Object> uniforms = new java.util.HashMap<>();
                        uniformsJson.entrySet().forEach(entry -> {
                            if (entry.getValue().isJsonPrimitive() && entry.getValue().getAsJsonPrimitive().isNumber()) {
                                uniforms.put(entry.getKey(), entry.getValue().getAsDouble());
                            } else if (entry.getValue().isJsonObject()) {
                                Map<String, Object> inner = new java.util.HashMap<>();
                                entry.getValue().getAsJsonObject().entrySet().forEach(e -> {
                                    if (e.getValue().isJsonPrimitive() && e.getValue().getAsJsonPrimitive().isNumber()) {
                                        inner.put(e.getKey(), e.getValue().getAsDouble());
                                    }
                                });
                                uniforms.put(entry.getKey(), inner);
                            }
                        });
                        apiService.rendering.setPostEffectUniforms(id, uniforms);
                    }
                    return true;
                }
                case "rendering:post:clear" -> {
                    apiService.rendering.clearPostEffects();
                    return true;
                }
                case "ui:toast" -> {
                    JsonObject json = builtinEventParser.fromJson(payload, JsonObject.class);
                    String title = json != null && json.has("title") ? json.get("title").getAsString() : "";
                    String body = json != null && json.has("body") ? json.get("body").getAsString() : "";
                    apiService.ui.showToast(title, body);
                    return true;
                }
                default -> {
                    return false;
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to handle builtin client event {}: {}", eventName, e.getMessage());
            return false;
        }
    }
}
