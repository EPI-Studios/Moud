package com.moud.client.fabric.mixin.veil;

import foundry.veil.api.client.render.framebuffer.FramebufferStack;
import java.util.List;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = FramebufferStack.class, remap = false)
public final class FramebufferStackMixin {
    @Unique
    private static final Logger LOGGER = LoggerFactory.getLogger("MoudVeil");

    @Shadow
    @Final
    private static List<?> STATE_STACK;

    @Unique
    private static int moud$emptyPopReports;

    @Inject(method = "pop", at = @At("HEAD"), cancellable = true)
    private static void moud$guardEmptyPop(Identifier name, CallbackInfo ci) {
        if (STATE_STACK != null && STATE_STACK.isEmpty()) {
            if (moud$emptyPopReports++ < 3) {
                LOGGER.error("Veil FramebufferStack.pop called while empty (name={})", name,
                        new RuntimeException("FramebufferStack underflow stacktrace"));
            }
            ci.cancel();
        }
    }
}
