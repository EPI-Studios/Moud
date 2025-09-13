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
}