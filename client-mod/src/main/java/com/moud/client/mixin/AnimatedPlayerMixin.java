package com.moud.client.mixin;

import com.moud.client.animation.ClientAnimationPlayer;
import com.moud.client.animation.IAnimatedPlayer;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(AbstractClientPlayerEntity.class)
public class AnimatedPlayerMixin implements IAnimatedPlayer {

    @Unique
    private ClientAnimationPlayer moud$animationPlayer;

    @Override
    public ClientAnimationPlayer getAnimationPlayer() {
        if (this.moud$animationPlayer == null) {
            this.moud$animationPlayer = new ClientAnimationPlayer((AbstractClientPlayerEntity)(Object)this);
        }
        return this.moud$animationPlayer;
    }
}