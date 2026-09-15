package com.meekdev.moud.mod.client.editor.assets;

import com.meekdev.moud.mod.adapter.audio.ResonaAudio;
import com.meekdev.moud.script.api.AudioRef;
import org.jspecify.annotations.Nullable;

public final class SoundPreview {

    private static AudioRef.@Nullable Voice playing;
    private static String current = "";

    private SoundPreview() {}

    public static void toggle(String soundId) {
        boolean same = soundId.equals(current) && playing != null && playing.playing();
        stop();
        if (same) return;
        playing = ResonaAudio.INSTANCE.play(soundId, AudioRef.Options.DEFAULT);
        current = soundId;
    }

    public static boolean playing(String soundId) {
        return soundId.equals(current) && playing != null && playing.playing();
    }

    public static void stop() {
        if (playing != null) playing.stop();
        playing = null;
        current = "";
    }
}
