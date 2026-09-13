package com.meekdev.moud.mod.adapter.audio;

import com.meekdev.moud.script.api.AudioRef;
import com.meekdev.resona.api.Music;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.Identifier;

record ResonaMusic(Music music) implements AudioRef.Music {

    static ResonaMusic of(PlaceSounds sounds, String bus, List<String> layers, Map<String, double[]> states) {
        Music.Builder builder = Music.builder(bus);
        for (String layer : layers) {
            Identifier id = sounds.id(layer);
            // a missing layer still takes its slot, so the volumes in every state keep their meaning
            builder.layer(id != null ? id : Identifier.fromNamespaceAndPath("moud", "missing.ogg"));
        }
        states.forEach((name, volumes) -> {
            float[] levels = new float[volumes.length];
            for (int n = 0; n < levels.length; n++) levels[n] = (float) volumes[n];
            builder.state(name, levels);
        });
        return new ResonaMusic(builder.build());
    }

    @Override
    public void state(String name, double seconds) {
        music.setState(name, seconds);
    }

    @Override
    public void transition(String name, double seconds, String quantize) {
        music.transitionTo(name, seconds, ResonaAudio.quantize(quantize));
    }

    @Override
    public void layer(int index, double volume) {
        music.setLayer(index, (float) volume);
    }

    @Override
    public void blend(double intensity) {
        music.blend((float) intensity);
    }

    @Override
    public void stop(double seconds) {
        music.stop(seconds);
    }
}
