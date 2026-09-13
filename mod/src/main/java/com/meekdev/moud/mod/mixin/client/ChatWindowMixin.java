package com.meekdev.moud.mod.mixin.client;

import com.meekdev.moud.core.instance.ChatWindow;
import com.meekdev.moud.mod.adapter.chat.ChatLook;
import com.meekdev.moud.mod.adapter.chat.ChatText;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import org.joml.Matrix3x2f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// the chat window, shaped by the place's ChatWindow when it has one. every number the game reads off
// the player's chat options is answered here instead, so a place changes the chat without touching
// anyone's settings
@Mixin(ChatComponent.class)
abstract class ChatWindowMixin {

    private static final String DRAW = "extractRenderState(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;)V";

    @Shadow public abstract boolean isChatFocused();

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V",
            at = @At("HEAD"), cancellable = true)
    private void moud$hidden(GuiGraphicsExtractor graphics, Font font, int ticks, int mouseX, int mouseY,
            ChatComponent.DisplayMode mode, boolean cursor, CallbackInfo ci) {
        ChatWindow window = ChatLook.window();
        if (window != null && !window.enabled) ci.cancel();
    }

    @Inject(method = "captureClickableText", at = @At("HEAD"), cancellable = true)
    private void moud$hiddenClicks(ActiveTextCollector collector, int screenHeight, int ticks,
            ChatComponent.DisplayMode mode, CallbackInfo ci) {
        ChatWindow window = ChatLook.window();
        if (window != null && !window.enabled) ci.cancel();
    }

    // moved as a whole: down through the screen height the drawing and the clicking both measure from,
    // and across through the pose they both build, so a link in a moved window is clicked where it is drawn
    @ModifyVariable(method = DRAW, at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int moud$down(int screenHeight) {
        ChatWindow window = ChatLook.window();
        return window == null ? screenHeight : screenHeight + (int) window.offsetY;
    }

    @Inject(method = "lambda$extractRenderState$0", at = @At("TAIL"))
    private static void moud$across(float scale, Matrix3x2f pose, CallbackInfo ci) {
        ChatWindow window = ChatLook.window();
        if (window != null && scale > 0) pose.translate((float) window.offsetX / scale, 0);
    }

    @Inject(method = "getWidth()I", at = @At("HEAD"), cancellable = true)
    private void moud$width(CallbackInfoReturnable<Integer> cir) {
        ChatWindow window = ChatLook.window();
        if (window != null) cir.setReturnValue((int) window.width);
    }

    @Inject(method = "getHeight()I", at = @At("HEAD"), cancellable = true)
    private void moud$height(CallbackInfoReturnable<Integer> cir) {
        ChatWindow window = ChatLook.window();
        if (window != null) cir.setReturnValue((int) (isChatFocused() ? window.focusedHeight : window.height));
    }

    @Inject(method = "getScale()D", at = @At("HEAD"), cancellable = true)
    private void moud$scale(CallbackInfoReturnable<Double> cir) {
        ChatWindow window = ChatLook.window();
        if (window != null) cir.setReturnValue(window.scale);
    }

    @Inject(method = "getLineHeight()I", at = @At("HEAD"), cancellable = true)
    private void moud$lineHeight(CallbackInfoReturnable<Integer> cir) {
        ChatWindow window = ChatLook.window();
        if (window != null) cir.setReturnValue((int) (9.0 * (window.lineSpacing + 1.0)));
    }

    // the three the draw reads straight off the options: text opacity, background opacity, spacing
    @ModifyVariable(method = DRAW, at = @At("STORE"), ordinal = 1)
    private float moud$textOpacity(float value) {
        ChatWindow window = ChatLook.window();
        return window == null ? value : (float) (1 - window.textTransparency) * 0.9f + 0.1f;
    }

    @ModifyVariable(method = DRAW, at = @At("STORE"), ordinal = 2)
    private float moud$backgroundOpacity(float value) {
        ChatWindow window = ChatLook.window();
        return window == null ? value : (float) (1 - window.backgroundTransparency);
    }

    @ModifyVariable(method = DRAW, at = @At("STORE"), ordinal = 0)
    private double moud$lineSpacing(double value) {
        ChatWindow window = ChatLook.window();
        return window == null ? value : window.lineSpacing;
    }

    // the background, in the place's colour. the line strips are filled from inside a lambda
    @Redirect(method = DRAW, at = @At(value = "INVOKE", target = "Lnet/minecraft/util/ARGB;black(F)I"))
    private int moud$background(float alpha) {
        return tinted(alpha);
    }

    @Redirect(method = "lambda$extractRenderState$1", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/ARGB;black(F)I"))
    private static int moud$lineBackground(float alpha) {
        return tinted(alpha);
    }

    private static int tinted(float alpha) {
        ChatWindow window = ChatLook.window();
        return window == null ? ARGB.black(alpha) : ARGB.color(alpha, ChatText.rgb(window.backgroundColor));
    }

    // a font set on the window reaches every line from then on
    @ModifyVariable(method = "addMessage", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Component moud$font(Component message) {
        ChatWindow window = ChatLook.window();
        if (window == null || window.font.isEmpty()) return message;
        Identifier font = Identifier.tryParse(window.font);
        if (font == null) return message;
        return Component.empty().append(message).withStyle(style -> style.withFont(new FontDescription.Resource(font)));
    }
}
