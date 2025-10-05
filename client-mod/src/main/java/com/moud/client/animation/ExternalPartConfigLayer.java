package com.moud.client.animation;

import com.moud.api.math.Vector3;
import com.zigythebird.playeranimcore.animation.layered.IAnimation;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonConfiguration;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonMode;
import com.zigythebird.playeranimcore.bones.PlayerAnimBone;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ExternalPartConfigLayer implements IAnimation {
    private final UUID playerUuid;
    private static final Map<UUID, FirstPersonConfiguration> playerFirstPersonConfigs = new ConcurrentHashMap<>();

    private static final FirstPersonConfiguration DEFAULT_FIRST_PERSON_CONFIG = new FirstPersonConfiguration()
            .setShowRightArm(true)
            .setShowLeftArm(true)
            .setShowRightItem(true)
            .setShowLeftItem(true)
            .setShowArmor(true);

    public ExternalPartConfigLayer(UUID playerUuid) {
        this.playerUuid = playerUuid;
    }

    public static void updateFirstPersonConfig(UUID playerId, Map<String, Object> config) {
        FirstPersonConfiguration configToUpdate = playerFirstPersonConfigs.computeIfAbsent(playerId, k -> new FirstPersonConfiguration());

        if (config.containsKey("showRightArm")) {
            configToUpdate.setShowRightArm((Boolean) config.get("showRightArm"));
        }
        if (config.containsKey("showLeftArm")) {
            configToUpdate.setShowLeftArm((Boolean) config.get("showLeftArm"));
        }
        if (config.containsKey("showRightItem")) {
            configToUpdate.setShowRightItem((Boolean) config.get("showRightItem"));
        }
        if (config.containsKey("showLeftItem")) {
            configToUpdate.setShowLeftItem((Boolean) config.get("showLeftItem"));
        }
        if (config.containsKey("showArmor")) {
            configToUpdate.setShowArmor((Boolean) config.get("showArmor"));
        }
    }
    public static void clearFirstPersonConfig(UUID playerId) {
        playerFirstPersonConfigs.remove(playerId);
    }

    @Override
    public boolean isActive() {
        PlayerPartConfigManager.PartConfig anyConfig = null;
        String[] parts = {"head", "body", "right_arm", "left_arm", "right_leg", "left_leg"};

        for (String part : parts) {
            PlayerPartConfigManager.PartConfig config = PlayerPartConfigManager.getInstance().getPartConfig(playerUuid, part);
            if (config != null) {
                anyConfig = config;
                break;
            }
        }

        return anyConfig != null;
    }

    @NotNull
    @Override
    public FirstPersonMode getFirstPersonMode() {
        return FirstPersonMode.THIRD_PERSON_MODEL;
    }

    @NotNull
    @Override
    public FirstPersonConfiguration getFirstPersonConfiguration() {
        return playerFirstPersonConfigs.getOrDefault(playerUuid, DEFAULT_FIRST_PERSON_CONFIG);
    }

    @Override
    public PlayerAnimBone get3DTransform(@NotNull PlayerAnimBone bone) {
        PlayerPartConfigManager.PartConfig config = PlayerPartConfigManager.getInstance().getPartConfig(playerUuid, bone.getName());

        if (config == null) {
            return bone;
        }

        if (config.visible != null && !config.visible) {
            bone.setScaleX(0);
            bone.setScaleY(0);
            bone.setScaleZ(0);
            return bone;
        }

        if (config.overrideAnimation) {
            bone.setToInitialPose();
        }

        Vector3 position = config.getInterpolatedPosition();
        Vector3 rotation = config.getInterpolatedRotation();
        Vector3 scale = config.getInterpolatedScale();

        if (position != null) {
            bone.setPosX(bone.getPosX() + (float) position.x);
            bone.setPosY(bone.getPosY() + (float) position.y);
            bone.setPosZ(bone.getPosZ() + (float) position.z);
        }

        if (rotation != null) {

            String boneName = bone.getName();
            if (boneName.equals("right_arm") || boneName.equals("left_arm") ||
                    boneName.equals("right_leg") || boneName.equals("left_leg"))
            {

                bone.setRotZ(bone.getRotZ() + MathHelper.RADIANS_PER_DEGREE * (float) rotation.x);

                bone.setRotY(bone.getRotY() + MathHelper.RADIANS_PER_DEGREE * (float) rotation.y);

                bone.setRotX(bone.getRotX() + MathHelper.RADIANS_PER_DEGREE * (float) rotation.z);
            }
            else
            {
                bone.setRotX(bone.getRotX() + MathHelper.RADIANS_PER_DEGREE * (float) rotation.x);
                bone.setRotY(bone.getRotY() + MathHelper.RADIANS_PER_DEGREE * (float) rotation.y);
                bone.setRotZ(bone.getRotZ() + MathHelper.RADIANS_PER_DEGREE * (float) rotation.z);
            }
        }

        if (scale != null) {
            bone.setScaleX(bone.getScaleX() * (float) scale.x);
            bone.setScaleY(bone.getScaleY() * (float) scale.y);
            bone.setScaleZ(bone.getScaleZ() * (float) scale.z);
        }
        return bone;
    }
}