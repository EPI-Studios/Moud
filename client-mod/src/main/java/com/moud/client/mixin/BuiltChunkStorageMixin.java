package com.moud.client.mixin;

import net.minecraft.client.render.BuiltChunkStorage;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BuiltChunkStorage.class)
public class BuiltChunkStorageMixin {

    /**
     * Reduce the initial view distance to prevent massive OpenGL buffer allocation freeze.
     */
    @ModifyVariable(
        method = "<init>",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0
    )
    private static int moud$reduceInitialViewDistance(int viewDistance, ChunkBuilder builder, World world, int originalViewDistance, WorldRenderer worldRenderer) {
        return Math.min(viewDistance, 3);
    }
}
