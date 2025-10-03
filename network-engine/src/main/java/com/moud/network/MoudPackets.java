package com.moud.network;

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
    public record SyncClientScriptsPacket(@Field(order = 0) String hash, @Field(order = 1) byte[] scriptData) {
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

    @Packet(value = "moud:player_model_create", direction = Direction.SERVER_TO_CLIENT)
    public record PlayerModelCreatePacket(@Field(order = 0) long modelId, @Field(order = 1) Vector3 position,
                                          @Field(order = 2) String skinUrl) {
    }

    @Packet(value = "moud:player_model_update", direction = Direction.SERVER_TO_CLIENT)
    public record PlayerModelUpdatePacket(@Field(order = 0) long modelId, @Field(order = 1) Vector3 position,
                                          @Field(order = 2) float yaw, @Field(order = 3) float pitch) {
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
            @Field(order = 1) @Nullable Map<String, Object> options
    ) {
        public enum Action {
            ENABLE,
            DISABLE,
            TRANSITION_TO,
            SNAP_TO
        }
    }
    @Packet(value = "moud:play_model_animation_fade", direction = Direction.SERVER_TO_CLIENT)
    public record S2C_PlayModelAnimationWithFadePacket(
            @Field(order = 0) long modelId,
            @Field(order = 1) String animationId,
            @Field(order = 2) int durationTicks
    ) {}

}