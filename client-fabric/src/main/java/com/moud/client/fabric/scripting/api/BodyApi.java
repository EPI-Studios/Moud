package com.moud.client.fabric.scripting.api;

import com.moud.client.fabric.player.PlayerBodyScale;
import com.moud.client.fabric.player.PlayerBodyVisibility;
import com.moud.client.fabric.player.PlayerHeadLook;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

public final class BodyApi {

    private BodyApiTarget target;

    public BodyApi() {
    }

    public BodyApi(BodyApiTarget target) {
        this.target = target;
    }

    public void setTarget(BodyApiTarget target) {
        this.target = target;
    }

    public float readFloat(String key) {
        if (target == null || key == null) return 0f;
        return target.getBodyFloat(key);
    }

    public void writeFloat(String key, float value) {
        if (target == null || key == null) return;
        switch (key) {
            case "velocity_x", "velocity_y", "velocity_z",
                 "speed", "acceleration", "deceleration",
                 "ground_friction", "air_control",
                 "jump_velocity", "gravity_scale",
                 "rotation_y", "head_yaw", "rotation_x", "rotation_z" -> target.setBodyFloat(key, value);
            default -> { /* silently ignore unknown write keys */ }
        }
    }

    public boolean readBool(String key) {
        if (target == null || key == null) return false;
        return target.getBodyBool(key);
    }

    public void writeBool(String key, boolean value) {
        if (target == null || key == null) return;
        if ("jump_requested".equals(key)) {
            target.setBodyBool(key, value);
        }
    }

    public void setVisible(boolean visible) {
        String uuid = localUuid();
        if (uuid != null) PlayerBodyVisibility.setBodyVisible(uuid, visible);
    }

    public boolean isVisible() {
        String uuid = localUuid();
        return uuid == null || PlayerBodyVisibility.isBodyVisible(uuid);
    }

    public void setPartVisible(String part, boolean visible) {
        String uuid = localUuid();
        if (uuid != null) PlayerBodyVisibility.setPartVisible(uuid, part, visible);
    }

    public boolean isPartVisible(String part) {
        String uuid = localUuid();
        return uuid == null || PlayerBodyVisibility.isPartVisible(uuid, part);
    }

    public void setScale(double x, double y, double z) {
        String uuid = localUuid();
        if (uuid != null) PlayerBodyScale.setWhole(uuid, (float) x, (float) y, (float) z);
    }

    public void clearScale() {
        String uuid = localUuid();
        if (uuid != null) PlayerBodyScale.clearWhole(uuid);
    }

    public void setPartScale(String part, double scale) {
        String uuid = localUuid();
        if (uuid != null) PlayerBodyScale.setPart(uuid, part, (float) scale);
    }

    public void clearPartScale(String part) {
        String uuid = localUuid();
        if (uuid != null) PlayerBodyScale.clearPart(uuid, part);
    }

    public void setHeadLookAt(double x, double y, double z) {
        PlayerHeadLook.set(x, y, z);
    }

    public void clearHeadLookAt() {
        PlayerHeadLook.clear();
    }

    private static String localUuid() {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity p = mc == null ? null : mc.player;
        return p == null ? null : p.getUuidAsString();
    }
}
