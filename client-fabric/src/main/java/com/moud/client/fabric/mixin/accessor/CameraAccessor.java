package com.moud.client.fabric.mixin.accessor;

import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Camera.class)
public interface CameraAccessor {
    @Invoker("setPos")
    void moud$setCameraPosition(double x, double y, double z);

    @Invoker("setRotation")
    void moud$setRotation(float yaw, float pitch);

    @Accessor("thirdPerson")
    void moud$setThirdPerson(boolean thirdPerson);

    @Accessor("ready")
    void moud$setReady(boolean ready);

    @Accessor("area")
    void moud$setArea(BlockView area);

    @Accessor("focusedEntity")
    void moud$setFocusedEntity(Entity focusedEntity);

    @Accessor("lastTickDelta")
    void moud$setLastTickDelta(float tickDelta);

    @Accessor("rotation")
    Quaternionf moud$getRotation();
}
