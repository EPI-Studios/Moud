package com.meekdev.moud.mod.mixin.storage;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.PlayerDataStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerDataStorage.class)
abstract class PlayerDataMixin {

    @Inject(method = "save", at = @At("HEAD"), cancellable = true)
    private void moud$dropSave(Player player, CallbackInfo ci) {
        ci.cancel();
    }
}
