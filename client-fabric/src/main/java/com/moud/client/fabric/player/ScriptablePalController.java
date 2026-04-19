package com.moud.client.fabric.player;

import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranimcore.animation.AnimationData;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonConfiguration;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonMode;
import com.zigythebird.playeranimcore.bones.PlayerAnimBone;
import com.zigythebird.playeranimcore.enums.PlayState;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import org.jetbrains.annotations.NotNull;

public final class ScriptablePalController extends PlayerAnimationController {

    private FirstPersonMode firstPersonMode = FirstPersonMode.NONE;
    private final FirstPersonConfiguration firstPersonConfiguration = new FirstPersonConfiguration();

    public ScriptablePalController(AbstractClientPlayerEntity player) {
        super(player, (controller, state, setter) -> PlayState.STOP);
    }

    @Override
    public boolean isActive() {
        return true;
    }

    @Override
    public void process(AnimationData state) {
        this.animationData = state;
    }

    @Override
    public @NotNull FirstPersonMode getFirstPersonMode() {
        return firstPersonMode;
    }

    @Override
    public @NotNull FirstPersonConfiguration getFirstPersonConfiguration() {
        return firstPersonConfiguration;
    }

    public void setFirstPersonMode(FirstPersonMode mode) {
        this.firstPersonMode = mode == null ? FirstPersonMode.NONE : mode;
    }

    public FirstPersonConfiguration mutableFirstPersonConfiguration() {
        return firstPersonConfiguration;
    }

    @Override
    public PlayerAnimBone get3DTransformRaw(@NotNull PlayerAnimBone bone) {
        PlayerAnimBone source = getBone(bone.getName());
        if (source != null) {
            bone.copyOtherBoneIfNotDisabled(source);
        }
        return bone;
    }
}
