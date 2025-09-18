package com.moud.server.network;

import com.moud.api.math.Vector3;
import net.minestom.server.network.NetworkBuffer;
import net.minestom.server.network.packet.server.common.PluginMessagePacket;

import java.nio.ByteBuffer;

import static net.minestom.server.network.NetworkBuffer.*;

public final class ServerNetworkPackets {

    public static PluginMessagePacket createSyncClientScriptsPacket(String hash, byte[] scriptData) {
        byte[] data = NetworkBuffer.makeArray(buffer -> {
            buffer.write(STRING, hash);
            buffer.write(BYTE_ARRAY, scriptData);
        });
        return new PluginMessagePacket("moud:sync_scripts", data);
    }

    public static PluginMessagePacket createClientboundScriptEventPacket(String eventName, String eventData) {
        byte[] data = NetworkBuffer.makeArray(buffer -> {
            buffer.write(STRING, eventName);
            buffer.write(STRING, eventData);
        });
        return new PluginMessagePacket("moud:script_event_c", data);
    }

    public static PluginMessagePacket createClientboundCameraLockPacket(Vector3 position, float yaw, float pitch, boolean isLocked) {
        byte[] data = NetworkBuffer.makeArray(buffer -> {
            buffer.write(FLOAT, position.x);
            buffer.write(FLOAT, position.y);
            buffer.write(FLOAT, position.z);
            buffer.write(FLOAT, yaw);
            buffer.write(FLOAT, pitch);
            buffer.write(BOOLEAN, isLocked);
        });
        return new PluginMessagePacket("moud:camera_lock", data);
    }

    public static PluginMessagePacket createClientboundPlayerStatePacket(boolean hideHotbar, boolean hideHand, boolean hideExperience) {
        byte[] data = NetworkBuffer.makeArray(buffer -> {
            buffer.write(BOOLEAN, hideHotbar);
            buffer.write(BOOLEAN, hideHand);
            buffer.write(BOOLEAN, hideExperience);
        });
        return new PluginMessagePacket("moud:player_state", data);
    }

    public static final class ServerboundScriptEventPacket {
        private final String eventName;
        private final String eventData;

        public ServerboundScriptEventPacket(byte[] data) {
            NetworkBuffer buffer = new NetworkBuffer(ByteBuffer.wrap(data));
            this.eventName = buffer.read(STRING);
            this.eventData = buffer.read(STRING);
        }

        public String getEventName() {
            return eventName;
        }

        public String getEventData() {
            return eventData;
        }
    }

    public static final class HelloPacket {
        private final int protocolVersion;

        public HelloPacket(byte[] data) {
            NetworkBuffer buffer = new NetworkBuffer(ByteBuffer.wrap(data));
            this.protocolVersion = buffer.read(VAR_INT);
        }

        public int getProtocolVersion() {
            return protocolVersion;
        }
    }

    public static final class ClientUpdateCameraPacket {
        private final Vector3 direction;

        public ClientUpdateCameraPacket(byte[] data) {
            NetworkBuffer buffer = new NetworkBuffer(ByteBuffer.wrap(data));
            float x = buffer.read(FLOAT);
            float y = buffer.read(FLOAT);
            float z = buffer.read(FLOAT);
            this.direction = new Vector3(x, y, z);
        }

        public Vector3 getDirection() {
            return direction;
        }
    }

    public static final class ClientboundCameraLockPacket {
        private final Vector3 position;
        private final float yaw;
        private final float pitch;
        private final boolean isLocked;

        public ClientboundCameraLockPacket(byte[] data) {
            NetworkBuffer buffer = new NetworkBuffer(ByteBuffer.wrap(data));
            float x = buffer.read(FLOAT);
            float y = buffer.read(FLOAT);
            float z = buffer.read(FLOAT);
            this.position = new Vector3(x, y, z);
            this.yaw = buffer.read(FLOAT);
            this.pitch = buffer.read(FLOAT);
            this.isLocked = buffer.read(BOOLEAN);
        }

        public Vector3 getPosition() {
            return position;
        }

        public float getYaw() {
            return yaw;
        }

        public float getPitch() {
            return pitch;
        }

        public boolean isLocked() {
            return isLocked;
        }
    }

    public static final class ClientboundPlayerStatePacket {
        private final boolean hideHotbar;
        private final boolean hideHand;
        private final boolean hideExperience;

        public ClientboundPlayerStatePacket(byte[] data) {
            NetworkBuffer buffer = new NetworkBuffer(ByteBuffer.wrap(data));
            this.hideHotbar = buffer.read(BOOLEAN);
            this.hideHand = buffer.read(BOOLEAN);
            this.hideExperience = buffer.read(BOOLEAN);
        }

        public boolean isHideHotbar() {
            return hideHotbar;
        }

        public boolean isHideHand() {
            return hideHand;
        }

        public boolean isHideExperience() {
            return hideExperience;
        }
    }
}