package com.moud.client.fabric.mixin;

import com.moud.client.fabric.mixin.accessor.EntityGroundAccessor;
import com.moud.client.fabric.physics.rapier.ClientRapierPhysics;
import com.moud.client.fabric.physics.rapier.ClientRapierPhysics.CharacterMove;
import com.moud.client.fabric.runtime.PlayRuntimeBus;
import com.moud.client.fabric.runtime.PlayRuntimeClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Entity.class)
public abstract class EntityObbCollisionMixin {
    private static final double DT_PER_TICK = 1.0 / 20.0;

    @ModifyVariable(method = "move", at = @At("HEAD"), argsOnly = true, index = 2)
    private Vec3d moud$deflectAgainstRapier(Vec3d movement) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || (Object) this != client.player) return movement;
        PlayRuntimeClient runtime = PlayRuntimeBus.get();
        if (runtime == null || !runtime.isActive() || runtime.isCharacterBodyDriving()) return movement;
        ClientRapierPhysics physics = ClientRapierPhysics.get();
        if (!physics.isAvailable()) return movement;
        physics.syncSceneIfNeeded();

        if (movement.x * movement.x + movement.y * movement.y + movement.z * movement.z < 1.0e-10) {
            return movement;
        }

        Entity self = (Entity) (Object) this;
        Box bb = self.getBoundingBox();
        double hw = (bb.maxX - bb.minX) * 0.5;
        double hd = (bb.maxZ - bb.minZ) * 0.5;
        double radius = Math.max(0.05, Math.min(hw, hd));
        double fullHeight = Math.max(radius * 2.0 + 1.0e-4, bb.maxY - bb.minY);

        double feetX = (bb.minX + bb.maxX) * 0.5;
        double feetY = bb.minY;
        double feetZ = (bb.minZ + bb.maxZ) * 0.5;

        CharacterMove result = physics.moveCharacter(
                feetX, feetY, feetZ,
                radius, fullHeight,
                movement.x, movement.y, movement.z,
                DT_PER_TICK);

        Vec3d velocity = self.getVelocity();
        if (result.grounded()) {
            ((EntityGroundAccessor) self).setOnGround(true);
            if (velocity.y < 0.0) self.setVelocity(velocity.x, 0.0, velocity.z);
        }
        if (movement.y > 1.0e-4 && result.dy() < movement.y - 1.0e-4 && velocity.y > 0.0) {
            self.setVelocity(velocity.x, 0.0, velocity.z);
        }
        return new Vec3d(result.dx(), result.dy(), result.dz());
    }
}
