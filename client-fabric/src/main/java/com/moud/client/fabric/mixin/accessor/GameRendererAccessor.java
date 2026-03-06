package com.moud.client.fabric.mixin.accessor;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Invoker;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(GameRenderer.class)
public interface GameRendererAccessor {
    @Invoker("getFov")
    double moud$getFov(Camera camera, float tickDelta, boolean changingFov);
}
