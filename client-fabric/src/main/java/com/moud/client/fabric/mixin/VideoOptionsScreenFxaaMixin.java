package com.moud.client.fabric.mixin;

import com.moud.client.fabric.mixin.accessor.GameOptionsScreenAccessor;
import com.moud.client.fabric.render.MoudFxaa;
import net.minecraft.client.gui.screen.option.VideoOptionsScreen;
import net.minecraft.client.gui.widget.OptionListWidget;
import net.minecraft.client.option.SimpleOption;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VideoOptionsScreen.class)
public abstract class VideoOptionsScreenFxaaMixin {

    @Inject(method = "addOptions", at = @At("TAIL"))
    private void moud$addFxaaOption(CallbackInfo ci) {
        OptionListWidget body = ((GameOptionsScreenAccessor) this).moud$getBody();
        if (body == null) return;
        SimpleOption<Boolean> fxaa = SimpleOption.ofBoolean(
                "options.moud.fxaa",
                MoudFxaa.isEnabled(),
                MoudFxaa::setEnabled
        );
        body.addSingleOptionEntry(fxaa);
    }
}
