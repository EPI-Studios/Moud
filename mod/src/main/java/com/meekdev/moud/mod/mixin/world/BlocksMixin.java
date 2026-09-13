package com.meekdev.moud.mod.mixin.world;

import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.features.Feature;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerGameMode.class)
abstract class BlocksMixin {

    @Inject(method = "handleBlockBreakAction", at = @At("HEAD"), cancellable = true)
    private void moud$suppressBreakAction(BlockPos pos, ServerboundPlayerActionPacket.Action action,
            Direction face, int height, int sequence, CallbackInfo ci) {
        if (!MoudMod.features().isOn(Feature.BLOCK_BREAKING)) ci.cancel();
    }

    @Inject(method = "destroyBlock", at = @At("HEAD"), cancellable = true)
    private void moud$suppressBreak(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (!MoudMod.features().isOn(Feature.BLOCK_BREAKING)) cir.setReturnValue(false);
    }

    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void moud$suppressPlace(ServerPlayer player, Level level, ItemStack stack,
            InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
        if (!MoudMod.features().isOn(Feature.BLOCK_PLACING)) cir.setReturnValue(InteractionResult.FAIL);
    }
}
