package com.meekdev.moud.mod.mixin.player;

import com.meekdev.moud.mod.server.tool.ServerTools;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
abstract class DropMixin {

    @Inject(method = "drop(Z)V", at = @At("HEAD"), cancellable = true)
    private void moud$dropTool(boolean all, CallbackInfo ci) {
        if (ServerTools.drop((ServerPlayer) (Object) this)) ci.cancel();
    }

    @Inject(method = "drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;", at = @At("HEAD"), cancellable = true)
    private void moud$throwTool(ItemStack stack, boolean randomly, boolean thrower, CallbackInfoReturnable<ItemEntity> cir) {
        if (ServerTools.throwing((ServerPlayer) (Object) this, stack)) cir.setReturnValue(null);
    }
}
