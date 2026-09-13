package com.meekdev.moud.script.host.audio;

import com.meekdev.moud.core.audio.Sound;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.script.api.AudioRef;
import com.meekdev.moud.script.host.Args;
import com.meekdev.moud.script.host.Host;
import com.meekdev.moud.script.host.HostError;
import com.meekdev.moud.script.host.HostObject;
import com.meekdev.moud.script.host.HostSignal;
import com.meekdev.moud.script.host.Members;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AudioLibrary {

    private static final List<String> SHAPES = List.of("sine", "triangle", "saw", "square");
    private static final List<String> QUANTIZE = List.of("immediate", "beat", "bar");
    private static final List<String> FILTERS = List.of("lowpass", "highpass", "bandpass", "peaking");

    private record Voice(AudioRef.Voice ref) implements HostObject {

        private static final Members METHODS = new Members("Voice")
                .method("stop", "() -> ()", a -> run(() -> ref(a).stop()))
                .method("setVolume", "(volume: number) -> ()", a -> run(() -> ref(a).volume(a.number(1))))
                .method("setPitch", "(pitch: number) -> ()", a -> run(() -> ref(a).pitch(a.number(1))))
                .method("fade", "(volume: number, seconds: number) -> ()", a -> run(() -> ref(a).fade(a.number(1), a.number(2))))
                .method("fadeOut", "(seconds: number) -> ()", a -> run(() -> ref(a).fadeOut(a.number(1))))
                .method("setPosition", "(at: Vector3) -> ()", a -> run(() -> ref(a).position(a.vector(1))))
                .method("isPlaying", "() -> boolean", a -> ref(a).playing());

        private static AudioRef.Voice ref(Args a) {
            return a.self(Voice.class).ref();
        }

        @Override
        public String typeName() {
            return "Voice";
        }

        @Override
        public Object get(String key) {
            return METHODS.get(key);
        }
    }

    private record Music(AudioRef.Music ref) implements HostObject {

        private static final Members METHODS = new Members("Music")
                .method("setState", "(name: string, seconds: number?) -> ()", a -> run(() -> ref(a).state(a.string(1), a.number(2, 0))))
                .method("transitionTo", "(name: string, seconds: number?, quantize: string?) -> ()",
                        a -> run(() -> ref(a).transition(a.string(1), a.number(2, 0), quantize(a, 3))))
                .method("setLayer", "(layer: number, volume: number) -> ()", a -> run(() -> ref(a).layer(a.integer(1) - 1, a.number(2))))
                .method("blend", "(intensity: number) -> ()", a -> run(() -> ref(a).blend(a.number(1))))
                .method("stop", "(seconds: number?) -> ()", a -> run(() -> ref(a).stop(a.number(1, 0))));

        private static AudioRef.Music ref(Args a) {
            return a.self(Music.class).ref();
        }

        @Override
        public String typeName() {
            return "Music";
        }

        @Override
        public Object get(String key) {
            return METHODS.get(key);
        }
    }

    private AudioLibrary() {}

    private static Object run(Runnable action) {
        action.run();
        return null;
    }

    public static void install(Host host) {
        Members sounds = host.instances().of(Classes.SOUND);
        sounds.method("play", "() -> ()", a -> {
            Sound sound = a.self(Sound.class);
            Instances.setBool(sound, Classes.SOUND.property("playing"), true);
            Instances.setNum(sound, Classes.SOUND.property("plays"), sound.plays + 1);
            return null;
        });
        sounds.method("stop", "() -> ()", a -> {
            Instances.setBool(a.self(Sound.class), Classes.SOUND.property("playing"), false);
            return null;
        });

        AudioRef audio = host.audio();
        if (audio == null) return;
        host.api().declare(Voice.METHODS.decl());
        host.api().declare(Music.METHODS.decl());
        host.api().alias("PlayOptions", "{ volume: number?, pitch: number?, looped: boolean?, bus: string?, priority: number?, "
                + "at: Vector3?, minDistance: number?, maxDistance: number?, rollOff: number?, fadeIn: number?, stream: boolean? }");

        HostSignal beat = new HostSignal(host, "StepSignal", "audio.beat");
        HostSignal bar = new HostSignal(host, "StepSignal", "audio.bar");
        host.onRenderStep(dt -> audio.drainBeats(n -> beat.fire((double) n), n -> bar.fire((double) n)));

        Members members = new Members("Audio")
                .function("play", "(soundId: string, options: PlayOptions?) -> Voice?", a -> voice(audio.play(a.string(0), options(a, 1))))
                .function("playEvent", "(name: string, at: Vector3?) -> Voice?", a -> voice(audio.playEvent(a.string(0), a.vector(1, null))))
                .function("stinger", "(name: string, quantize: string?) -> Voice?", a -> voice(audio.stinger(a.string(0), quantize(a, 1))))
                .function("defineEvent", "(name: string, event: { sounds: { string }, bus: string?, volume: any?, pitch: any?, looped: boolean? }) -> ()",
                        a -> run(() -> defineEvent(audio, a)))
                .function("tempo", "(bpm: number, beatsPerBar: number?) -> ()", a -> run(() -> audio.tempo(a.number(0), a.integer(1, 4))))
                .function("stopTempo", "() -> ()", a -> run(audio::stopTempo))
                .function("beats", "() -> number", a -> audio.beats())
                .value("beat", "StepSignal", beat)
                .value("bar", "StepSignal", bar)
                .function("setParameter", "(name: string, value: number) -> ()", a -> run(() -> audio.parameter(a.string(0), a.number(1))))
                .function("getParameter", "(name: string) -> number", a -> audio.parameter(a.string(0)))
                .function("bindBusVolume", "(parameter: string, bus: string, curve: { { number } }) -> ()",
                        a -> run(() -> audio.bindBusVolume(a.string(0), a.string(1), curve(a, 2))))
                .function("setSwitch", "(group: string, value: string) -> ()", a -> run(() -> audio.switchTo(a.string(0), a.string(1))))
                .function("getSwitch", "(group: string) -> string?", a -> audio.switchOf(a.string(0)))
                .function("lfo", "(parameter: string, shape: string, hertz: number, min: number, max: number) -> ()", a -> {
                    String shape = a.string(1);
                    if (!SHAPES.contains(shape)) throw new HostError("unknown lfo shape '%s'", shape);
                    audio.lfo(a.string(0), shape, a.number(2), a.number(3), a.number(4));
                    return null;
                })
                .function("snapshot", "(volumes: { [string]: number }, seconds: number?) -> ()", a -> {
                    Map<String, Double> volumes = new LinkedHashMap<>();
                    for (Map.Entry<String, Object> entry : a.map(0).entrySet()) {
                        if (!(entry.getValue() instanceof Number n)) throw new HostError("snapshot must be a table of bus volumes");
                        volumes.put(entry.getKey(), n.doubleValue());
                    }
                    audio.snapshot(volumes, a.number(1, 0));
                    return null;
                })
                .function("clearSnapshot", "(seconds: number?) -> ()", a -> run(() -> audio.clearSnapshot(a.number(0, 0))))
                .function("sidechain", "(sourceBus: string, targetBus: string, amount: number) -> ()",
                        a -> run(() -> audio.sidechain(a.string(0), a.string(1), a.number(2))))
                .function("reverb", "(decaySeconds: number, wet: number) -> ()", a -> run(() -> audio.reverb(a.number(0), a.number(1))))
                .function("hrtf", "(enabled: boolean) -> ()", a -> run(() -> audio.hrtf(a.truthy(0))))
                .function("occlusion", "(enabled: boolean) -> ()", a -> run(() -> audio.occlusion(a.truthy(0))))
                .function("music", "(music: { layers: { string }, states: { [string]: { number } }?, bus: string? }) -> Music", a -> music(audio, a))
                .function("voiceChat", "(settings: { enabled: boolean?, strength: number?, filters: { any }? }) -> ()",
                        a -> run(() -> voiceChat(audio, a)))
                .function("voiceCount", "() -> number", a -> (double) audio.voiceCount());
        host.global("audio", "Audio", members);
        host.declare(members);
    }

    private static Object voice(AudioRef.Voice voice) {
        return voice == null ? null : new Voice(voice);
    }

    private static String quantize(Args a, int at) {
        String value = a.string(at, "immediate");
        if (!QUANTIZE.contains(value)) throw new HostError("unknown quantize '%s'", value);
        return value;
    }

    private static AudioRef.Options options(Args a, int at) {
        AudioRef.Options d = AudioRef.Options.DEFAULT;
        if (!a.has(at)) return d;
        Map<String, Object> o = a.map(at);
        return new AudioRef.Options(number(o, "volume", d.volume()), number(o, "pitch", d.pitch()),
                bool(o, "looped", d.looped()), text(o, "bus", d.bus()), (int) number(o, "priority", d.priority()),
                o.get("at") instanceof Vector3 v ? v : null, number(o, "minDistance", d.minDistance()),
                number(o, "maxDistance", d.maxDistance()), number(o, "rollOff", d.rollOff()),
                number(o, "fadeIn", d.fadeIn()), bool(o, "stream", d.stream()));
    }

    private static double number(Map<String, Object> map, String key, double fallback) {
        Object value = map.get(key);
        if (value == null) return fallback;
        if (value instanceof Number n) return n.doubleValue();
        throw new HostError("%s expects a number", key);
    }

    private static boolean bool(Map<String, Object> map, String key, boolean fallback) {
        Object value = map.get(key);
        return value == null ? fallback : !Boolean.FALSE.equals(value);
    }

    private static String text(Map<String, Object> map, String key, String fallback) {
        Object value = map.get(key);
        if (value == null) return fallback;
        if (value instanceof String s) return s;
        throw new HostError("%s expects a string", key);
    }

    private static double[] range(Map<String, Object> map, String key) {
        return switch (map.get(key)) {
            case null -> new double[] {1, 1};
            case Number n -> new double[] {n.doubleValue(), n.doubleValue()};
            case List<?> l when l.size() >= 2 && l.get(0) instanceof Number x && l.get(1) instanceof Number y ->
                    new double[] {x.doubleValue(), y.doubleValue()};
            default -> throw new HostError("%s expects a number or {min, max}", key);
        };
    }

    private static List<String> strings(Object value, String what) {
        if (!(value instanceof List<?> list)) throw new HostError(what);
        List<String> out = new ArrayList<>();
        for (Object entry : list) {
            if (!(entry instanceof String s)) throw new HostError(what);
            out.add(s);
        }
        return out;
    }

    private static void defineEvent(AudioRef audio, Args a) {
        String name = a.string(0);
        Map<String, Object> event = a.map(1);
        List<String> sounds = strings(event.get("sounds"), "event has no sounds list");
        if (sounds.isEmpty()) throw new HostError("an event needs at least one sound");
        double[] volume = range(event, "volume");
        double[] pitch = range(event, "pitch");
        audio.defineEvent(name, sounds, text(event, "bus", "sfx"), volume[0], volume[1], pitch[0], pitch[1], bool(event, "looped", false));
    }

    private static double[][] curve(Args a, int at) {
        List<Object> points = a.list(at);
        double[][] out = new double[points.size()][];
        for (int n = 0; n < points.size(); n++) {
            if (!(points.get(n) instanceof List<?> pair) || pair.size() < 2
                    || !(pair.get(0) instanceof Number x) || !(pair.get(1) instanceof Number y)) {
                throw new HostError("curve must be a list of {input, output} pairs");
            }
            out[n] = new double[] {x.doubleValue(), y.doubleValue()};
        }
        return out;
    }

    private static Object music(AudioRef audio, Args a) {
        Map<String, Object> music = a.map(0);
        List<String> layers = strings(music.get("layers"), "music has no layers list");
        Map<String, double[]> states = new LinkedHashMap<>();
        if (music.get("states") instanceof Map<?, ?> table) {
            for (Map.Entry<?, ?> entry : table.entrySet()) {
                if (!(entry.getValue() instanceof List<?> volumes)) continue;
                double[] levels = new double[volumes.size()];
                for (int n = 0; n < levels.length; n++) {
                    levels[n] = volumes.get(n) instanceof Number v ? v.doubleValue() : 0;
                }
                states.put(String.valueOf(entry.getKey()), levels);
            }
        }
        return new Music(audio.music(text(music, "bus", "music"), layers, states));
    }

    private static void voiceChat(AudioRef audio, Args a) {
        Map<String, Object> settings = a.map(0);
        List<AudioRef.Filter> filters = new ArrayList<>();
        if (settings.get("filters") instanceof List<?> list) {
            for (Object entry : list) {
                if (!(entry instanceof Map<?, ?> raw)) continue;
                @SuppressWarnings("unchecked")
                Map<String, Object> one = (Map<String, Object>) raw;
                String kind = text(one, "kind", "lowpass");
                if (!FILTERS.contains(kind)) throw new HostError("unknown filter '%s'", kind);
                filters.add(new AudioRef.Filter(kind, number(one, "frequency", 1000), number(one, "q", 0.707), number(one, "gain", 0)));
            }
        }
        audio.voiceChat(bool(settings, "enabled", true), number(settings, "strength", 1), filters);
    }
}
