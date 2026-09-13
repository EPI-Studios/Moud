package com.meekdev.moud.mod.mixin.client;

import com.meekdev.moud.mod.adapter.chat.ChatView;
import com.meekdev.moud.mod.adapter.chat.ClientChat;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// while a place runs, the game's chat keeps its input and history but not its window: messages go to
// our chat, which draws them, and the game's drawing call is where the items and tooltips go in
@Mixin(ChatComponent.class)
abstract class ChatComponentMixin {

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V",
            at = @At("HEAD"), cancellable = true)
    private void moud$draw(GuiGraphicsExtractor graphics, Font font, int ticks, int mouseX, int mouseY,
            ChatComponent.DisplayMode mode, boolean cursor, CallbackInfo ci) {
        if (!ChatView.active()) return;
        ChatView.extract(graphics, font, mouseX, mouseY, mode.foreground);
        ci.cancel();
    }

    @Inject(method = "captureClickableText", at = @At("HEAD"), cancellable = true)
    private void moud$clicks(ActiveTextCollector collector, int screenHeight, int ticks,
            ChatComponent.DisplayMode mode, CallbackInfo ci) {
        if (ChatView.active()) ci.cancel();
    }

    @Inject(method = "addMessage", at = @At("HEAD"), cancellable = true)
    private void moud$message(Component message, MessageSignature signature, GuiMessageSource source,
            GuiMessageTag tag, CallbackInfo ci) {
        if (!ChatView.active()) return;
        ClientChat.INSTANCE.vanilla(message);
        ci.cancel();
    }

    @Inject(method = "scrollChat", at = @At("HEAD"), cancellable = true)
    private void moud$scroll(int amount, CallbackInfo ci) {
        if (ChatView.scroll(amount / 7.0)) ci.cancel();
    }
}
