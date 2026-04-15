package com.moud.client.fabric.player;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.math.MathHelper;

public final class PlayerBodyAttachmentCache {
    private static final Map<String, float[]> rootByUuid = new ConcurrentHashMap<>();

    private static final Map<String, float[]> pointByKey = new ConcurrentHashMap<>();

    private static final Map<String, float[]> rotByKey = new ConcurrentHashMap<>();

    /**
     * Decay constant for frame-rate-independent exponential smoothing.
     * Derived so that at 60 fps (dt ≈ 0.01667 s) the effective alpha ≈ 0.35.
     * alpha(dt) = 1 - exp(ln(1 - 0.35) / 0.01667 * dt) = 1 - exp(-25.8 * dt)
     */
    private static final double BONE_LERP_DECAY = -Math.log(1.0 - 0.35) / 0.01667; // ≈ 25.8

    /** Target pose received from the remote state (updated at 24 Hz). Key: uuid:boneName */
    private static final Map<String, float[]> boneTargetByKey = new ConcurrentHashMap<>();

    /** Current visual pose (lerped toward target at render framerate). Key: uuid:boneName */
    private static final Map<String, float[]> boneCurrentByKey = new ConcurrentHashMap<>();

    /**
     * Last-seen raw network strings per bone component, used to skip re-parsing
     * unchanged values. Key: "uuid:boneName.rot" or "uuid:boneName.pos".
     */
    private static final Map<String, String> boneRawCache = new ConcurrentHashMap<>();

    /** Per-UUID wall-clock time of last bone lerp update, for computing real dt. */
    private static final Map<String, Long> boneLerpLastNanosPerUuid = new ConcurrentHashMap<>();

    /**
     * Pre-allocated, render-thread-only set for deduplicating discovered bone names
     * within a single applyReplicatedBoneState call. Cleared before each use.
     */
    private static final java.util.HashSet<String> reusedSeenBones = new java.util.HashSet<>();

    private PlayerBodyAttachmentCache() {
    }

    public static void update(AbstractClientPlayerEntity player, float tickDelta) {
        if (player == null) {
            return;
        }
        String uuid = player.getUuidAsString();
        applyReplicatedBoneState(player, uuid);

        float px = (float) MathHelper.lerp(tickDelta, player.lastRenderX, player.getX());
        float py = (float) MathHelper.lerp(tickDelta, player.lastRenderY, player.getY());
        float pz = (float) MathHelper.lerp(tickDelta, player.lastRenderZ, player.getZ());
        float bodyYaw = MathHelper.lerpAngleDegrees(tickDelta, player.prevBodyYaw, player.getBodyYaw());
        float headYaw = MathHelper.lerpAngleDegrees(tickDelta, player.prevYaw, player.getYaw());

        float[] root = rootByUuid.computeIfAbsent(uuid, k -> new float[5]);
        root[0] = px;
        root[1] = py;
        root[2] = pz;
        root[3] = bodyYaw;
        root[4] = headYaw;

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

    private static void applyReplicatedBoneState(AbstractClientPlayerEntity player, String uuid) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || player == client.player || uuid == null || uuid.isBlank()) {
            return;
        }
        ScriptablePalController controller = MoudPalAnimLayer.controller(player);
        if (controller == null) {
            return;
        }

        // Compute real frame dt in seconds for frame-rate-independent lerp.
        long now = System.nanoTime();
        long last = boneLerpLastNanosPerUuid.getOrDefault(uuid, now);
        boneLerpLastNanosPerUuid.put(uuid, now);
        float dtSecs = Math.min((now - last) * 1e-9f, 0.1f); // cap at 100ms to survive pauses
        float alpha = dtSecs <= 0f ? 0f : 1f - (float) Math.exp(-BONE_LERP_DECAY * dtSecs);

