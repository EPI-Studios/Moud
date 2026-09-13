package com.meekdev.moud.mod.mixin.client;

import com.meekdev.moud.core.instance.ChatInputBar;
import com.meekdev.moud.mod.adapter.chat.ChatLook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// a place that turned typing off keeps the chat key from opening the box
@Mixin(Minecraft.class)
abstract class ChatInputMixin {

    @Inject(method = "openChatScreen", at = @At("HEAD"), cancellable = true)
    private void moud$closed(ChatComponent.ChatMethod method, CallbackInfo ci) {
        ChatInputBar bar = ChatLook.inputBar();
        if (bar != null && !bar.enabled) ci.cancel();
    }
}
