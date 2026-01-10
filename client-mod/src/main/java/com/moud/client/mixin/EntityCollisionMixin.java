package com.moud.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.moud.client.collision.ClientCollisionManager;
import com.moud.client.collision.CollisionMesh;
import com.moud.client.collision.CollisionResult;
import com.moud.client.collision.MeshCollider;
import com.moud.client.movement.ClientMovementTracker;
import com.moud.client.physics.ClientPhysicsWorld;
import com.moud.client.primitives.PrimitiveMeshCollisionManager;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

@Mixin(Entity.class)
public abstract class EntityCollisionMixin {

    @Shadow public abstract Box getBoundingBox();
    @Shadow public abstract float getStepHeight();

    @ModifyReturnValue(method = "adjustMovementForCollisions", at = @At("RETURN"))
    private Vec3d moud_blendMeshCollision(Vec3d vanillaResult) {
        Entity self = (Entity) (Object) this;

        if (!(self instanceof ClientPlayerEntity)) {
            return vanillaResult;
        }

        if (ClientMovementTracker.getInstance().isPredictionEnabled()) {
            return vanillaResult;
        }

        ClientPhysicsWorld physics = ClientPhysicsWorld.getInstance();
        if (physics.isInitialized() && physics.hasStaticBodies()) {
            return moud_joltCollision(vanillaResult);
        }
        return moud_legacyCollision(vanillaResult);
    }

    @Unique
    private Vec3d moud_joltCollision(Vec3d vanillaResult) {
        Entity self = (Entity) (Object) this;
        ClientPhysicsWorld physics = ClientPhysicsWorld.getInstance();
        if (!physics.isInitialized() || !physics.hasStaticBodies()) {
            return vanillaResult;
        }

        Box box = this.getBoundingBox();
        if (box == null) {
            return vanillaResult;
        }
        Vector3f currentPos = new Vector3f(
                (float) box.minX + (float) (box.maxX - box.minX) / 2f,
                (float) box.minY,
                (float) box.minZ + (float) (box.maxZ - box.minZ) / 2f
        );
        Vector3f desiredMovement = new Vector3f(
                (float) vanillaResult.x,
                (float) vanillaResult.y,
                (float) vanillaResult.z
        );

        float width = (float) (box.maxX - box.minX);
        float height = (float) (box.maxY - box.minY);
        boolean allowStep = self.isOnGround() && vanillaResult.y <= 0.02;
        float stepHeight = allowStep ? this.getStepHeight() : 0.0f;

        float deltaTime = 0.05f;
        Vector3f actualMovement = physics.movePlayer(currentPos, desiredMovement, deltaTime, width, height, stepHeight, allowStep);

        Vec3d afterJolt = new Vec3d(actualMovement.x, actualMovement.y, actualMovement.z);
        return afterJolt;
    }

    @Unique
    private Vec3d moud_legacyCollision(Vec3d vanillaResult) {
        Entity self = (Entity) (Object) this;
        Box box = this.getBoundingBox();
        if (box == null) return vanillaResult;

        Box query = box.union(box.offset(vanillaResult)).expand(0.5);
        List<CollisionMesh> meshes = new java.util.ArrayList<>();
        meshes.addAll(ClientCollisionManager.getMeshesNear(query));
        meshes.addAll(PrimitiveMeshCollisionManager.getInstance().getMeshesNear(query));

        if (meshes.isEmpty()) return vanillaResult;

        Vec3d finalMovement = vanillaResult;
        float stepHeight = self.isOnGround() ? this.getStepHeight() : 0.0f;

        for (CollisionMesh mesh : meshes) {
            CollisionResult result = MeshCollider.collideWithStepUp(box, finalMovement, mesh, stepHeight);
            finalMovement = result.allowedMovement();
        }

        return finalMovement;
    }

}