        // Discover all bones that have any remote anim state key set for this player.
        // Keys: anim.<boneName>.rot  or  anim.<boneName>.pos
        // Reuse pre-allocated set to avoid per-frame allocation.
        reusedSeenBones.clear();
        for (String key : RemotePlayerStateCache.getPlayerKeys(uuid)) {
            if (!key.startsWith("anim.")) {
                continue;
            }
            String boneName = null;
            if (key.endsWith(".rot")) {
                boneName = key.substring("anim.".length(), key.length() - ".rot".length());
            } else if (key.endsWith(".pos")) {
                boneName = key.substring("anim.".length(), key.length() - ".pos".length());
            }
            if (boneName == null || boneName.isBlank() || !reusedSeenBones.add(boneName)) {
                continue;
            }
            applyReplicatedBone(controller, uuid, boneName, alpha);
        }
    }

    private static void applyReplicatedBone(ScriptablePalController controller, String uuid, String boneName, float alpha) {
        var bone = controller.getBone(boneName);
        if (bone == null) {
            return;
        }
        String compactKey = uuid + ":" + boneName;
        float[] target = boneTargetByKey.computeIfAbsent(compactKey, k -> new float[6]);

        // Only re-parse rotation when the raw network string has changed (network updates at 24 Hz,
        // rendering runs at 60-144 Hz — avoid String.split + Float.parseFloat every frame).
        String rotNetKey = "anim." + boneName + ".rot";
        String rotValue = RemotePlayerStateCache.get(uuid, rotNetKey);
        String rotCacheKey = compactKey + ".rot";
        if (!rotValue.equals(boneRawCache.getOrDefault(rotCacheKey, ""))) {
            boneRawCache.put(rotCacheKey, rotValue);
            if (!rotValue.isEmpty()) {
                float[] rotDeg = parseTriple(rotValue);
                if (rotDeg != null) {
                    target[3] = rotDeg[0];
                    target[4] = rotDeg[1];
                    target[5] = rotDeg[2];
                }
            }
        }

        String posNetKey = "anim." + boneName + ".pos";
        String posValue = RemotePlayerStateCache.get(uuid, posNetKey);
        String posCacheKey = compactKey + ".pos";
        if (!posValue.equals(boneRawCache.getOrDefault(posCacheKey, ""))) {
            boneRawCache.put(posCacheKey, posValue);
            if (!posValue.isEmpty()) {
                float[] pos = parseTriple(posValue);
                if (pos != null) {
                    target[0] = pos[0];
                    target[1] = pos[1];
                    target[2] = pos[2];
                }
            }
        }

        // Frame-rate-independent lerp toward target using pre-computed alpha.
        float[] current = boneCurrentByKey.computeIfAbsent(compactKey, k -> target.clone());
        current[0] = approachLinear(current[0], target[0], alpha);
        current[1] = approachLinear(current[1], target[1], alpha);
        current[2] = approachLinear(current[2], target[2], alpha);
        current[3] = approachAngle(current[3], target[3], alpha);
        current[4] = approachAngle(current[4], target[4], alpha);
        current[5] = approachAngle(current[5], target[5], alpha);

        bone.setPosX(current[0]);
        bone.setPosY(current[1]);
        bone.setPosZ(current[2]);
        bone.setRotX((float) Math.toRadians(current[3]));
        bone.setRotY((float) Math.toRadians(current[4]));
        bone.setRotZ((float) Math.toRadians(current[5]));
    }

    private static float approachLinear(float current, float target, float alpha) {
        return current + (target - current) * alpha;
    }

    private static float approachAngle(float current, float target, float alpha) {
        return wrapDegrees(current + wrapDegrees(target - current) * alpha);
    }

    private static float wrapDegrees(float degrees) {
        float value = degrees;
        while (value > 180.0f) {
            value -= 360.0f;
        }
        while (value < -180.0f) {
            value += 360.0f;
        }
        return value;
    }

    private static float[] parseTriple(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String[] parts = value.split(",", -1);
        if (parts.length != 3) {
            return null;
        }
        try {
            float x = Float.parseFloat(parts[0].trim());
            float y = Float.parseFloat(parts[1].trim());
            float z = Float.parseFloat(parts[2].trim());
            if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) {
                return null;
            }
            return new float[]{x, y, z};
        } catch (Exception ignored) {
            return null;
        }
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

    /** Removes all cached state for a single player UUID (call on player disconnect). */
    public static void clearPlayer(String uuid) {
        if (uuid == null) {
            return;
        }
        rootByUuid.remove(uuid);
        boneLerpLastNanosPerUuid.remove(uuid);
        String prefix = uuid + ":";
        pointByKey.keySet().removeIf(k -> k.startsWith(prefix));
        rotByKey.keySet().removeIf(k -> k.startsWith(prefix));
        boneTargetByKey.keySet().removeIf(k -> k.startsWith(prefix));
        boneCurrentByKey.keySet().removeIf(k -> k.startsWith(prefix));
        boneRawCache.keySet().removeIf(k -> k.startsWith(prefix));
    }

    /** Clears all cached state for all players (call on scene teardown / disconnect). */
    public static void clear() {
        rootByUuid.clear();
        pointByKey.clear();
        rotByKey.clear();
        boneTargetByKey.clear();
        boneCurrentByKey.clear();
        boneRawCache.clear();
        boneLerpLastNanosPerUuid.clear();
        RemotePlayerStateCache.clear();
    }
}
