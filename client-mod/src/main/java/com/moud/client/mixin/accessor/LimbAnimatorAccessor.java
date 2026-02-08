package com.moud.client.mixin.accessor;

import net.minecraft.entity.LimbAnimator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LimbAnimator.class)
public interface LimbAnimatorAccessor {
    @Accessor("speed")
    void moud$setSpeedRaw(float speed);

    @Accessor("prevSpeed")
    void moud$setPrevSpeedRaw(float prevSpeed);

    @Accessor("pos")
    void moud$setPosRaw(float pos);

}