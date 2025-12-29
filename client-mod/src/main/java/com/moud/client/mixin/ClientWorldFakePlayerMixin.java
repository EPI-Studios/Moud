package com.moud.client.mixin;

import com.moud.client.animation.ClientFakePlayerManager;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(ClientWorld.class)
public class ClientWorldFakePlayerMixin {

    @Inject(method = "addEntity", at = @At("TAIL"))
    private void onEntityAdded(Entity entity, CallbackInfo ci) {
        if (entity instanceof OtherClientPlayerEntity player) {
            ClientFakePlayerManager.getInstance().autoRegisterFakePlayer(player);
        }
    }

    @Inject(method = "removeEntity", at = @At("HEAD"))
    private void onEntityRemoved(int entityId, Entity.RemovalReason removalReason, CallbackInfo ci) {
        ClientWorld world = (ClientWorld) (Object) this;
        Entity entity = world.getEntityById(entityId);

        if (entity instanceof OtherClientPlayerEntity player) {
            if (ClientFakePlayerManager.isFakePlayer(player)) {
                Long modelId = ClientFakePlayerManager.getInstance().getModelIdByName(player.getName().getString());
                if (modelId != null) {
                    ClientFakePlayerManager.getInstance().unregisterFakePlayer(modelId);
                }
            }
        }
    }
}
