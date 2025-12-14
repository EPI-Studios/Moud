package com.moud.client.audio.voice;

import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundCategory;

public final class VoiceClientVolume {

    private VoiceClientVolume() {
    }

    public static float readVoiceVolume() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options == null) {
            return 1.0f;
        }
        return Math.max(0.0f, Math.min(1.0f, client.options.getSoundVolume(SoundCategory.VOICE)));
    }
}

