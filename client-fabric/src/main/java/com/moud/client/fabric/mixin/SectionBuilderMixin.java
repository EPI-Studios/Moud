package com.moud.client.fabric.mixin;

import com.moud.client.fabric.editor.ghost.EditorGhostBlocks;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.chunk.ChunkRendererRegion;
import net.minecraft.client.render.chunk.SectionBuilder;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(SectionBuilder.class)
public abstract class SectionBuilderMixin {
    @Redirect(
            method = "build",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/chunk/ChunkRendererRegion;getBlockState(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/block/BlockState;"
            )
    )
    private BlockState moud$maskCsgBlocks(ChunkRendererRegion region, BlockPos pos) {
        BlockState state = region.getBlockState(pos);
        EditorGhostBlocks ghosts = EditorGhostBlocks.get();
        if (ghosts.shouldHideInChunks(pos, state)) {
            return Blocks.AIR.getDefaultState();
        }
        return state;
    }
}

