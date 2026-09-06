package com.meekdev.moud.mod.mixin.storage;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.chunk.storage.IOWorker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// no anvil, ever
// every region read and write in the game funnels through here, chunks, entities and poi alike,
// so one cut is the whole format gone and the place file is the only thing on disk
@Mixin(IOWorker.class)
abstract class RegionIoMixin {

    @Inject(method = "store", at = @At("HEAD"), cancellable = true)
    private void moud$dropStore(CallbackInfoReturnable<CompletableFuture<Void>> cir) {
        cir.setReturnValue(CompletableFuture.completedFuture(null));
    }

    @Inject(method = "loadAsync", at = @At("HEAD"), cancellable = true)
    private void moud$dropLoad(CallbackInfoReturnable<CompletableFuture<Optional<CompoundTag>>> cir) {
        cir.setReturnValue(CompletableFuture.completedFuture(Optional.empty()));
    }
}
