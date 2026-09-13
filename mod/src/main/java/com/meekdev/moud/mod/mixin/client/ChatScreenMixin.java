package com.meekdev.moud.mod.mixin.client;

import com.meekdev.moud.core.chat.ChatInputBar;
import com.meekdev.moud.mod.adapter.chat.ChatLook;
import com.meekdev.moud.mod.adapter.chat.ChatText;
import com.meekdev.moud.mod.adapter.chat.ChatView;
import com.meekdev.moud.mod.adapter.chat.ClientChat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
abstract class ChatScreenMixin {

    @Shadow protected EditBox input;
    @Shadow private CommandSuggestions commandSuggestions;

    @Inject(method = "init", at = @At("TAIL"))
    private void moud$style(CallbackInfo ci) {
        if (!ChatView.active()) return;
        ChatInputBar bar = ChatLook.inputBarOrDefault();
        input.setMaxLength(bar.maxLength);
        input.setTextColor(ChatView.argbOf(bar.textColor, 1));
        if (!bar.placeholder.isEmpty()) {
            input.setHint(ChatText.of("<color=" + String.format("#%06x", ChatText.rgb(bar.placeholderColor)) + ">"
                    + bar.placeholder + "</color>"));
        }
        if (!bar.autocomplete) commandSuggestions.setAllowSuggestions(false);
        ClientChat.INSTANCE.opened();
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void moud$closed(CallbackInfo ci) {
        if (ChatView.active()) ClientChat.INSTANCE.closed();
    }

    @Redirect(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V", ordinal = 0))
    private void moud$bar(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int colour) {
        if (!ChatView.active()) {
            graphics.fill(x0, y0, x1, y1, colour);
            return;
        }
        ChatInputBar bar = ChatLook.inputBarOrDefault();
        int argb = ChatView.argbOf(bar.backgroundColor, 1 - bar.backgroundTransparency);
        if ((argb >>> 24) != 0) graphics.fill(x0, y0, x1, y1, argb);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void moud$click(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (commandSuggestions.mouseClicked(event)) return;
        if (ChatView.click(event.x(), event.y(), event.button())) cir.setReturnValue(true);
    }

    @Inject(method = "onEdited", at = @At("TAIL"))
    private void moud$typing(String text, CallbackInfo ci) {
        if (ChatView.active()) ClientChat.INSTANCE.typing(text);
    }

    @Inject(method = "handleChatInput", at = @At("HEAD"), cancellable = true)
    private void moud$send(String message, boolean addToRecent, CallbackInfo ci) {
        if (!ChatView.active()) return;
        String normal = ((ChatScreen) (Object) this).normalizeChatMessage(message);
        if (normal.isEmpty() || normal.startsWith("/")) return;
        if (addToRecent) Minecraft.getInstance().gui.getChat().addRecentChat(normal);
        ClientChat.INSTANCE.typed(normal);
        ci.cancel();
    }
}
