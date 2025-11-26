package com.moud.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.moud.client.collision.ClientCollisionManager;
import com.moud.client.collision.CollisionMesh;
import com.moud.client.collision.CollisionResult;
import com.moud.client.collision.MeshCollider;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

@Mixin(Entity.class)
public abstract class EntityCollisionMixin {

    @Shadow public abstract Box getBoundingBox();
    @Shadow public abstract float getStepHeight();

    @ModifyReturnValue(method = "adjustMovementForCollisions", at = @At("RETURN"))
    private Vec3d moud_blendMeshCollision(Vec3d vanillaResult) {
        Box box = this.getBoundingBox();
        if (box == null) return vanillaResult;

        Box query = box.union(box.offset(vanillaResult)).expand(0.5);
        List<CollisionMesh> meshes = ClientCollisionManager.getMeshesNear(query);

        if (meshes.isEmpty()) return vanillaResult;

        Vec3d finalMovement = vanillaResult;

        for (CollisionMesh mesh : meshes) {
            CollisionResult result = MeshCollider.collideWithStepUp(box, finalMovement, mesh, this.getStepHeight());
            finalMovement = result.allowedMovement();
        }

        return finalMovement;
    }
}