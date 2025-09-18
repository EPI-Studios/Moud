package com.moud.server.network;

import com.moud.api.math.Vector3;
import net.minestom.server.network.NetworkBuffer;
import net.minestom.server.network.packet.server.common.PluginMessagePacket;

import java.nio.ByteBuffer;

import static net.minestom.server.network.NetworkBuffer.*;

public final class PlayerModelPackets {

    public static PluginMessagePacket createPlayerModelCreatePacket(long modelId, Vector3 position, String skinUrl) {
        byte[] data = NetworkBuffer.makeArray(buffer -> {
            buffer.write(LONG, modelId);
            buffer.write(DOUBLE, position.x);
            buffer.write(DOUBLE, position.y);
            buffer.write(DOUBLE, position.z);
            buffer.write(STRING, skinUrl);
        });
        return new PluginMessagePacket("moud:player_model_create", data);
    }

    public static PluginMessagePacket createPlayerModelUpdatePacket(long modelId, Vector3 position, float yaw, float pitch) {
        byte[] data = NetworkBuffer.makeArray(buffer -> {
            buffer.write(LONG, modelId);
            buffer.write(DOUBLE, position.x);
            buffer.write(DOUBLE, position.y);
            buffer.write(DOUBLE, position.z);
            buffer.write(FLOAT, yaw);
            buffer.write(FLOAT, pitch);
        });
        return new PluginMessagePacket("moud:player_model_update", data);
    }

    public static PluginMessagePacket createPlayerModelSkinPacket(long modelId, String skinUrl) {
        byte[] data = NetworkBuffer.makeArray(buffer -> {
            buffer.write(LONG, modelId);
            buffer.write(STRING, skinUrl);
        });
        return new PluginMessagePacket("moud:player_model_skin", data);
    }

    public static PluginMessagePacket createPlayerModelAnimationPacket(long modelId, String animationName) {
        byte[] data = NetworkBuffer.makeArray(buffer -> {
            buffer.write(LONG, modelId);
            buffer.write(STRING, animationName);
        });
        return new PluginMessagePacket("moud:player_model_animation", data);
    }

    public static PluginMessagePacket createPlayerModelRemovePacket(long modelId) {
        byte[] data = NetworkBuffer.makeArray(buffer -> {
            buffer.write(LONG, modelId);
        });
        return new PluginMessagePacket("moud:player_model_remove", data);
    }

    public static final class ClientboundPlayerModelCreatePacket {
        private final long modelId;
        private final Vector3 position;
        private final String skinUrl;

        public ClientboundPlayerModelCreatePacket(byte[] data) {
            NetworkBuffer buffer = new NetworkBuffer(ByteBuffer.wrap(data));
            this.modelId = buffer.read(LONG);
            float x = buffer.read(FLOAT);
            float y = buffer.read(FLOAT);
            float z = buffer.read(FLOAT);
            this.position = new Vector3(x, y, z);
            this.skinUrl = buffer.read(STRING);
        }

        public long getModelId() { return modelId; }
        public Vector3 getPosition() { return position; }
        public String getSkinUrl() { return skinUrl; }
    }

    public static final class ClientboundPlayerModelUpdatePacket {
        private final long modelId;
        private final Vector3 position;
        private final float yaw;
        private final float pitch;

        public ClientboundPlayerModelUpdatePacket(byte[] data) {
            NetworkBuffer buffer = new NetworkBuffer(ByteBuffer.wrap(data));
            this.modelId = buffer.read(LONG);
            float x = buffer.read(FLOAT);
            float y = buffer.read(FLOAT);
            float z = buffer.read(FLOAT);
            this.position = new Vector3(x, y, z);
            this.yaw = buffer.read(FLOAT);
            this.pitch = buffer.read(FLOAT);
        }

        public long getModelId() { return modelId; }
        public Vector3 getPosition() { return position; }
        public float getYaw() { return yaw; }
        public float getPitch() { return pitch; }
    }

    public static final class ClientboundPlayerModelSkinPacket {
        private final long modelId;
        private final String skinUrl;

        public ClientboundPlayerModelSkinPacket(byte[] data) {
            NetworkBuffer buffer = new NetworkBuffer(ByteBuffer.wrap(data));
            this.modelId = buffer.read(LONG);
            this.skinUrl = buffer.read(STRING);
        }

        public long getModelId() { return modelId; }
        public String getSkinUrl() { return skinUrl; }
    }

    public static final class ClientboundPlayerModelAnimationPacket {
        private final long modelId;
        private final String animationName;

        public ClientboundPlayerModelAnimationPacket(byte[] data) {
            NetworkBuffer buffer = new NetworkBuffer(ByteBuffer.wrap(data));
            this.modelId = buffer.read(LONG);
            this.animationName = buffer.read(STRING);
        }

        public long getModelId() { return modelId; }
        public String getAnimationName() { return animationName; }
    }

    public static final class ClientboundPlayerModelRemovePacket {
        private final long modelId;

        public ClientboundPlayerModelRemovePacket(byte[] data) {
            NetworkBuffer buffer = new NetworkBuffer(ByteBuffer.wrap(data));
            this.modelId = buffer.read(LONG);
        }

        public long getModelId() { return modelId; }
    }

    public static final class ServerboundPlayerModelClickPacket {
        private final long modelId;
        private final double mouseX;
        private final double mouseY;
        private final int button;

        public ServerboundPlayerModelClickPacket(byte[] data) {
            NetworkBuffer buffer = new NetworkBuffer(ByteBuffer.wrap(data));
            this.modelId = buffer.read(LONG);
            this.mouseX = buffer.read(DOUBLE);
            this.mouseY = buffer.read(DOUBLE);
            this.button = buffer.read(INT);
        }

        public long getModelId() { return modelId; }
        public double getMouseX() { return mouseX; }
        public double getMouseY() { return mouseY; }
        public int getButton() { return button; }
    }
}