package com.moud.client.fabric.player;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.math.MathHelper;

public final class PlayerBodyAttachmentCache {

    private static final Map<String, float[]> rootByUuid = new ConcurrentHashMap<>();

    private static final Map<String, float[]> pointByKey = new ConcurrentHashMap<>();

    private static final Map<String, float[]> rotByKey = new ConcurrentHashMap<>();

    private PlayerBodyAttachmentCache() {
    }

    public static void update(AbstractClientPlayerEntity player, float tickDelta) {
        if (player == null) {
            return;
        }
        String uuid = player.getUuidAsString();

        float px = (float) MathHelper.lerp(tickDelta, player.lastRenderX, player.getX());
        float py = (float) MathHelper.lerp(tickDelta, player.lastRenderY, player.getY());
        float pz = (float) MathHelper.lerp(tickDelta, player.lastRenderZ, player.getZ());
        float bodyYaw = MathHelper.lerpAngleDegrees(tickDelta, player.prevBodyYaw, player.getBodyYaw());

        float[] root = rootByUuid.computeIfAbsent(uuid, k -> new float[4]);
        root[0] = px;
        root[1] = py;
        root[2] = pz;
        root[3] = bodyYaw;

        storePoint(uuid, "root",       px, py,        pz);
        storePoint(uuid, "center",     px, py + 0.9f, pz);
        storePoint(uuid, "head",       px, py + 1.45f, pz);
        storePoint(uuid, "above_head", px, py + 2.1f, pz);

        float yawRad = (float) Math.toRadians(bodyYaw);
        float sinYaw = (float) Math.sin(yawRad);
        float cosYaw = (float) Math.cos(yawRad);

        storePointRotated(uuid, "right_hand", px, py + 0.95f, pz,  0.35f, 0f, sinYaw, cosYaw);
        storePointRotated(uuid, "left_hand",  px, py + 0.95f, pz, -0.35f, 0f, sinYaw, cosYaw);
        storePointRotated(uuid, "right_item", px, py + 0.9f,  pz,  0.65f, 0f, sinYaw, cosYaw);
        storePointRotated(uuid, "left_item",  px, py + 0.9f,  pz, -0.65f, 0f, sinYaw, cosYaw);
        storePointRotated(uuid, "right_foot", px, py + 0.25f, pz,  0.15f, 0f, sinYaw, cosYaw);
        storePointRotated(uuid, "left_foot",  px, py + 0.25f, pz, -0.15f, 0f, sinYaw, cosYaw);

        MoudPalAnimLayer.applyBoneOffsets(player, uuid, px, py, pz, sinYaw, cosYaw);
    }

    private static void storePoint(String uuid, String point, float x, float y, float z) {
        String key = uuid + ":" + point;
        float[] arr = pointByKey.computeIfAbsent(key, k -> new float[3]);
        arr[0] = x;
        arr[1] = y;
        arr[2] = z;
    }

    private static void storePointRotated(String uuid, String point, float baseX, float baseY, float baseZ,
                                          float localX, float localZ,
                                          float sinYaw, float cosYaw) {
        float worldX = baseX + localX * (-cosYaw) + localZ * sinYaw;
        float worldZ = baseZ + localX * (-sinYaw) + localZ * (-cosYaw);
        storePoint(uuid, point, worldX, baseY, worldZ);
    }

    static void storePalBonePoint(String uuid, String point, float x, float y, float z) {
        storePoint(uuid, point, x, y, z);
    }

    static void storePalBoneRot(String uuid, String point, float rotXDeg, float rotYDeg, float rotZDeg) {
        String key = uuid + ":" + point;
        float[] arr = rotByKey.computeIfAbsent(key, k -> new float[3]);
        arr[0] = rotXDeg;
        arr[1] = rotYDeg;
        arr[2] = rotZDeg;
    }

    public static float[] getRoot(String uuid) {
        if (uuid == null) {
            return null;
        }
        return rootByUuid.get(uuid);
    }

    public static float[] getAttachPoint(String uuid, String attachPoint) {
        if (uuid == null) {
            return null;
        }
        String point = (attachPoint == null || attachPoint.isBlank()) ? "root" : attachPoint;
        float[] pos = pointByKey.get(uuid + ":" + point);
        if (pos != null) {
            return pos;
        }
        return pointByKey.get(uuid + ":root");
    }

    public static float[] getRotation(String uuid, String attachPoint) {
        if (uuid == null || attachPoint == null || attachPoint.isBlank()) {
            return null;
        }
        return rotByKey.get(uuid + ":" + attachPoint);
    }

    public static java.util.Set<String> getActiveUuids() {
        return rootByUuid.keySet();
    }

    public static void clear() {
        rootByUuid.clear();
        pointByKey.clear();
        rotByKey.clear();
    }
}