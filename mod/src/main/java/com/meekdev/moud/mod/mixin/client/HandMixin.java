package com.meekdev.moud.mod.mixin.client;

import com.meekdev.moud.core.instance.Appearance;
import com.meekdev.moud.core.instance.Character;
import com.meekdev.moud.core.instance.FirstPerson;
import com.meekdev.moud.core.instance.Rig;
import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.adapter.render.Hands;
import com.meekdev.moud.mod.client.ClientScene;
import com.meekdev.moud.mod.features.Feature;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// the pass that draws what you see of yourself, and which of the four it draws
//
// the first person hand comes straight from the game renderer, with no event and no amnetic hook in
// front of it. that is also the only moment the depth buffer is clear and the projection is the
// flat seventy degrees a hand is drawn at, so it is the one place ours can go
//
// which of the four happens is a property of your own body rather than a switch the place throws,
// because it is a thing about that body: a spider has no hand to hold up, and a place with both in
// it should not have to pick one answer for the two
@Mixin(ItemInHandRenderer.class)
abstract class HandMixin {

    @Inject(method = "renderHandsWithItems", at = @At("HEAD"), cancellable = true)
    private void moud$suppress(float tickDelta, PoseStack pose, SubmitNodeCollector collector, LocalPlayer player, int light, CallbackInfo ci) {
        Character mine = ClientScene.own();
        Appearance look = mine == null ? null : Rig.appearance(mine);

        // no body of your own yet -- before a place has spawned you, and for a frame after a
        // respawn. the switch is the place's answer for that moment and for nothing else
        if (look == null) {
            if (MoudMod.features().isOn(Feature.HAND)) return;
            ci.cancel();
            return;
        }

        // the game's own hand, its own animations, and whatever it is really holding
        if (look.firstPerson == FirstPerson.HAND) return;

        // holding something, the game draws the item and no arm at all, so our arm has nothing to add
        // and the game's pass is the one that knows how an item is held up in front of a face
        if (look.firstPerson == FirstPerson.ARM
                && (!player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty())) {
            return;
        }

        // ours, from the body the client owns, before the call that would have drawn the game's
        if (look.firstPerson == FirstPerson.ARM) Hands.draw(tickDelta);

        // and for a body seen whole, nothing here at all: the world pass already drew it, and the
        // head it drew is being looked at from the inside, where its faces point away
        ci.cancel();
    }
}
