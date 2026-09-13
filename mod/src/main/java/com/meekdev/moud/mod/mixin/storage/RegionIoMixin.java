package com.meekdev.moud.mod.mixin.storage;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.SimpleRegionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SimpleRegionStorage.class)
abstract class RegionIoMixin {

    @Inject(method = "write(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/nbt/CompoundTag;)Ljava/util/concurrent/CompletableFuture;",
            at = @At("HEAD"), cancellable = true)
    private void moud$dropWriteTag(ChunkPos pos, CompoundTag tag, CallbackInfoReturnable<CompletableFuture<Void>> cir) {
        cir.setReturnValue(CompletableFuture.completedFuture(null));
    }

    @Inject(method = "write(Lnet/minecraft/world/level/ChunkPos;Ljava/util/function/Supplier;)Ljava/util/concurrent/CompletableFuture;",
            at = @At("HEAD"), cancellable = true)
    private void moud$dropWriteSupplier(ChunkPos pos, Supplier<CompoundTag> tag, CallbackInfoReturnable<CompletableFuture<Void>> cir) {
        cir.setReturnValue(CompletableFuture.completedFuture(null));
    }

    @Inject(method = "read(Lnet/minecraft/world/level/ChunkPos;)Ljava/util/concurrent/CompletableFuture;",
            at = @At("HEAD"), cancellable = true)
    private void moud$dropRead(ChunkPos pos, CallbackInfoReturnable<CompletableFuture<Optional<CompoundTag>>> cir) {
        cir.setReturnValue(CompletableFuture.completedFuture(Optional.empty()));
    }
}
