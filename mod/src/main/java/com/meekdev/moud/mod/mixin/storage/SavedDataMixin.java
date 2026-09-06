package com.meekdev.moud.mod.mixin.storage;

import java.util.concurrent.CompletableFuture;
import net.minecraft.world.level.storage.SavedDataStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// no data/*.dat
// scoreboards, weather, world border and the rest are vanilla state a place does not own,
// so they live for the session and go no further
@Mixin(SavedDataStorage.class)
abstract class SavedDataMixin {

    @Inject(method = "scheduleSave", at = @At("HEAD"), cancellable = true)
    private void moud$dropSave(CallbackInfoReturnable<CompletableFuture<?>> cir) {
        cir.setReturnValue(CompletableFuture.completedFuture(null));
    }
}
