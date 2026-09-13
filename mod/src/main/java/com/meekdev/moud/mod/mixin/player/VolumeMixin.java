package com.meekdev.moud.mod.mixin.player;

import com.meekdev.moud.core.character.Character;
import com.meekdev.moud.mod.adapter.physics.Bodies;
import com.meekdev.moud.mod.client.ClientScene;
import com.meekdev.moud.mod.server.ServerScene;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
abstract class VolumeMixin {

    @Inject(method = "getDefaultDimensions", at = @At("RETURN"), cancellable = true)
    private void moud$fromTheBody(Pose pose, CallbackInfoReturnable<EntityDimensions> cir) {
        if (!((Object) this instanceof Player player)) return;
        Character body = body(player);
        if (body == null) return;

        float width = (float) (body.radius * 2);
        float height = (float) body.height;
        cir.setReturnValue(EntityDimensions.scalable(width, height)
                .withEyeHeight(height * 0.9f));
    }

    private static Character body(Player player) {
        String owner = player.getUUID().toString();
        Character mine = Bodies.of(ServerScene.tree(), owner);
        return mine != null ? mine : Bodies.of(ClientScene.tree(), owner);
    }
}
