package com.moud.client.network;

import com.moud.api.math.Vector3;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public final class MoudPackets {

    public record HelloPacket(int protocolVersion) implements CustomPayload {
        public static final CustomPayload.Id<HelloPacket> ID =
                new CustomPayload.Id<>(Identifier.of("moud", "hello"));

        public static final PacketCodec<PacketByteBuf, HelloPacket> CODEC =
                PacketCodec.of(HelloPacket::write, HelloPacket::new);

        private HelloPacket(PacketByteBuf buf) {
            this(buf.readVarInt());
        }

        private void write(PacketByteBuf buf) {
            buf.writeVarInt(protocolVersion);
        }

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record SyncClientScripts(String hash, byte[] scriptData) implements CustomPayload {
        public static final CustomPayload.Id<SyncClientScripts> ID =
                new CustomPayload.Id<>(Identifier.of("moud", "sync_scripts"));

        public static final PacketCodec<PacketByteBuf, SyncClientScripts> CODEC =
                PacketCodec.of(SyncClientScripts::write, SyncClientScripts::new);

        private SyncClientScripts(PacketByteBuf buf) {
            this(buf.readString(), buf.readByteArray());
        }

        private void write(PacketByteBuf buf) {
            buf.writeString(hash);
            buf.writeByteArray(scriptData);
        }

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record ClientboundScriptEvent(String eventName, String eventData) implements CustomPayload {
        public static final CustomPayload.Id<ClientboundScriptEvent> ID =
                new CustomPayload.Id<>(Identifier.of("moud", "script_event_c"));

        public static final PacketCodec<PacketByteBuf, ClientboundScriptEvent> CODEC =
                PacketCodec.of(ClientboundScriptEvent::write, ClientboundScriptEvent::new);

        private ClientboundScriptEvent(PacketByteBuf buf) {
            this(buf.readString(), buf.readString());
        }

        private void write(PacketByteBuf buf) {
            buf.writeString(eventName);
            buf.writeString(eventData);
        }

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record ServerboundScriptEvent(String eventName, String eventData) implements CustomPayload {
        public static final CustomPayload.Id<ServerboundScriptEvent> ID =
                new CustomPayload.Id<>(Identifier.of("moud", "script_event_s"));

        public static final PacketCodec<PacketByteBuf, ServerboundScriptEvent> CODEC =
                PacketCodec.of(ServerboundScriptEvent::write, ServerboundScriptEvent::new);

        private ServerboundScriptEvent(PacketByteBuf buf) {
            this(buf.readString(), buf.readString());
        }

        private void write(PacketByteBuf buf) {
            buf.writeString(eventName);
            buf.writeString(eventData);
        }

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record ClientUpdateCameraPacket(Vector3 direction) implements CustomPayload {
        public static final CustomPayload.Id<ClientUpdateCameraPacket> ID =
                new CustomPayload.Id<>(Identifier.of("moud", "update_camera"));

        public static final PacketCodec<PacketByteBuf, ClientUpdateCameraPacket> CODEC =
                PacketCodec.of(ClientUpdateCameraPacket::write, ClientUpdateCameraPacket::new);

        private ClientUpdateCameraPacket(PacketByteBuf buf) {
            this(new Vector3(buf.readFloat(), buf.readFloat(), buf.readFloat()));
        }

        private void write(PacketByteBuf buf) {
            buf.writeFloat(direction.x);
            buf.writeFloat(direction.y);
            buf.writeFloat(direction.z);
        }

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}