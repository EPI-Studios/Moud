package com.meekdev.moud.mod.mixin.player;

import com.meekdev.moud.core.instance.Character;
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

// the volume the game thinks a player occupies, taken from the body it is actually wearing
//
// the game's is a fixed 0.6 by 1.8 and the body's is whatever the place said. everything that asks
// the game rather than us was then wrong for a scaled body: what your sword reaches, what the
// crosshair picks, whether you are in view at all, and where a projectile stops. a two metre giant
// could be hit only in its shins
//
// this is the box, not the collision: what a body walks into is Bkun's capsule, and that already
// reads the same radius and height. this is the one everyone else asks
@Mixin(LivingEntity.class)
abstract class VolumeMixin {

    @Inject(method = "getDefaultDimensions", at = @At("RETURN"), cancellable = true)
    private void moud$fromTheBody(Pose pose, CallbackInfoReturnable<EntityDimensions> cir) {
        if (!((Object) this instanceof Player player)) return;
        Character body = body(player);
        if (body == null) return;

        // the capsule the body really has. scale grows the model and the capsule is stated apart
        // from it on purpose, so this follows the capsule and not the drawing
        float width = (float) (body.radius * 2);
        float height = (float) body.height;
        // the eye rides the head rather than a constant: a crouched or grown body looks out of
        // where its head is, which is what the game does with a pose change
        cir.setReturnValue(EntityDimensions.scalable(width, height)
                .withEyeHeight(height * 0.9f));
    }

    private static Character body(Player player) {
        String owner = player.getUUID().toString();
        Character mine = Bodies.of(ServerScene.tree(), owner);
        return mine != null ? mine : Bodies.of(ClientScene.tree(), owner);
    }
}
