package com.moud.client.fabric.scripting.api;

import com.moud.client.fabric.player.MoudPalAnimLayer;
import com.moud.client.fabric.player.PlayerBodyAttachmentCache;
import com.moud.client.fabric.player.ScriptablePalController;
import com.moud.client.fabric.runtime.PlayRuntimeBus;
import com.moud.client.fabric.runtime.PlayRuntimeClient;
import com.moud.client.fabric.util.ClientDebugLog;
import com.zigythebird.playeranim.animation.PlayerAnimResources;
import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranim.api.PlayerAnimationAccess;
import com.zigythebird.playeranimcore.animation.AnimationController;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonConfiguration;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonMode;
import com.zigythebird.playeranimcore.animation.RawAnimation;
import com.zigythebird.playeranimcore.bones.PlayerAnimBone;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.Identifier;

public final class AnimApi {

    private static final String TAG = "AnimApi";
    private static final float DEFAULT_STOP_FADE = 0.2f;
    private static final String DEFAULT_SYNC_MODE = "local";
    private final AbstractClientPlayerEntity player;
    private volatile String syncMode = DEFAULT_SYNC_MODE;
    private final Map<String, String> boneSyncModes = new ConcurrentHashMap<>();

    public AnimApi(AbstractClientPlayerEntity player) {
        this.player = player;
    }

    public void play(String layer, String animName) {
        playWithFade(layer, animName, 0.0f);
    }

    public void playWithFade(String layer, String animName, float fadeSecs) {
        PlayerAnimationController ctrl = playerController();
        Identifier animId = animationId(animName);
        if (ctrl == null || animId == null) {
            return;
        }
        try {
            if (!PlayerAnimResources.hasAnimation(animId)) {
                return;
            }
            ctrl.triggerAnimation(animId, Math.max(0.0f, fadeSecs));
        } catch (Exception e) {
            ClientDebugLog.warn(TAG, "Failed to play animation '" + animName + "': " + e.getMessage());
        }
    }

    public void stop(String layer) {
        stopWithFade(layer, DEFAULT_STOP_FADE);
    }

    public void stopWithFade(String layer, float fadeSecs) {
        AnimationController ctrl = controller();
        if (ctrl == null) {
            return;
        }
        try {
            ctrl.stop();
        } catch (Exception e) {
            ClientDebugLog.warn(TAG, "Failed to stop animation '" + layer + "': " + e.getMessage());
        }
    }

