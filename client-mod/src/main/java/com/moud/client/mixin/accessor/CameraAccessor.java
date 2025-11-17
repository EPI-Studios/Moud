package com.moud.client.mixin.accessor;

import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Camera.class)
public interface CameraAccessor {
    @Invoker("setPos")
    void moud$setCameraPosition(double x, double y, double z);

    @Invoker("setRotation")
    void moud$setRotation(float yaw, float pitch);
}
