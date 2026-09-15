package com.meekdev.moud.mod.client.editor;

import com.meekdev.moud.mod.client.ClientPlace;
import com.meekdev.moud.mod.transport.payload.EditDownPayload;
import com.meekdev.moud.mod.transport.payload.EditUpPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;

public final class EditMode {

    private static boolean editing;
    private static boolean allowed;
    private static boolean hudWasHidden;

    private EditMode() {}

    public static void install() {
        ClientPlayNetworking.registerGlobalReceiver(EditDownPayload.TYPE, (payload, context) ->
                context.client().execute(() -> apply(payload.editing(), payload.allowed())));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> apply(false, false)));
    }

    public static boolean editing() {
        return editing;
    }

    public static boolean allowed() {
        return allowed;
    }

    public static void request(boolean edit) {
        if (!ClientPlayNetworking.canSend(EditUpPayload.TYPE)) return;
        ClientPlayNetworking.send(new EditUpPayload(edit));
    }

    private static void apply(boolean nowEditing, boolean nowAllowed) {
        allowed = nowAllowed;
        if (nowEditing == editing) return;
        editing = nowEditing;
        Minecraft minecraft = Minecraft.getInstance();
        if (editing) {
            ClientPlace.edit();
            hudWasHidden = minecraft.options.hideGui;
            minecraft.options.hideGui = true;
            minecraft.setScreen(new EditorScreen());
        } else {
            ClientPlace.play();
            minecraft.options.hideGui = hudWasHidden;
            if (minecraft.screen instanceof EditorScreen) minecraft.setScreen(null);
        }
    }
}