    public boolean isPlaying(String layer) {
        AnimationController ctrl = controller();
        Identifier animId = animationId(layer);
        if (ctrl == null || animId == null) {
            return false;
        }
        try {
            var target = PlayerAnimResources.getAnimationOptional(animId).orElse(null);
            if (target == null || !ctrl.isActive()) {
                return false;
            }
            if (target.equals(ctrl.getCurrentAnimationInstance())) {
                return true;
            }
            RawAnimation raw = ctrl.getCurrentRawAnimation();
            if (raw == null) {
                return false;
            }
            for (RawAnimation.Stage stage : raw.getAnimationStages()) {
                if (stage != null && target.equals(stage.animation())) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            ClientDebugLog.warn(TAG, "Failed to query animation '" + layer + "': " + e.getMessage());
            return false;
        }
    }

    public boolean hasController() {
        return controller() != null;
    }

    public boolean hasBone(String name) {
        return bone(name) != null;
    }

    public boolean setSyncMode(String modeName) {
        String normalized = normalizeSyncMode(modeName);
        if (normalized == null) {
            ClientDebugLog.warn(TAG, "Invalid sync mode '" + modeName + "'");
            return false;
        }
        syncMode = normalized;
        return true;
    }

    public String getSyncMode() {
        return syncMode;
    }

    public boolean setBoneSync(String boneName, String modeName) {
        if (boneName == null || boneName.isBlank()) {
            ClientDebugLog.warn(TAG, "Cannot set sync mode for blank bone name");
            return false;
        }
        String normalized = normalizeSyncMode(modeName);
        if (normalized == null) {
            ClientDebugLog.warn(TAG, "Invalid sync mode '" + modeName + "' for bone '" + boneName + "'");
            return false;
        }
        boneSyncModes.put(boneName.trim(), normalized);
        return true;
    }

    public String getBoneSync(String boneName) {
        if (boneName == null || boneName.isBlank()) {
            return syncMode;
        }
        return boneSyncModes.getOrDefault(boneName.trim(), syncMode);
    }

    public boolean clearBoneSync(String boneName) {
        if (boneName == null || boneName.isBlank()) {
            return false;
        }
        boolean removed = boneSyncModes.remove(boneName.trim()) != null;
        return removed;
    }

    public void clearBoneSyncModes() {
        boneSyncModes.clear();
    }

    public boolean setFirstPersonMode(String modeName) {
        ScriptablePalController ctrl = scriptableController();
        FirstPersonMode mode = parseFirstPersonMode(modeName);
        if (ctrl == null || mode == null) {
            return false;
        }
        ctrl.setFirstPersonMode(mode);
        return true;
    }

    public String getFirstPersonMode() {
        ScriptablePalController ctrl = scriptableController();
        FirstPersonMode mode = ctrl == null ? FirstPersonMode.NONE : ctrl.getFirstPersonMode();
        return switch (mode) {
            case VANILLA -> "vanilla";
            case THIRD_PERSON_MODEL -> "third_person_model";
            case DISABLED -> "disabled";
            default -> "none";
        };
    }

    public boolean configureFirstPerson(boolean showRightArm, boolean showLeftArm,
                                        boolean showRightItem, boolean showLeftItem,
                                        boolean showArmor) {
        ScriptablePalController ctrl = scriptableController();
        if (ctrl == null) {
            return false;
        }
        FirstPersonConfiguration cfg = ctrl.mutableFirstPersonConfiguration();
        cfg.setShowRightArm(showRightArm);
        cfg.setShowLeftArm(showLeftArm);
        cfg.setShowRightItem(showRightItem);
        cfg.setShowLeftItem(showLeftItem);
        cfg.setShowArmor(showArmor);
        return true;
    }

    public boolean setFirstPersonArms(boolean showRightArm, boolean showLeftArm) {
        ScriptablePalController ctrl = scriptableController();
        if (ctrl == null) {
            return false;
        }
        FirstPersonConfiguration cfg = ctrl.mutableFirstPersonConfiguration();
        cfg.setShowRightArm(showRightArm);
        cfg.setShowLeftArm(showLeftArm);
        return true;
    }

    public boolean setFirstPersonItems(boolean showRightItem, boolean showLeftItem) {
        ScriptablePalController ctrl = scriptableController();
        if (ctrl == null) {
            return false;
        }
        FirstPersonConfiguration cfg = ctrl.mutableFirstPersonConfiguration();
        cfg.setShowRightItem(showRightItem);
        cfg.setShowLeftItem(showLeftItem);
        return true;
    }

    public boolean setFirstPersonArmor(boolean showArmor) {
        ScriptablePalController ctrl = scriptableController();
        if (ctrl == null) {
            return false;
        }
        ctrl.mutableFirstPersonConfiguration().setShowArmor(showArmor);
        return true;
    }

    public float[] getAttachPos(String name) {
        if (player == null) {
            return new float[]{Float.NaN, Float.NaN, Float.NaN};
        }
        float[] pos = PlayerBodyAttachmentCache.getAttachPoint(player.getUuidAsString(), name);
        if (pos == null || pos.length < 3) {
            return new float[]{Float.NaN, Float.NaN, Float.NaN};
        }
        return new float[]{pos[0], pos[1], pos[2]};
    }

    public float[] getAttachRot(String name) {
        if (player == null) {
            return new float[]{Float.NaN, Float.NaN, Float.NaN};
        }
        float[] rot = PlayerBodyAttachmentCache.getRotation(player.getUuidAsString(), name);
        if (rot == null || rot.length < 3) {
            return new float[]{Float.NaN, Float.NaN, Float.NaN};
        }
        return new float[]{rot[0], rot[1], rot[2]};
    }

    public float[] getBonePos(String name) {
        PlayerAnimBone bone = bone(name);
        if (bone == null) {
            return new float[]{Float.NaN, Float.NaN, Float.NaN};
        }
        try {
            return new float[]{bone.getPosX(), bone.getPosY(), bone.getPosZ()};
        } catch (Exception e) {
            ClientDebugLog.warn(TAG, "Failed to read bone position '" + name + "': " + e.getMessage());
            return new float[]{Float.NaN, Float.NaN, Float.NaN};
        }
    }

    public float[] getBoneRot(String name) {
        PlayerAnimBone bone = bone(name);
        if (bone == null) {
            return new float[]{Float.NaN, Float.NaN, Float.NaN};
        }
        try {
            return new float[]{bone.getRotX(), bone.getRotY(), bone.getRotZ()};
        } catch (Exception e) {
            ClientDebugLog.warn(TAG, "Failed to read bone rotation '" + name + "': " + e.getMessage());
            return new float[]{Float.NaN, Float.NaN, Float.NaN};
        }
    }

    public boolean setBonePos(String name, float x, float y, float z) {
        PlayerAnimBone bone = bone(name);
        if (bone == null) {
            ClientDebugLog.warn(TAG, "Cannot set bone position for missing bone '" + name + "'");
            return false;
        }
        try {
            bone.setPosX(x);
            bone.setPosY(y);
            bone.setPosZ(z);
            publishBonePos(name, x, y, z);
            return true;
        } catch (Exception e) {
            ClientDebugLog.warn(TAG, "Failed to set bone position '" + name + "': " + e.getMessage());
            return false;
        }
    }

    public boolean setBoneRot(String name, float x, float y, float z) {
        PlayerAnimBone bone = bone(name);
        if (bone == null) {
            ClientDebugLog.warn(TAG, "Cannot set bone rotation for missing bone '" + name + "'");
            return false;
        }
        try {
            bone.setRotX(x);
            bone.setRotY(y);
            bone.setRotZ(z);
            publishBoneRotRad(name, x, y, z);
            return true;
        } catch (Exception e) {
            ClientDebugLog.warn(TAG, "Failed to set bone rotation '" + name + "': " + e.getMessage());
            return false;
        }
    }

    public boolean setBoneRotDeg(String name, float xDeg, float yDeg, float zDeg) {
        return setBoneRot(name,
                (float) Math.toRadians(xDeg),
                (float) Math.toRadians(yDeg),
                (float) Math.toRadians(zDeg));
    }

    public boolean pointBoneAt(String boneName, double targetX, double targetY, double targetZ) {
        return pointBoneAtFrom(boneName, "root", targetX, targetY, targetZ);
    }

    public boolean pointBoneAtFrom(String boneName, String originName, double targetX, double targetY, double targetZ) {
        if (player == null || boneName == null || boneName.isBlank()) {
            return false;
        }
        String uuid = player.getUuidAsString();
        float[] origin = PlayerBodyAttachmentCache.getAttachPoint(uuid, originName);
        float[] root = PlayerBodyAttachmentCache.getRoot(uuid);
        if (origin == null || origin.length < 3 || root == null || root.length < 4) {
            return false;
        }

        double dx = targetX - origin[0];
        double dy = targetY - origin[1];
        double dz = targetZ - origin[2];
        double horiz = Math.sqrt(dx * dx + dz * dz);
        if (horiz < 1.0e-5 && Math.abs(dy) < 1.0e-5) {
            return false;
        }

        double worldYawDeg = Math.toDegrees(Math.atan2(dx, -dz));
        double worldPitchDeg = -Math.toDegrees(Math.atan2(dy, Math.max(1.0e-5, horiz)));
        double localYawDeg = normalizeDeg(worldYawDeg - root[3]);

        return setBoneRotDeg(
                boneName,
                (float) worldPitchDeg,
                (float) localYawDeg,
                0.0f
        );
    }

    public boolean resetBone(String name) {
        PlayerAnimBone bone = bone(name);
        if (bone == null) {
            ClientDebugLog.warn(TAG, "Cannot reset missing bone '" + name + "'");
            return false;
        }
        try {
            bone.setPosX(0.0f);
            bone.setPosY(0.0f);
            bone.setPosZ(0.0f);
            bone.setRotX(0.0f);
            bone.setRotY(0.0f);
            bone.setRotZ(0.0f);
            publishBonePos(name, 0.0f, 0.0f, 0.0f);
            publishBoneRotDeg(name, 0.0f, 0.0f, 0.0f);
            return true;
        } catch (Exception e) {
            ClientDebugLog.warn(TAG, "Failed to reset bone '" + name + "': " + e.getMessage());
            return false;
        }
    }

    private AnimationController controller() {
        if (player == null) {
            return null;
        }
        try {
            Object layer = PlayerAnimationAccess.getPlayerAnimationLayer(player, MoudPalAnimLayer.LAYER_ID);
            if (layer instanceof AnimationController ctrl) {
                return ctrl;
            }
            return null;
        } catch (Exception e) {
            ClientDebugLog.warn(TAG, "Failed to access PAL animation controller: " + e.getMessage());
            return null;
        }
    }

    private PlayerAnimationController playerController() {
        if (player == null) {
            return null;
        }
        try {
            Object layer = PlayerAnimationAccess.getPlayerAnimationLayer(player, MoudPalAnimLayer.LAYER_ID);
            if (layer instanceof PlayerAnimationController ctrl) {
                return ctrl;
            }
            return null;
        } catch (Exception e) {
            ClientDebugLog.warn(TAG, "Failed to access PAL player animation controller: " + e.getMessage());
            return null;
        }
    }

    private ScriptablePalController scriptableController() {
        if (player == null) {
            return null;
        }
        try {
            Object layer = PlayerAnimationAccess.getPlayerAnimationLayer(player, MoudPalAnimLayer.LAYER_ID);
            if (layer instanceof ScriptablePalController ctrl) {
                return ctrl;
            }
            return null;
        } catch (Exception e) {
            ClientDebugLog.warn(TAG, "Failed to access scriptable PAL controller: " + e.getMessage());
            return null;
        }
    }

    private PlayerAnimBone bone(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        AnimationController ctrl = controller();
        if (ctrl == null) {
            return null;
        }
        try {
            return ctrl.getBone(name);
        } catch (Exception e) {
            ClientDebugLog.warn(TAG, "Failed to access bone '" + name + "': " + e.getMessage());
            return null;
        }
    }

    private static Identifier animationId(String animName) {
        if (animName == null || animName.isBlank()) {
            return null;
        }
        try {
            return Identifier.of("moud", animName.trim());
        } catch (Exception e) {
            ClientDebugLog.warn(TAG, "Invalid animation id '" + animName + "': " + e.getMessage());
            return null;
        }
    }

    private static FirstPersonMode parseFirstPersonMode(String modeName) {
        if (modeName == null) {
            return null;
        }
        return switch (modeName.trim().toLowerCase()) {
            case "none" -> FirstPersonMode.NONE;
            case "vanilla" -> FirstPersonMode.VANILLA;
            case "third_person_model", "thirdpersonmodel", "third_person", "thirdperson" ->
                    FirstPersonMode.THIRD_PERSON_MODEL;
            case "disabled" -> FirstPersonMode.DISABLED;
            default -> null;
        };
    }

    private static String normalizeSyncMode(String modeName) {
        if (modeName == null || modeName.isBlank()) {
            return DEFAULT_SYNC_MODE;
        }
        return switch (modeName.trim().toLowerCase()) {
            case "local", "owner", "server" -> modeName.trim().toLowerCase();
            default -> null;
        };
    }

    private static double normalizeDeg(double deg) {
        while (deg > 180.0) deg -= 360.0;
        while (deg < -180.0) deg += 360.0;
        return deg;
    }

    private void publishBonePos(String boneName, float x, float y, float z) {
        if (!shouldReplicateBone(boneName)) {
            return;
        }
        PlayRuntimeClient runtime = PlayRuntimeBus.get();
        if (runtime == null) {
            return;
        }
        runtime.setPlayerState(
            "anim." + sanitizeBoneKey(boneName) + ".pos",
            compactFloat(x) + "," + compactFloat(y) + "," + compactFloat(z)
        );
    }

    private void publishBoneRotRad(String boneName, float x, float y, float z) {
        publishBoneRotDeg(
            boneName,
            (float) Math.toDegrees(x),
            (float) Math.toDegrees(y),
            (float) Math.toDegrees(z)
        );
    }

    private void publishBoneRotDeg(String boneName, float xDeg, float yDeg, float zDeg) {
        if (!shouldReplicateBone(boneName)) {
            return;
        }
        PlayRuntimeClient runtime = PlayRuntimeBus.get();
        if (runtime == null) {
            return;
        }
        runtime.setPlayerState(
            "anim." + sanitizeBoneKey(boneName) + ".rot",
            compactFloat(xDeg) + "," + compactFloat(yDeg) + "," + compactFloat(zDeg)
        );
    }

    private boolean shouldReplicateBone(String boneName) {
        String mode = getBoneSync(boneName);
        return "server".equals(mode) || "owner".equals(mode);
    }

    private static String sanitizeBoneKey(String boneName) {
        return boneName == null ? "" : boneName.trim().toLowerCase().replaceAll("[^a-z0-9_-]", "_");
    }

    private static String compactFloat(float value) {
        float rounded = Math.round(value * 10.0f) / 10.0f;
        if (Math.abs(rounded - Math.round(rounded)) < 0.0001f) {
            return Integer.toString(Math.round(rounded));
        }
        return Float.toString(rounded);
    }
}
