package com.moud.client.network;

import com.moud.api.math.Vector3;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public final class MoudPackets {


    public record SyncClientScripts(String hash, byte[] scriptData) implements CustomPayload {
        public static final Id<SyncClientScripts> ID = new Id<>(Identifier.of("moud", "sync_scripts"));
        public static final PacketCodec<PacketByteBuf, SyncClientScripts> CODEC = PacketCodec.of(SyncClientScripts::write, SyncClientScripts::new);

        public SyncClientScripts(PacketByteBuf buf) {
            this(buf.readString(), buf.readByteArray());
        }

        public void write(PacketByteBuf buf) {
            buf.writeString(this.hash);
            buf.writeByteArray(this.scriptData);
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record ClientboundScriptEvent(String eventName, String eventData) implements CustomPayload {
        public static final Id<ClientboundScriptEvent> ID = new Id<>(Identifier.of("moud", "script_event_c"));
        public static final PacketCodec<PacketByteBuf, ClientboundScriptEvent> CODEC = PacketCodec.of(ClientboundScriptEvent::write, ClientboundScriptEvent::new);

        public ClientboundScriptEvent(PacketByteBuf buf) {
            this(buf.readString(), buf.readString());
        }

        public void write(PacketByteBuf buf) {
            buf.writeString(this.eventName);
            buf.writeString(this.eventData);
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record ClientboundCameraLockPacket(Vector3 position, float yaw, float pitch,
                                              boolean isLocked) implements CustomPayload {
        public static final Id<ClientboundCameraLockPacket> ID = new Id<>(Identifier.of("moud", "camera_lock"));
        public static final PacketCodec<PacketByteBuf, ClientboundCameraLockPacket> CODEC = PacketCodec.of(ClientboundCameraLockPacket::write, ClientboundCameraLockPacket::new);

        public ClientboundCameraLockPacket(PacketByteBuf buf) {
            this(new Vector3(buf.readDouble(), buf.readDouble(), buf.readDouble()), buf.readFloat(), buf.readFloat(), buf.readBoolean());
        }

        public void write(PacketByteBuf buf) {
            buf.writeDouble(this.position.x);
            buf.writeDouble(this.position.y);
            buf.writeDouble(this.position.z);
            buf.writeFloat(this.yaw);
            buf.writeFloat(this.pitch);
            buf.writeBoolean(this.isLocked);
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record ClientboundPlayerStatePacket(boolean hideHotbar, boolean hideHand,
                                               boolean hideExperience) implements CustomPayload {
        public static final Id<ClientboundPlayerStatePacket> ID = new Id<>(Identifier.of("moud", "player_state"));
        public static final PacketCodec<PacketByteBuf, ClientboundPlayerStatePacket> CODEC = PacketCodec.of(ClientboundPlayerStatePacket::write, ClientboundPlayerStatePacket::new);

        public ClientboundPlayerStatePacket(PacketByteBuf buf) {
            this(buf.readBoolean(), buf.readBoolean(), buf.readBoolean());
        }

        public void write(PacketByteBuf buf) {
            buf.writeBoolean(this.hideHotbar);
            buf.writeBoolean(this.hideHand);
            buf.writeBoolean(this.hideExperience);
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record S2C_PlayerModelCreatePacket(long modelId, Vector3 position, String skinUrl) implements CustomPayload {
        public static final Id<S2C_PlayerModelCreatePacket> ID = new Id<>(Identifier.of("moud", "player_model_create"));
        public static final PacketCodec<PacketByteBuf, S2C_PlayerModelCreatePacket> CODEC = PacketCodec.of(S2C_PlayerModelCreatePacket::write, S2C_PlayerModelCreatePacket::new);

        public S2C_PlayerModelCreatePacket(PacketByteBuf buf) {
            this(buf.readLong(), new Vector3(buf.readDouble(), buf.readDouble(), buf.readDouble()), buf.readString());
        }

        public void write(PacketByteBuf buf) {
            buf.writeLong(this.modelId);
            buf.writeDouble(this.position.x);
            buf.writeDouble(this.position.y);
            buf.writeDouble(this.position.z);
            buf.writeString(this.skinUrl);
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record S2C_PlayerModelUpdatePacket(long modelId, Vector3 position, float yaw,
                                              float pitch) implements CustomPayload {
        public static final Id<S2C_PlayerModelUpdatePacket> ID = new Id<>(Identifier.of("moud", "player_model_update"));
        public static final PacketCodec<PacketByteBuf, S2C_PlayerModelUpdatePacket> CODEC = PacketCodec.of(S2C_PlayerModelUpdatePacket::write, S2C_PlayerModelUpdatePacket::new);

        public S2C_PlayerModelUpdatePacket(PacketByteBuf buf) {
            this(buf.readLong(), new Vector3(buf.readDouble(), buf.readDouble(), buf.readDouble()), buf.readFloat(), buf.readFloat());
        }

        public void write(PacketByteBuf buf) {
            buf.writeLong(this.modelId);
            buf.writeDouble(this.position.x);
            buf.writeDouble(this.position.y);
            buf.writeDouble(this.position.z);
            buf.writeFloat(this.yaw);
            buf.writeFloat(this.pitch);
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record S2C_PlayerModelSkinPacket(long modelId, String skinUrl) implements CustomPayload {
        public static final Id<S2C_PlayerModelSkinPacket> ID = new Id<>(Identifier.of("moud", "player_model_skin"));
        public static final PacketCodec<PacketByteBuf, S2C_PlayerModelSkinPacket> CODEC = PacketCodec.of(S2C_PlayerModelSkinPacket::write, S2C_PlayerModelSkinPacket::new);

        public S2C_PlayerModelSkinPacket(PacketByteBuf buf) {
            this(buf.readLong(), buf.readString());
        }

        public void write(PacketByteBuf buf) {
            buf.writeLong(this.modelId);
            buf.writeString(this.skinUrl);
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record S2C_PlayerModelAnimationPacket(long modelId, String animationName) implements CustomPayload {
        public static final Id<S2C_PlayerModelAnimationPacket> ID = new Id<>(Identifier.of("moud", "player_model_animation"));
        public static final PacketCodec<PacketByteBuf, S2C_PlayerModelAnimationPacket> CODEC = PacketCodec.of(S2C_PlayerModelAnimationPacket::write, S2C_PlayerModelAnimationPacket::new);

        public S2C_PlayerModelAnimationPacket(PacketByteBuf buf) {
            this(buf.readLong(), buf.readString());
        }

        public void write(PacketByteBuf buf) {
            buf.writeLong(this.modelId);
            buf.writeString(this.animationName);
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record S2C_PlayerModelRemovePacket(long modelId) implements CustomPayload {
        public static final Id<S2C_PlayerModelRemovePacket> ID = new Id<>(Identifier.of("moud", "player_model_remove"));
        public static final PacketCodec<PacketByteBuf, S2C_PlayerModelRemovePacket> CODEC = PacketCodec.of(S2C_PlayerModelRemovePacket::write, S2C_PlayerModelRemovePacket::new);

        public S2C_PlayerModelRemovePacket(PacketByteBuf buf) {
            this(buf.readLong());
        }

        public void write(PacketByteBuf buf) {
            buf.writeLong(this.modelId);
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record HelloPacket(int protocolVersion) implements CustomPayload {
        public static final Id<HelloPacket> ID = new Id<>(Identifier.of("moud", "hello"));
        public static final PacketCodec<PacketByteBuf, HelloPacket> CODEC = PacketCodec.of(HelloPacket::write, HelloPacket::new);

        public HelloPacket(PacketByteBuf buf) {
            this(buf.readVarInt());
        }

        public void write(PacketByteBuf buf) {
            buf.writeVarInt(this.protocolVersion);
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record ServerboundScriptEvent(String eventName, String eventData) implements CustomPayload {
        public static final Id<ServerboundScriptEvent> ID = new Id<>(Identifier.of("moud", "script_event_s"));
        public static final PacketCodec<PacketByteBuf, ServerboundScriptEvent> CODEC = PacketCodec.of(ServerboundScriptEvent::write, ServerboundScriptEvent::new);

        public ServerboundScriptEvent(PacketByteBuf buf) {
            this(buf.readString(), buf.readString());
        }

        public void write(PacketByteBuf buf) {
            buf.writeString(this.eventName);
            buf.writeString(this.eventData);
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record ClientUpdateCameraPacket(Vector3 direction) implements CustomPayload {
        public static final Id<ClientUpdateCameraPacket> ID = new Id<>(Identifier.of("moud", "update_camera"));
        public static final PacketCodec<PacketByteBuf, ClientUpdateCameraPacket> CODEC = PacketCodec.of(ClientUpdateCameraPacket::write, ClientUpdateCameraPacket::new);

        public ClientUpdateCameraPacket(PacketByteBuf buf) {
            this(new Vector3(buf.readDouble(), buf.readDouble(), buf.readDouble()));
        }

        public void write(PacketByteBuf buf) {
            buf.writeDouble(this.direction.x);
            buf.writeDouble(this.direction.y);
            buf.writeDouble(this.direction.z);
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record C2S_MouseMovementPacket(float deltaX, float deltaY) implements CustomPayload {
        public static final Id<C2S_MouseMovementPacket> ID = new Id<>(Identifier.of("moud", "mouse_move"));
        public static final PacketCodec<PacketByteBuf, C2S_MouseMovementPacket> CODEC = PacketCodec.of(C2S_MouseMovementPacket::write, C2S_MouseMovementPacket::new);

        public C2S_MouseMovementPacket(PacketByteBuf buf) {
            this(buf.readFloat(), buf.readFloat());
        }

        public void write(PacketByteBuf buf) {
            buf.writeFloat(this.deltaX);
            buf.writeFloat(this.deltaY);
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record C2S_PlayerClickPacket(int button) implements CustomPayload {
        public static final Id<C2S_PlayerClickPacket> ID = new Id<>(Identifier.of("moud", "player_click"));
        public static final PacketCodec<PacketByteBuf, C2S_PlayerClickPacket> CODEC = PacketCodec.of(C2S_PlayerClickPacket::write, C2S_PlayerClickPacket::new);

        public C2S_PlayerClickPacket(PacketByteBuf buf) {
            this(buf.readVarInt());
        }

        public void write(PacketByteBuf buf) {
            buf.writeVarInt(this.button);
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record PlayerModelClickPacket(long modelId, double mouseX, double mouseY,
                                         int button) implements CustomPayload {
        public static final Id<PlayerModelClickPacket> ID = new Id<>(Identifier.of("moud", "player_model_click"));
        public static final PacketCodec<PacketByteBuf, PlayerModelClickPacket> CODEC = PacketCodec.of(PlayerModelClickPacket::write, PlayerModelClickPacket::new);

        public PlayerModelClickPacket(PacketByteBuf buf) {
            this(buf.readLong(), buf.readDouble(), buf.readDouble(), buf.readInt());
        }

        public void write(PacketByteBuf buf) {
            buf.writeLong(this.modelId);
            buf.writeDouble(this.mouseX);
            buf.writeDouble(this.mouseY);
            buf.writeInt(this.button);
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    // TODO : REWRITE THESE IN DIFF CLASSES
}