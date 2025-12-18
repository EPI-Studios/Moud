package com.moud.client.audio;

import com.moud.client.MoudClientMod;
import com.moud.client.api.service.ClientAPIService;
import com.moud.client.settings.VoiceSettingsManager;
import com.moud.client.ui.screen.VoiceChatConfigScreen;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public final class VoiceKeybindManager {

    private static final String CATEGORY = "category.moud.voice";
    private static boolean initialized = false;

    public static KeyBinding PUSH_TO_TALK;

    public static KeyBinding TOGGLE_MUTE;

    public static KeyBinding TOGGLE_DEAFEN;

    public static KeyBinding OPEN_SETTINGS;

    private static boolean lastPushToTalkPressed = false;

    private VoiceKeybindManager() {
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;

        PUSH_TO_TALK = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.moud.voice.push_to_talk",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_V,
                CATEGORY
        ));

        TOGGLE_MUTE = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.moud.voice.toggle_mute",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_M,
                CATEGORY
        ));

        TOGGLE_DEAFEN = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.moud.voice.toggle_deafen",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_N,
                CATEGORY
        ));

        OPEN_SETTINGS = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.moud.voice.open_settings",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_O,
                CATEGORY
        ));
    }

    public static void tick(MinecraftClient client) {
        if (!initialized) {
            lastPushToTalkPressed = false;
            return;
        }
        if (!shouldHandleKeybinds(client)) {
            lastPushToTalkPressed = false;
            return;
        }

        VoiceSettingsManager.VoiceSettings settings = VoiceSettingsManager.get();

        boolean pushToTalkPressed = settings.activationMode == VoiceSettingsManager.ActivationMode.PUSH_TO_TALK
                && PUSH_TO_TALK.isPressed();
        if (pushToTalkPressed != lastPushToTalkPressed) {
            if (pushToTalkPressed) {
                VoiceChatController.enableLocalCapture();
            } else {
                VoiceChatController.disableLocalCapture();
            }
            lastPushToTalkPressed = pushToTalkPressed;
        }

        while (TOGGLE_MUTE.wasPressed()) {
            toggleMute();
        }

        while (TOGGLE_DEAFEN.wasPressed()) {
            toggleDeafen();
        }

        while (OPEN_SETTINGS.wasPressed()) {
            openSettings(client);
        }
    }

    private static void toggleMute() {
        VoiceSettingsManager.VoiceSettings settings = VoiceSettingsManager.get();
        settings.microphoneMuted = !settings.microphoneMuted;
        VoiceSettingsManager.save();

        if (settings.microphoneMuted) {
            VoiceChatController.disableLocalCapture();

            ClientAPIService api = ClientAPIService.INSTANCE;
            if (api != null && api.audio != null) {
                api.audio.getMicrophone().stop();
            }
        }
    }

    private static void toggleDeafen() {
        VoiceSettingsManager.VoiceSettings settings = VoiceSettingsManager.get();
        settings.deafened = !settings.deafened;
        VoiceSettingsManager.save();
    }

    private static void openSettings(MinecraftClient client) {
        if (client == null || client.currentScreen != null) {
            return;
        }
        client.setScreen(new VoiceChatConfigScreen(null));
    }

    private static boolean shouldHandleKeybinds(MinecraftClient client) {
        if (client == null || client.currentScreen != null) {
            return false;
        }
        return MoudClientMod.isOnMoudServer();
    }
}
