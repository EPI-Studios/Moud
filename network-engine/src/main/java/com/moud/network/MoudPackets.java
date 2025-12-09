package com.moud.network;

import com.moud.api.math.Quaternion;
import com.moud.api.math.Vector3;
import com.moud.network.annotation.Direction;
import com.moud.network.annotation.Field;
import com.moud.network.annotation.Packet;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class MoudPackets {

    @Packet(value = "moud:sync_scripts", direction = Direction.SERVER_TO_CLIENT)
    public record SyncClientScriptsPacket(@Field(order = 0) String hash, @Field(order = 1, optional = true) byte[] scriptData) {
    }

    @Packet(value = "moud:sync_scripts_chunk", direction = Direction.SERVER_TO_CLIENT)
    public record SyncClientScriptsChunkPacket(@Field(order = 0) String hash,
                                               @Field(order = 1) int totalChunks,
                                               @Field(order = 2) int chunkIndex,
                                               @Field(order = 3) byte[] data) {
    }

    @Packet(value = "moud:script_event_c", direction = Direction.SERVER_TO_CLIENT)
    public record ClientboundScriptEventPacket(@Field(order = 0) String eventName, @Field(order = 1) String eventData) {
    }

    @Packet(value = "moud:camera_lock", direction = Direction.SERVER_TO_CLIENT)
    public record CameraLockPacket(@Field(order = 0) Vector3 position, @Field(order = 1) float yaw,
                                   @Field(order = 2) float pitch, @Field(order = 3) boolean isLocked) {
    }

    @Packet(value = "moud:advanced_camera_lock", direction = Direction.SERVER_TO_CLIENT)
    public record AdvancedCameraLockPacket(@Field(order = 0) Vector3 position, @Field(order = 1) Vector3 rotation,
                                           @Field(order = 2) boolean smoothTransitions,
                                           @Field(order = 3) float transitionSpeed,
                                           @Field(order = 4) boolean disableViewBobbing,
                                           @Field(order = 5) boolean disableHandMovement,
                                           @Field(order = 6) boolean isLocked) {
    }

    @Packet(value = "moud:camera_update", direction = Direction.SERVER_TO_CLIENT)
    public record CameraUpdatePacket(@Field(order = 0) Vector3 position, @Field(order = 1) Vector3 rotation) {
    }

    @Packet(value = "moud:camera_offset", direction = Direction.SERVER_TO_CLIENT)
    public record CameraOffsetPacket(@Field(order = 0) float intensity, @Field(order = 1) int durationMs) {
    }

    @Packet(value = "moud:camera_release", direction = Direction.SERVER_TO_CLIENT)
    public record CameraReleasePacket(@Field(order = 0) boolean isLocked) {
    }

    @Packet(value = "moud:player_state", direction = Direction.SERVER_TO_CLIENT)
    public record PlayerStatePacket(@Field(order = 0) boolean hideHotbar, @Field(order = 1) boolean hideHand,
                                    @Field(order = 2) boolean hideExperience) {
    }

    @Packet(value = "moud:extended_player_state", direction = Direction.SERVER_TO_CLIENT)
    public record ExtendedPlayerStatePacket(@Field(order = 0) boolean hideHotbar, @Field(order = 1) boolean hideHand,
                                            @Field(order = 2) boolean hideExperience,
                                            @Field(order = 3) boolean hideHealth, @Field(order = 4) boolean hideFood,
                                            @Field(order = 5) boolean hideCrosshair, @Field(order = 6) boolean hideChat,
                                            @Field(order = 7) boolean hidePlayerList,
                                            @Field(order = 8) boolean hideScoreboard) {
    }

    public record UIElementDefinition(
            @Field(order = 0) String id,
            @Field(order = 1) String type,
            @Field(order = 2, optional = true) @Nullable String parentId,
            @Field(order = 3, optional = true) @Nullable Map<String, Object> props
    ) {}

    @Packet(value = "moud:ui_upsert", direction = Direction.SERVER_TO_CLIENT)
    public record UIOverlayUpsertPacket(@Field(order = 0) List<UIElementDefinition> elements) {
    }

    @Packet(value = "moud:ui_remove", direction = Direction.SERVER_TO_CLIENT)
    public record UIOverlayRemovePacket(@Field(order = 0) List<String> elementIds) {
    }

    @Packet(value = "moud:ui_clear", direction = Direction.SERVER_TO_CLIENT)
    public record UIOverlayClearPacket() {
    }

    @Packet(value = "moud:player_model_create", direction = Direction.SERVER_TO_CLIENT)
    public record PlayerModelCreatePacket(@Field(order = 0) long modelId, @Field(order = 1) Vector3 position,
                                          @Field(order = 2) String skinUrl) {
    }

    @Packet(value = "moud:player_model_update", direction = Direction.SERVER_TO_CLIENT)
    public record PlayerModelUpdatePacket(@Field(order = 0) long modelId, @Field(order = 1) Vector3 position,
                                          @Field(order = 2) float yaw, @Field(order = 3) float pitch,
                                          @Field(order = 4, optional = true) String instance) {
    }

    @Packet(value = "moud:player_model_skin", direction = Direction.SERVER_TO_CLIENT)
    public record PlayerModelSkinPacket(@Field(order = 0) long modelId, @Field(order = 1) String skinUrl) {
    }

    @Packet(value = "moud:player_model_animation", direction = Direction.SERVER_TO_CLIENT)
    public record PlayerModelAnimationPacket(@Field(order = 0) long modelId, @Field(order = 1) String animationName) {
    }

    @Packet(value = "moud:player_model_remove", direction = Direction.SERVER_TO_CLIENT)
    public record PlayerModelRemovePacket(@Field(order = 0) long modelId) {
    }

    @Packet(value = "moud:sync_shared_values", direction = Direction.SERVER_TO_CLIENT)
    public record SyncSharedValuesPacket(@Field(order = 0) String playerId, @Field(order = 1) String storeName,
                                         @Field(order = 2) Map<String, Object> deltaChanges,
                                         @Field(order = 3) long timestamp) {
    }

    @Packet(value = "moud:cursor_update_pos", direction = Direction.SERVER_TO_CLIENT)
    public record CursorPositionUpdatePacket(@Field(order = 0) List<CursorUpdateData> updates) {
    }

    public record CursorUpdateData(@Field(order = 0) UUID playerId, @Field(order = 1) Vector3 position,
                                   @Field(order = 2) Vector3 normal, @Field(order = 3) boolean hit) {
    }

    @Packet(value = "moud:cursor_update_appearance", direction = Direction.SERVER_TO_CLIENT)
    public record CursorAppearancePacket(@Field(order = 0) UUID playerId, @Field(order = 1) String texture,
                                         @Field(order = 2) Vector3 color, @Field(order = 3) float scale,
                                         @Field(order = 4) String renderMode) {
    }

    @Packet(value = "moud:cursor_update_visibility", direction = Direction.SERVER_TO_CLIENT)
    public record CursorVisibilityPacket(@Field(order = 0) UUID playerId, @Field(order = 1) boolean visible) {
    }

    @Packet(value = "moud:cursor_remove", direction = Direction.SERVER_TO_CLIENT)
    public record RemoveCursorsPacket(@Field(order = 0) List<UUID> playerIds) {
    }

    @Packet(value = "moud:interpolation_settings", direction = Direction.SERVER_TO_CLIENT)
    public record InterpolationSettingsPacket(@Field(order = 0) UUID playerId,
                                              @Field(order = 1) Map<String, Object> settings) {
    }

    @Packet(value = "moud:first_person_config", direction = Direction.SERVER_TO_CLIENT)
    public record FirstPersonConfigPacket(@Field(order = 0) UUID playerId,
                                          @Field(order = 1) Map<String, Object> config) {
    }

    @Packet(value = "moud:hello", direction = Direction.CLIENT_TO_SERVER)
    public record HelloPacket(@Field(order = 0) int protocolVersion) {
    }

    @Packet(value = "moud:script_event_s", direction = Direction.CLIENT_TO_SERVER)
    public record ServerboundScriptEventPacket(@Field(order = 0) String eventName, @Field(order = 1) String eventData) {
    }

    @Packet(value = "moud:update_camera", direction = Direction.CLIENT_TO_SERVER)
    public record ClientUpdateCameraPacket(@Field(order = 0) Vector3 direction) {
    }

    @Packet(value = "moud:mouse_move", direction = Direction.CLIENT_TO_SERVER)
    public record MouseMovementPacket(@Field(order = 0) float deltaX, @Field(order = 1) float deltaY) {
    }

    @Packet(value = "moud:player_click", direction = Direction.CLIENT_TO_SERVER)
    public record PlayerClickPacket(@Field(order = 0) int button) {
    }

    @Packet(value = "moud:player_model_click", direction = Direction.CLIENT_TO_SERVER)
    public record PlayerModelClickPacket(@Field(order = 0) long modelId, @Field(order = 1) double mouseX,
                                         @Field(order = 2) double mouseY, @Field(order = 3) int button) {
    }

    @Packet(value = "moud:ui_interaction", direction = Direction.CLIENT_TO_SERVER)
    public record UIInteractionPacket(@Field(order = 0) String elementId, @Field(order = 1) String action,
                                      @Field(order = 2, optional = true) @Nullable Map<String, Object> payload) {
    }

    @Packet(value = "moud:update_shared_value", direction = Direction.CLIENT_TO_SERVER)
    public record ClientUpdateValuePacket(@Field(order = 0) String storeName, @Field(order = 1) String key,
                                          @Field(order = 2) Object value, @Field(order = 3) long clientTimestamp) {
    }

    @Packet(value = "moud:play_player_animation", direction = Direction.SERVER_TO_CLIENT)
    public record S2C_PlayPlayerAnimationPacket(@Field(order = 0) String animationId) {
    }

    @Packet(value = "moud:play_model_animation", direction = Direction.SERVER_TO_CLIENT)
    public record S2C_PlayModelAnimationPacket(@Field(order = 0) long modelId, @Field(order = 1) String animationId) {
    }

    @Packet(value = "moud:set_player_part_config", direction = Direction.SERVER_TO_CLIENT)
    public record S2C_SetPlayerPartConfigPacket(
            @Field(order = 0) UUID playerId,
            @Field(order = 1) String partName,
            @Field(order = 2) Map<String, Object> properties
    ) {}
    @Packet(value = "moud:movement_state", direction = Direction.CLIENT_TO_SERVER)
    public record MovementStatePacket(
            @Field(order = 0) boolean forward,
            @Field(order = 1) boolean backward,
            @Field(order = 2) boolean left,
            @Field(order = 3) boolean right,
            @Field(order = 4) boolean jumping,
            @Field(order = 5) boolean sneaking,
            @Field(order = 6) boolean sprinting,
            @Field(order = 7) boolean onGround,
            @Field(order = 8) float speed
    ) {}


    @Packet(value = "moud:client_ready", direction = Direction.CLIENT_TO_SERVER)
    public record ClientReadyPacket() {}

    @Packet(value = "moud:camera_control", direction = Direction.SERVER_TO_CLIENT)
    public record CameraControlPacket(
            @Field(order = 0) Action action,
            @Field(order = 1, optional = true) @Nullable Map<String, Object> options,
            @Field(order = 2, optional = true) @Nullable String cameraId
    ) {
        public enum Action {
            ENABLE,
            DISABLE,
            TRANSITION_TO,
            SNAP_TO,
            FOLLOW_TO,
            FOLLOW_PATH,
            STOP_PATH,
            CREATE_CINEMATIC,
            STOP_CINEMATIC,
            LOOK_AT,
            CLEAR_LOOK_AT,
            DOLLY_ZOOM
        }
    }
    @Packet(value = "moud:play_model_animation_fade", direction = Direction.SERVER_TO_CLIENT)
    public record S2C_PlayModelAnimationWithFadePacket(
            @Field(order = 0) long modelId,
            @Field(order = 1) String animationId,
            @Field(order = 2) int durationTicks
    ) {}

    @Packet(value = "moud:manage_window", direction = Direction.SERVER_TO_CLIENT)
    public record S2C_ManageWindowPacket(
            @Field(order = 0) Action action,
            @Field(order = 1) int int1,       // width or x
            @Field(order = 2) int int2,       // height or y
            @Field(order = 3) String string1, // title
            @Field(order = 4) boolean bool1   // borderless
    ) {
        public enum Action { SET_SIZE, SET_POSITION, SET_TITLE, SET_BORDERLESS, MAXIMIZE, MINIMIZE, RESTORE }
    }

    @Packet(value = "moud:transition_window", direction = Direction.SERVER_TO_CLIENT)
    public record S2C_TransitionWindowPacket(
            @Field(order = 0) int targetX,
            @Field(order = 1) int targetY,
            @Field(order = 2) int targetWidth,
            @Field(order = 3) int targetHeight,
            @Field(order = 4) int duration, // in milliseconds
            @Field(order = 5) String easing // e.g., "ease-out-quad"
    ) {}

    @Packet(value = "moud:restore_window", direction = Direction.SERVER_TO_CLIENT)
    public record S2C_RestoreWindowPacket(
            @Field(order = 0) int duration,
            @Field(order = 1) String easing
    ) {}

    @Packet(value = "moud:window_sequence", direction = Direction.SERVER_TO_CLIENT)
    public record S2C_WindowSequencePacket(
            @Field(order = 0) List<Map<String, Object>> steps
    ) {}

    @Packet(value = "moud:play_player_animation_fade", direction = Direction.SERVER_TO_CLIENT)
    public record S2C_PlayPlayerAnimationWithFadePacket(
            @Field(order = 0) String animationId,
            @Field(order = 1) int durationTicks
    ) {}


    @Packet(value = "moud:create_model", direction = Direction.SERVER_TO_CLIENT)
    public record S2C_CreateModelPacket(
            @Field(order = 0) long modelId,
            @Field(order = 1) String modelPath,
            @Field(order = 2) Vector3 position,
            @Field(order = 3) Quaternion rotation,
            @Field(order = 4) Vector3 scale,
            @Field(order = 5) double collisionWidth,
            @Field(order = 6) double collisionHeight,
            @Field(order = 7) double collisionDepth,
            @Field(order = 8) String texturePath,
            @Field(order = 9, optional = true) @Nullable List<CollisionBoxData> collisionBoxes,
            @Field(order = 10, optional = true) @Nullable CollisionMode collisionMode,
            @Field(order = 11, optional = true) @Nullable byte[] compressedMeshVertices,
            @Field(order = 12, optional = true) @Nullable byte[] compressedMeshIndices,
            @Field(order = 13, optional = true) @Nullable List<ConvexHullData> convexHulls
    ) {}

    @Packet(value = "moud:update_model_transform", direction = Direction.SERVER_TO_CLIENT)
    public record S2C_UpdateModelTransformPacket(
            @Field(order = 0) long modelId,
            @Field(order = 1) Vector3 position,
            @Field(order = 2) Quaternion rotation,
            @Field(order = 3) Vector3 scale
    ) {}

    @Packet(value = "moud:update_model_texture", direction = Direction.SERVER_TO_CLIENT)
    public record S2C_UpdateModelTexturePacket(
            @Field(order = 0) long modelId,
            @Field(order = 1) String texturePath
    ) {}

    @Packet(value = "moud:update_model_collision", direction = Direction.SERVER_TO_CLIENT)
    public record S2C_UpdateModelCollisionPacket(
            @Field(order = 0) long modelId,
            @Field(order = 1) double collisionWidth,
            @Field(order = 2) double collisionHeight,
            @Field(order = 3) double collisionDepth
    ) {}

    @Packet(value = "moud:sync_model_collision_boxes", direction = Direction.SERVER_TO_CLIENT)
    public record S2C_SyncModelCollisionBoxesPacket(
            @Field(order = 0) long modelId,
            @Field(order = 1) List<CollisionBoxData> collisionBoxes
    ) {}

    public record CollisionBoxData(
            @Field(order = 0) Vector3 center,
            @Field(order = 1) Vector3 halfExtents,
            @Field(order = 2) Quaternion rotation
    ) {}

    public record ConvexHullData(
            @Field(order = 0) byte[] compressedVertices,
            @Field(order = 1) byte[] compressedIndices,
            @Field(order = 2) CollisionBoxData bounds
    ) {}

    public enum CollisionMode {
        BOX,
        CONVEX_HULLS,
        MESH
    }

    @Packet(value = "moud:remove_model", direction = Direction.SERVER_TO_CLIENT)
    public record S2C_RemoveModelPacket(
            @Field(order = 0) long modelId
    ) {}

    public enum DisplayAnchorType {
        FREE,
        BLOCK,
        ENTITY
    }

    public enum DisplayContentType {
        IMAGE,
        URL,
        FRAME_SEQUENCE
    }

    @Packet(value = "moud:create_display", direction = Direction.SERVER_TO_CLIENT)
    public record S2C_CreateDisplayPacket(
            @Field(order = 0) long displayId,
            @Field(order = 1) Vector3 position,
            @Field(order = 2) Quaternion rotation,
            @Field(order = 3) Vector3 scale,
            @Field(order = 4) DisplayAnchorType anchorType,
            @Field(order = 5, optional = true) @Nullable Vector3 anchorBlockPosition,
            @Field(order = 6, optional = true) @Nullable UUID anchorEntityUuid,
            @Field(order = 7, optional = true) @Nullable Vector3 anchorOffset,
            @Field(order = 8) DisplayContentType contentType,
            @Field(order = 9, optional = true) @Nullable String primarySource,
            @Field(order = 10, optional = true) @Nullable List<String> frameSources,
            @Field(order = 11) float frameRate,
            @Field(order = 12) boolean loop,
            @Field(order = 13) boolean playing,
            @Field(order = 14) float playbackSpeed,
            @Field(order = 15) float startOffsetSeconds
    ) {}

    @Packet(value = "moud:update_display_transform", direction = Direction.SERVER_TO_CLIENT)
    public record S2C_UpdateDisplayTransformPacket(
            @Field(order = 0) long displayId,
            @Field(order = 1) Vector3 position,
            @Field(order = 2) Quaternion rotation,
            @Field(order = 3) Vector3 scale
    ) {}

    @Packet(value = "moud:update_display_anchor", direction = Direction.SERVER_TO_CLIENT)
    public record S2C_UpdateDisplayAnchorPacket(
            @Field(order = 0) long displayId,
            @Field(order = 1) DisplayAnchorType anchorType,
            @Field(order = 2, optional = true) @Nullable Vector3 anchorBlockPosition,
            @Field(order = 3, optional = true) @Nullable UUID anchorEntityUuid,
            @Field(order = 4, optional = true) @Nullable Vector3 anchorOffset
    ) {}

    @Packet(value = "moud:update_display_content", direction = Direction.SERVER_TO_CLIENT)
    public record S2C_UpdateDisplayContentPacket(
            @Field(order = 0) long displayId,
            @Field(order = 1) DisplayContentType contentType,
            @Field(order = 2, optional = true) @Nullable String primarySource,
            @Field(order = 3, optional = true) @Nullable List<String> frameSources,
            @Field(order = 4) float frameRate,
            @Field(order = 5) boolean loop
    ) {}

    @Packet(value = "moud:update_display_playback", direction = Direction.SERVER_TO_CLIENT)
    public record S2C_UpdateDisplayPlaybackPacket(
            @Field(order = 0) long displayId,
            @Field(order = 1) boolean playing,
            @Field(order = 2) float playbackSpeed,
            @Field(order = 3) float startOffsetSeconds
    ) {}

    @Packet(value = "moud:remove_display", direction = Direction.SERVER_TO_CLIENT)
    public record S2C_RemoveDisplayPacket(
            @Field(order = 0) long displayId
    ) {}

    @Packet(value = "moud:scene_request_state", direction = Direction.CLIENT_TO_SERVER)
    public record RequestSceneStatePacket(
            @Field(order = 0) String sceneId
    ) {}

    @Packet(value = "moud:scene_state", direction = Direction.SERVER_TO_CLIENT)
    public record SceneStatePacket(
            @Field(order = 0) String sceneId,
            @Field(order = 1) List<SceneObjectSnapshot> objects,
            @Field(order = 2) long version
    ) {}

    @Packet(value = "moud:scene_edit", direction = Direction.CLIENT_TO_SERVER)
    public record SceneEditPacket(
            @Field(order = 0) String sceneId,
            @Field(order = 1) String action,
            @Field(order = 2) Map<String, Object> payload,
            @Field(order = 3) long clientVersion
    ) {}

    @Packet(value = "moud:scene_edit_ack", direction = Direction.SERVER_TO_CLIENT)
    public record SceneEditAckPacket(
            @Field(order = 0) String sceneId,
            @Field(order = 1) boolean success,
            @Field(order = 2) String message,
            @Field(order = 3, optional = true) SceneObjectSnapshot updatedObject,
            @Field(order = 4) long serverVersion,
            @Field(order = 5, optional = true) String objectId
    ) {}

    public record SceneObjectSnapshot(
            @Field(order = 0) String objectId,
            @Field(order = 1) String objectType,
            @Field(order = 2) Map<String, Object> properties
    ) {}

    @Packet(value = "moud:editor_assets_request", direction = Direction.CLIENT_TO_SERVER)
    public record RequestEditorAssetsPacket() {}

    @Packet(value = "moud:editor_assets", direction = Direction.SERVER_TO_CLIENT)
    public record EditorAssetListPacket(
            @Field(order = 0) List<EditorAssetDefinition> assets
    ) {}

    public record EditorAssetDefinition(
            @Field(order = 0) String id,
            @Field(order = 1) String label,
            @Field(order = 2) String objectType,
            @Field(order = 3) Map<String, Object> defaultProperties
    ) {}

    // Animation packets
    @Packet(value = "moud:animation_save", direction = Direction.CLIENT_TO_SERVER)
    public record AnimationSavePacket(
            @Field(order = 0) String animationId,
            @Field(order = 1) String projectPath,
            @Field(order = 2) com.moud.api.animation.AnimationClip clip
    ) {}

    @Packet(value = "moud:animation_load", direction = Direction.CLIENT_TO_SERVER)
    public record AnimationLoadPacket(
            @Field(order = 0) String projectPath
    ) {}

    @Packet(value = "moud:animation_load_response", direction = Direction.SERVER_TO_CLIENT)
    public record AnimationLoadResponsePacket(
            @Field(order = 0) String projectPath,
            @Field(order = 1, optional = true) com.moud.api.animation.AnimationClip clip,
            @Field(order = 2) boolean success,
            @Field(order = 3, optional = true) String error
    ) {}

    @Packet(value = "moud:animation_list", direction = Direction.CLIENT_TO_SERVER)
    public record AnimationListPacket() {}

    @Packet(value = "moud:animation_list_response", direction = Direction.SERVER_TO_CLIENT)
    public record AnimationListResponsePacket(
            @Field(order = 0) List<AnimationFileInfo> animations
    ) {}

    public record AnimationFileInfo(
            @Field(order = 0) String path,
            @Field(order = 1) String name,
            @Field(order = 2) float duration,
            @Field(order = 3) int trackCount
    ) {}

    @Packet(value = "moud:animation_play", direction = Direction.CLIENT_TO_SERVER)
    public record AnimationPlayPacket(
            @Field(order = 0) String animationId,
            @Field(order = 1) boolean loop,
            @Field(order = 2) float speed
    ) {}

    @Packet(value = "moud:animation_stop", direction = Direction.CLIENT_TO_SERVER)
    public record AnimationStopPacket(
            @Field(order = 0) String animationId
    ) {}

    @Packet(value = "moud:animation_seek", direction = Direction.CLIENT_TO_SERVER)
    public record AnimationSeekPacket(
            @Field(order = 0) String animationId,
            @Field(order = 1) float time
    ) {}

    @Packet(value = "moud:animation_event", direction = Direction.SERVER_TO_CLIENT)
    public record AnimationEventPacket(
            @Field(order = 0) String animationId,
            @Field(order = 1) String objectId,
            @Field(order = 2) String eventName,
            @Field(order = 3) Map<String, String> payload
    ) {}

    @Packet(value = "moud:animation_prop_update", direction = Direction.SERVER_TO_CLIENT)
    public record AnimationPropertyUpdatePacket(
            @Field(order = 0) String sceneId,
            @Field(order = 1) String objectId,
            @Field(order = 2) String propertyKey,
            @Field(order = 3) com.moud.api.animation.PropertyTrack.PropertyType propertyType,
            @Field(order = 4) float value,
            @Field(order = 5, optional = true) Map<String, Object> payload
    ) {}

    @Packet(value = "moud:project_map_request", direction = Direction.CLIENT_TO_SERVER)
    public record RequestProjectMapPacket() {}

    @Packet(value = "moud:project_map", direction = Direction.SERVER_TO_CLIENT)
    public record ProjectMapPacket(
            @Field(order = 0) List<ProjectFileEntry> entries
    ) {}

    public record ProjectFileEntry(
            @Field(order = 0) String path,
            @Field(order = 1) ProjectEntryKind kind
    ) {}

    public enum ProjectEntryKind {
        FILE,
        DIRECTORY
    }

    @Packet(value = "moud:project_file_request", direction = Direction.CLIENT_TO_SERVER)
    public record RequestProjectFilePacket(
            @Field(order = 0) String path
    ) {}

    @Packet(value = "moud:project_file", direction = Direction.SERVER_TO_CLIENT)
    public record ProjectFileContentPacket(
            @Field(order = 0) String path,
            @Field(order = 1, optional = true) String content,
            @Field(order = 2) boolean success,
            @Field(order = 3, optional = true) String message,
            @Field(order = 4, optional = true) String absolutePath
    ) {}

    @Packet(value = "moud:scene_binding", direction = Direction.SERVER_TO_CLIENT)
    public record SceneBindingPacket(
            @Field(order = 0) String sceneId,
            @Field(order = 1) String objectId,
            @Field(order = 2) long modelId,
            @Field(order = 3) boolean removed
    ) {}

    @Packet(value = "moud:blueprint_save", direction = Direction.CLIENT_TO_SERVER)
    public record SaveBlueprintPacket(
            @Field(order = 0) String name,
            @Field(order = 1) byte[] data
    ) {}

    @Packet(value = "moud:blueprint_save_ack", direction = Direction.SERVER_TO_CLIENT)
    public record BlueprintSaveAckPacket(
            @Field(order = 0) String name,
            @Field(order = 1) boolean success,
            @Field(order = 2) String message
    ) {}

    @Packet(value = "moud:blueprint_request", direction = Direction.CLIENT_TO_SERVER)
    public record RequestBlueprintPacket(
            @Field(order = 0) String name
    ) {}

    @Packet(value = "moud:blueprint_data", direction = Direction.SERVER_TO_CLIENT)
    public record BlueprintDataPacket(
            @Field(order = 0) String name,
            @Field(order = 1, optional = true) byte[] data,
            @Field(order = 2) boolean success,
            @Field(order = 3) String message
    ) {}

    @Packet(value = "moud:runtime_model_transform", direction = Direction.CLIENT_TO_SERVER)
    public record UpdateRuntimeModelPacket(
            @Field(order = 0) long modelId,
            @Field(order = 1) Vector3 position,
            @Field(order = 2) Quaternion rotation,
            @Field(order = 3) Vector3 scale
    ) {}

    @Packet(value = "moud:runtime_display_transform", direction = Direction.CLIENT_TO_SERVER)
    public record UpdateRuntimeDisplayPacket(
            @Field(order = 0) long displayId,
            @Field(order = 1) Vector3 position,
            @Field(order = 2) Quaternion rotation,
            @Field(order = 3) Vector3 scale
    ) {}

    @Packet(value = "moud:update_player_transform", direction = Direction.CLIENT_TO_SERVER)
    public record UpdatePlayerTransformPacket(
            @Field(order = 0) UUID playerId,
            @Field(order = 1) Vector3 position,
            @Field(order = 2, optional = true) Quaternion rotation
    ) {}

    @Packet(value = "moud:particle_batch", direction = Direction.SERVER_TO_CLIENT)
    public record ParticleBatchPacket(@Field(order = 0) List<com.moud.api.particle.ParticleDescriptor> particles) {
    }

    @Packet(value = "moud:particle_emitter_upsert", direction = Direction.SERVER_TO_CLIENT)
    public record ParticleEmitterUpsertPacket(@Field(order = 0) List<com.moud.api.particle.ParticleEmitterConfig> emitters) {
    }

    @Packet(value = "moud:particle_emitter_remove", direction = Direction.SERVER_TO_CLIENT)
    public record ParticleEmitterRemovePacket(@Field(order = 0) List<String> ids) {
    }
}
