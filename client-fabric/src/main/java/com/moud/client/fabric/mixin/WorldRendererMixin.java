package com.moud.client.fabric.mixin;

import com.moud.client.fabric.platform.MinecraftGhostBlocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.util.math.BlockPos;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {
    @Shadow @Final private MinecraftClient client;
    @Shadow @Final private BufferBuilderStorage bufferBuilders;

    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/BufferBuilderStorage;getEntityVertexConsumers()Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;",
                    shift = At.Shift.AFTER
            )
    )
    private void moud$renderGhostBlocks(RenderTickCounter tickCounter, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightmapTextureManager lightmapTextureManager, Matrix4f matrix4f, Matrix4f matrix4f2, CallbackInfo ci) {
        MinecraftGhostBlocks ghosts = MinecraftGhostBlocks.get();
        if (!ghosts.isActive()) {
            return;
        }
        if (client == null || client.world == null || camera == null) {
            return;
        }

        Vec3d cam = camera.getPos();

        BlockRenderManager brm = client.getBlockRenderManager();
        if (brm == null) {
            return;
        }

        VertexConsumerProvider.Immediate consumers = bufferBuilders.getEntityVertexConsumers();
        MatrixStack matrices = new MatrixStack();

        int lightValue = 0x00F000F0; // LightmapTextureManager.MAX_LIGHT_COORDINATE
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        for (long packed : ghosts.previewPositions()) {
            int px = BlockPos.unpackLongX(packed);
            int py = BlockPos.unpackLongY(packed);
            int pz = BlockPos.unpackLongZ(packed);

            if (ghosts.phase() == MinecraftGhostBlocks.Phase.AWAITING_ACK) {
                mutable.set(px, py, pz);
                var state = client.world.getBlockState(mutable);
                if (state != null && state.getBlock() == ghosts.csgDefaultState().getBlock()) {
                    continue;
                }
            }
            matrices.push();
            matrices.translate(px - cam.x, py - cam.y, pz - cam.z);
            brm.renderBlockAsEntity(ghosts.csgDefaultState(), matrices, consumers, lightValue, OverlayTexture.DEFAULT_UV);
            matrices.pop();
        }
    }
}
