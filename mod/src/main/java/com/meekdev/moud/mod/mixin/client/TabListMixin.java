package com.meekdev.moud.mod.mixin.client;

import com.meekdev.moud.mod.client.TabStats;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerTabOverlay.class)
abstract class TabListMixin {

    @Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true)
    private void moud$stats(PlayerInfo info, CallbackInfoReturnable<Component> cir) {
        String columns = TabStats.of(info.getProfile().id());
        if (columns.isEmpty()) return;
        cir.setReturnValue(Component.empty().append(cir.getReturnValue())
                .append(Component.literal(columns).withStyle(ChatFormatting.GRAY)));
    }

    @Inject(method = "getPlayerInfos", at = @At("RETURN"), cancellable = true)
    private void moud$ranked(CallbackInfoReturnable<List<PlayerInfo>> cir) {
        if (!TabStats.any()) return;
        List<PlayerInfo> ranked = new ArrayList<>(cir.getReturnValue());
        ranked.sort(Comparator.comparingDouble((PlayerInfo info) -> TabStats.leading(info.getProfile().id())).reversed());
        cir.setReturnValue(ranked);
    }
}
