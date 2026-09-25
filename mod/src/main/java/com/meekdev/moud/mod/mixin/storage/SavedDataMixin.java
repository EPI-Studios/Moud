package com.meekdev.moud.mod.mixin.storage;

import com.meekdev.moud.mod.server.WorldSaving;
import java.util.concurrent.CompletableFuture;
import net.minecraft.world.level.storage.SavedDataStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SavedDataStorage.class)
abstract class SavedDataMixin {

    @Inject(method = "scheduleSave", at = @At("HEAD"), cancellable = true)
    private void moud$dropSave(CallbackInfoReturnable<CompletableFuture<?>> cir) {
        if (WorldSaving.off()) cir.setReturnValue(CompletableFuture.completedFuture(null));
    }
}
