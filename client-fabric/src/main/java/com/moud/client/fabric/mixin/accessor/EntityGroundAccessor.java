package com.moud.client.fabric.mixin.accessor;

import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Entity.class)
public interface EntityGroundAccessor {
    @Accessor("onGround")
    void setOnGround(boolean onGround);
}
