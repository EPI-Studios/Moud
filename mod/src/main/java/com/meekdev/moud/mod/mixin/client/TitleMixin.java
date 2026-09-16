package com.meekdev.moud.mod.mixin.client;

import com.meekdev.moud.mod.client.WindowApi;
import com.meekdev.moud.mod.place.Game;
import com.meekdev.moud.mod.place.PlaceToml;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
abstract class TitleMixin {

    @Inject(method = "createTitle", at = @At("HEAD"), cancellable = true)
    private void moud$title(CallbackInfoReturnable<String> cir) {
        String title = WindowApi.INSTANCE.titleOverride();
        if (title != null) cir.setReturnValue(title);
        else if (Game.standalone()) cir.setReturnValue(PlaceToml.config().name());
    }
}
