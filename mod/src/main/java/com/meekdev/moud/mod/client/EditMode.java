package com.meekdev.moud.mod.client;

import com.meekdev.amnetic.client.camera.AmneticCamera;
import com.meekdev.moud.mod.adapter.player.EditorBody;
import com.meekdev.moud.mod.adapter.render.EditorOverlay;
import com.meekdev.moud.mod.adapter.render.ViewportCapture;
import com.meekdev.moud.mod.transport.payload.EditDownPayload;
import com.meekdev.moud.mod.transport.payload.EditUpPayload;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;

public final class EditMode {

    private static boolean editing;
    private static boolean allowed;
    private static boolean hudWasHidden;
    private static int session;
    private static CameraType cameraBefore = CameraType.FIRST_PERSON;
    private static Supplier<Screen> screens = () -> null;
    private static @Nullable Screen shown;

    private EditMode() {}

    public static void install(Supplier<Screen> editorScreens) {
        screens = editorScreens;
        ClientPlayNetworking.registerGlobalReceiver(EditDownPayload.TYPE, (payload, context) ->
                context.client().execute(() -> apply(payload.editing(), payload.allowed())));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player != null && EditorBody.editing(client.player) != editing) EditorBody.set(client.player, editing);
            if (editing && client.screen instanceof PauseScreen) show(client);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> apply(false, false)));
    }

    public static boolean editing() {
        return editing;
    }

    public static int session() {
        return session;
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
            session++;
            ClientPlace.edit();
            hudWasHidden = minecraft.options.hideGui;
            cameraBefore = minecraft.options.getCameraType();
            minecraft.options.hideGui = true;
            show(minecraft);
        } else {
            AmneticCamera.clearPose();
            AmneticCamera.clearOrthographic();
            EditorOverlay.hide();
            ViewportCapture.hide();
            ClientPlace.play();
            minecraft.options.hideGui = hudWasHidden;
            minecraft.options.setCameraType(cameraBefore);
            if (shown != null && minecraft.screen == shown) minecraft.setScreen(null);
            shown = null;
        }
    }

    private static void show(Minecraft minecraft) {
        shown = screens.get();
        if (shown != null) minecraft.setScreen(shown);
    }
}
