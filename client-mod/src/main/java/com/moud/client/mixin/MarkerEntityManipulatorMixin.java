package com.moud.client.mixin;

import com.moud.client.MoudClientMod;
import com.moud.client.axiom.MoudLightEditorScreen;
import com.moud.client.axiom.MoudModelEditorScreen;
import com.moulberry.axiom.ClientEvents;
import com.moulberry.axiom.marker.MarkerEntityManipulator;
import com.moulberry.axiom.packets.SupportedProtocol;
import net.minecraft.client.MinecraftClient;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MarkerEntityManipulator.class, remap = false)
public abstract class MarkerEntityManipulatorMixin {

    @Shadow private static boolean copyMarkerEntity;

    @Redirect(
            method = "handleRightClick",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/moulberry/axiom/ClientEvents;serverSupportsProtocol(Lcom/moulberry/axiom/packets/SupportedProtocol;)Z"
            ),
            require = 0
    )
    private static boolean moud$allowMarkerNbtRequests(SupportedProtocol protocol) {
        if (protocol == SupportedProtocol.MARKER_NBT_REQUEST && MoudClientMod.isOnMoudServer()) {
            return true;
        }
        return ClientEvents.serverSupportsProtocol(protocol);
    }

    @Inject(method = "receivedNbtData", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MinecraftClient;setScreen(Lnet/minecraft/client/gui/screen/Screen;)V"), cancellable = true)
    private static void moud$openCustomEditor(java.util.UUID uuid, NbtCompound data, CallbackInfo ci) {
        if (!MoudClientMod.isOnMoudServer() || copyMarkerEntity || data == null) {
            return;
        }
        if (!data.contains("moudType", NbtElement.STRING_TYPE)) {
            return;
        }
        String type = data.getString("moudType");
        MinecraftClient client = MinecraftClient.getInstance();
        if ("model".equals(type)) {
            client.setScreen(new MoudModelEditorScreen(uuid, data));
            ci.cancel();
        } else if ("light".equals(type)) {
            client.setScreen(new MoudLightEditorScreen(uuid, data));
            ci.cancel();
        }
    }
}
