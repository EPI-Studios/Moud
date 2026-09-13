package com.meekdev.moud.script.api;

import com.meekdev.moud.core.math.Vector3;
import java.util.List;
import java.util.Map;
import java.util.function.LongConsumer;

public interface AudioRef {

    interface Voice {
        void stop();
        void volume(double value);
        void pitch(double value);
        void fade(double to, double seconds);
        void fadeOut(double seconds);
        void position(Vector3 at);
        boolean playing();
    }

    interface Music {
        void state(String name, double seconds);
        void transition(String name, double seconds, String quantize);
        void layer(int index, double volume);
        void blend(double intensity);
        void stop(double seconds);
    }

    record Options(double volume, double pitch, boolean looped, String bus, int priority, Vector3 at,
                   double minDistance, double maxDistance, double rollOff, double fadeIn, boolean stream) {

        public static final Options DEFAULT = new Options(1, 1, false, "sfx", 0, null, 8, 48, 1, 0, false);
    }

    record Filter(String kind, double frequency, double q, double gainDb) {}

    Voice play(String soundId, Options options);

    Voice playEvent(String name, Vector3 at);

    Voice stinger(String name, String quantize);

    void defineEvent(String name, List<String> sounds, String bus, double volumeMin, double volumeMax,
                     double pitchMin, double pitchMax, boolean looped);

    void tempo(double bpm, int beatsPerBar);

    void stopTempo();

    double beats();

    void drainBeats(LongConsumer beat, LongConsumer bar);

    void parameter(String name, double value);

    double parameter(String name);

    void bindBusVolume(String parameter, String bus, double[][] curve);

    void switchTo(String group, String value);

    String switchOf(String group);

    void lfo(String parameter, String shape, double hertz, double min, double max);

    void snapshot(Map<String, Double> volumes, double seconds);

    void clearSnapshot(double seconds);

    void sidechain(String sourceBus, String targetBus, double amount);

    void reverb(double decaySeconds, double wet);

    void hrtf(boolean enabled);

    void occlusion(boolean enabled);

    Music music(String bus, List<String> layers, Map<String, double[]> states);

    void voiceChat(boolean enabled, double strength, List<Filter> filters);

    int voiceCount();
}
