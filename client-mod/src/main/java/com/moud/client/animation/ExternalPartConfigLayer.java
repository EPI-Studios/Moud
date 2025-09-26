package com.moud.client.animation;

import com.zigythebird.playeranimcore.animation.layered.IAnimation;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonConfiguration;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonMode;
import com.zigythebird.playeranimcore.bones.PlayerAnimBone;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class ExternalPartConfigLayer implements IAnimation {
    private final UUID playerUuid;

    private static final FirstPersonConfiguration FIRST_PERSON_CONFIG = new FirstPersonConfiguration()
            .setShowRightArm(true)
            .setShowLeftArm(true)
            .setShowRightItem(true)
            .setShowLeftItem(true)
            .setShowArmor(true);

    public ExternalPartConfigLayer(UUID playerUuid) {
        this.playerUuid = playerUuid;
    }

    @Override
    public boolean isActive() {
        return true;
    }

    @NotNull
    @Override
    public FirstPersonMode getFirstPersonMode() {
        return FirstPersonMode.THIRD_PERSON_MODEL;
    }

    @NotNull
    @Override
    public FirstPersonConfiguration getFirstPersonConfiguration() {
        return FIRST_PERSON_CONFIG;
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
        if (config.position != null) {
            bone.addPos((float)config.position.x, (float)config.position.y, (float)config.position.z);
        }
        if (config.rotation != null) {
            bone.addRot(
                    MathHelper.RADIANS_PER_DEGREE * (float)config.rotation.x,
                    MathHelper.RADIANS_PER_DEGREE * (float)config.rotation.y,
                    MathHelper.RADIANS_PER_DEGREE * (float)config.rotation.z
            );
        }
        if (config.scale != null) {
            bone.mulScale(
                    (float)config.scale.x,
                    (float)config.scale.y,
                    (float)config.scale.z
            );
        }

        return bone;
    }
}