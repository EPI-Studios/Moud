package com.meekdev.moud.script.bind;

import com.meekdev.moud.core.math.Vec3;
import com.meekdev.moud.script.api.AudioRef;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;
import net.hollowcube.luau.LuaFunc;
import net.hollowcube.luau.LuaState;
import net.hollowcube.luau.LuaType;

// the audio global a client place gets: one-off sounds, events, music, the tempo and the mix
public final class Audio {

    static final int VOICE = 11;
    static final int MUSIC = 12;

    private static final String VOICE_METHODS = "moud.voice.methods";
    private static final String MUSIC_METHODS = "moud.music.methods";

    private Audio() {}

    public static void install(LuaState state, AudioRef audio, Signals.Handlers beat, Signals.Handlers bar) {
        methods(state, VOICE, VOICE_METHODS, "voice", voiceMethods());
        methods(state, MUSIC, MUSIC_METHODS, "music", musicMethods());

        state.newTable();
        function(state, "play", s -> {
            push(s, audio.play(s.checkString(1), options(s, 2)));
            return 1;
        });
        function(state, "playEvent", s -> {
            push(s, audio.playEvent(s.checkString(1), s.isNoneOrNil(2) ? null : Values.vec3(s, 2)));
            return 1;
        });
        function(state, "stinger", s -> {
            push(s, audio.stinger(s.checkString(1), quantize(s, 2)));
            return 1;
        });
        function(state, "defineEvent", s -> {
            defineEvent(s, audio);
            return 0;
        });
        function(state, "tempo", s -> {
            audio.tempo(s.checkNumber(1), s.isNoneOrNil(2) ? 4 : (int) s.checkNumber(2));
            return 0;
        });
        function(state, "stopTempo", s -> {
            audio.stopTempo();
            return 0;
        });
        function(state, "beats", s -> {
            s.pushNumber(audio.beats());
            return 1;
        });
        function(state, "setParameter", s -> {
            audio.parameter(s.checkString(1), s.checkNumber(2));
            return 0;
        });
        function(state, "getParameter", s -> {
            s.pushNumber(audio.parameter(s.checkString(1)));
            return 1;
        });
        function(state, "bindBusVolume", s -> {
            audio.bindBusVolume(s.checkString(1), s.checkString(2), curve(s, 3));
            return 0;
        });
        function(state, "setSwitch", s -> {
            audio.switchTo(s.checkString(1), s.checkString(2));
            return 0;
        });
        function(state, "getSwitch", s -> {
            String value = audio.switchOf(s.checkString(1));
            if (value == null) s.pushNil(); else s.pushString(value);
            return 1;
        });
        function(state, "lfo", s -> {
            String shape = s.checkString(2);
            if (!List.of("sine", "triangle", "saw", "square").contains(shape)) {
                throw s.error("an lfo is sine, triangle, saw or square, not '%s'", shape);
            }
            audio.lfo(s.checkString(1), shape, s.checkNumber(3), s.checkNumber(4), s.checkNumber(5));
            return 0;
        });
        function(state, "snapshot", s -> {
            audio.snapshot(volumes(s, 1), s.isNoneOrNil(2) ? 0 : s.checkNumber(2));
            return 0;
        });
        function(state, "clearSnapshot", s -> {
            audio.clearSnapshot(s.isNoneOrNil(1) ? 0 : s.checkNumber(1));
            return 0;
        });
        function(state, "sidechain", s -> {
            audio.sidechain(s.checkString(1), s.checkString(2), s.checkNumber(3));
            return 0;
        });
        function(state, "reverb", s -> {
            audio.reverb(s.checkNumber(1), s.checkNumber(2));
            return 0;
        });
        function(state, "hrtf", s -> {
            audio.hrtf(s.toBoolean(1));
            return 0;
        });
        function(state, "occlusion", s -> {
            audio.occlusion(s.toBoolean(1));
            return 0;
        });
        function(state, "music", s -> {
            music(s, audio);
            return 1;
        });
        function(state, "voiceChat", s -> {
            voiceChat(s, audio);
            return 0;
        });
        function(state, "voiceCount", s -> {
            s.pushNumber(audio.voiceCount());
            return 1;
        });
        Signals.push(state, beat);
        state.rawSetField(-2, "beat");
        Signals.push(state, bar);
        state.rawSetField(-2, "bar");
        state.setGlobal("audio");
    }

    private static Map<String, ToIntFunction<LuaState>> voiceMethods() {
        Map<String, ToIntFunction<LuaState>> m = new LinkedHashMap<>();
        m.put("stop", s -> {
            voice(s).stop();
            return 0;
        });
        m.put("setVolume", s -> {
            voice(s).volume(s.checkNumber(2));
            return 0;
        });
        m.put("setPitch", s -> {
            voice(s).pitch(s.checkNumber(2));
            return 0;
        });
        m.put("fade", s -> {
            voice(s).fade(s.checkNumber(2), s.checkNumber(3));
            return 0;
        });
        m.put("fadeOut", s -> {
            voice(s).fadeOut(s.checkNumber(2));
            return 0;
        });
        m.put("setPosition", s -> {
            voice(s).position(Values.vec3(s, 2));
            return 0;
        });
        m.put("isPlaying", s -> {
            s.pushBoolean(voice(s).playing());
            return 1;
        });
        return m;
    }

    private static Map<String, ToIntFunction<LuaState>> musicMethods() {
        Map<String, ToIntFunction<LuaState>> m = new LinkedHashMap<>();
        m.put("setState", s -> {
            music(s).state(s.checkString(2), s.isNoneOrNil(3) ? 0 : s.checkNumber(3));
            return 0;
        });
        m.put("transitionTo", s -> {
            music(s).transition(s.checkString(2), s.isNoneOrNil(3) ? 0 : s.checkNumber(3), quantize(s, 4));
            return 0;
        });
        m.put("setLayer", s -> {
            // counted from one, like everything else a place indexes
            music(s).layer((int) s.checkNumber(2) - 1, s.checkNumber(3));
            return 0;
        });
        m.put("blend", s -> {
            music(s).blend(s.checkNumber(2));
            return 0;
        });
        m.put("stop", s -> {
            music(s).stop(s.isNoneOrNil(2) ? 0 : s.checkNumber(2));
            return 0;
        });
        return m;
    }

    private static void methods(LuaState state, int tag, String registry, String what,
                                Map<String, ToIntFunction<LuaState>> methods) {
        state.newTable();
        for (Map.Entry<String, ToIntFunction<LuaState>> entry : methods.entrySet()) {
            ToIntFunction<LuaState> body = entry.getValue();
            state.pushFunction(LuaFunc.wrap(body::applyAsInt, what + ":" + entry.getKey()));
            state.rawSetField(-2, entry.getKey());
        }
        state.rawSetField(LuaState.REGISTRY_INDEX, registry);

        state.newTable();
        state.pushFunction(LuaFunc.wrap(s -> {
            String key = s.checkString(2);
            s.rawGetField(LuaState.REGISTRY_INDEX, registry);
            if (s.rawGetField(-1, key) != LuaType.NIL) {
                s.remove(-2);
                return 1;
            }
            throw s.error("%s has no member '%s'", what, key);
        }, what + ".__index"));
        state.rawSetField(-2, "__index");
        state.setUserDataMetaTable(tag);
    }

    private static void function(LuaState state, String name, ToIntFunction<LuaState> body) {
        state.pushFunction(LuaFunc.wrap(body::applyAsInt, "audio." + name));
        state.rawSetField(-2, name);
    }

    private static void push(LuaState state, AudioRef.Voice voice) {
        if (voice == null) state.pushNil(); else state.newUserDataTaggedWithMetatable(voice, VOICE);
    }

    private static AudioRef.Voice voice(LuaState state) {
        Object value = state.toUserDataTagged(1, VOICE);
        if (value == null) throw state.error("expected a voice");
        return (AudioRef.Voice) value;
    }

    private static AudioRef.Music music(LuaState state) {
        Object value = state.toUserDataTagged(1, MUSIC);
        if (value == null) throw state.error("expected music");
        return (AudioRef.Music) value;
    }

    private static String quantize(LuaState state, int at) {
        if (state.isNoneOrNil(at)) return "immediate";
        String value = state.checkString(at);
        if (!List.of("immediate", "beat", "bar").contains(value)) {
            throw state.error("quantize is immediate, beat or bar, not '%s'", value);
        }
        return value;
    }

    private static AudioRef.Options options(LuaState state, int at) {
        AudioRef.Options d = AudioRef.Options.DEFAULT;
        if (state.isNoneOrNil(at)) return d;
        if (state.type(at) != LuaType.TABLE) throw state.error("the options are a table");
        return new AudioRef.Options(
                number(state, at, "volume", d.volume()),
                number(state, at, "pitch", d.pitch()),
                bool(state, at, "looped", d.looped()),
                text(state, at, "bus", d.bus()),
                (int) number(state, at, "priority", d.priority()),
                position(state, at),
                number(state, at, "minDistance", d.minDistance()),
                number(state, at, "maxDistance", d.maxDistance()),
                number(state, at, "rollOff", d.rollOff()),
                number(state, at, "fadeIn", d.fadeIn()),
                bool(state, at, "stream", d.stream()));
    }

    private static Vec3 position(LuaState state, int at) {
        state.getField(at, "at");
        Vec3 value = state.isNoneOrNil(-1) ? null : Values.vec3(state, state.top());
        state.pop(1);
        return value;
    }

    private static double number(LuaState state, int at, String key, double fallback) {
        state.getField(at, key);
        double value = state.isNoneOrNil(-1) ? fallback : state.checkNumber(state.top());
        state.pop(1);
        return value;
    }

    private static boolean bool(LuaState state, int at, String key, boolean fallback) {
        state.getField(at, key);
        boolean value = state.isNoneOrNil(-1) ? fallback : state.toBoolean(-1);
        state.pop(1);
        return value;
    }

    private static String text(LuaState state, int at, String key, String fallback) {
        state.getField(at, key);
        String value = state.isNoneOrNil(-1) ? fallback : state.checkString(state.top());
        state.pop(1);
        return value;
    }

    // a number, or {min, max}
    private static double[] range(LuaState state, int at, String key) {
        state.getField(at, key);
        double[] value;
        if (state.isNoneOrNil(-1)) {
            value = new double[] {1, 1};
        } else if (state.type(-1) == LuaType.TABLE) {
            int table = state.top();
            value = new double[] {element(state, table, 1), element(state, table, 2)};
        } else {
            double one = state.checkNumber(state.top());
            value = new double[] {one, one};
        }
        state.pop(1);
        return value;
    }

    private static double element(LuaState state, int table, int index) {
        state.rawGetI(table, index);
        double value = state.checkNumber(state.top());
        state.pop(1);
        return value;
    }

    private static List<String> strings(LuaState state, int table) {
        List<String> out = new ArrayList<>();
        int length = state.len(table);
        for (int n = 1; n <= length; n++) {
            state.rawGetI(table, n);
            out.add(state.checkString(state.top()));
            state.pop(1);
        }
        return out;
    }

    private static void defineEvent(LuaState state, AudioRef audio) {
        String name = state.checkString(1);
        if (state.type(2) != LuaType.TABLE) throw state.error("defineEvent wants a name and a table");
        state.getField(2, "sounds");
        if (state.type(-1) != LuaType.TABLE) throw state.error("an event lists its sounds in sounds = { ... }");
        List<String> sounds = strings(state, state.top());
        state.pop(1);
        if (sounds.isEmpty()) throw state.error("an event needs at least one sound");
        double[] volume = range(state, 2, "volume");
        double[] pitch = range(state, 2, "pitch");
        audio.defineEvent(name, sounds, text(state, 2, "bus", "sfx"), volume[0], volume[1], pitch[0], pitch[1],
                bool(state, 2, "looped", false));
    }

    // {{0, 0}, {1, 1}}: pairs of parameter value and bus volume
    private static double[][] curve(LuaState state, int at) {
        if (state.type(at) != LuaType.TABLE) throw state.error("a curve is a list of {input, output} pairs");
        int length = state.len(at);
        double[][] points = new double[length][];
        for (int n = 1; n <= length; n++) {
            state.rawGetI(at, n);
            int pair = state.top();
            points[n - 1] = new double[] {element(state, pair, 1), element(state, pair, 2)};
            state.pop(1);
        }
        return points;
    }

    // {sfx = 0.2, music = 1}
    private static Map<String, Double> volumes(LuaState state, int at) {
        if (state.type(at) != LuaType.TABLE) throw state.error("a snapshot is a table of bus volumes");
        Map<String, Double> out = new LinkedHashMap<>();
        state.pushNil();
        while (state.next(at)) {
            if (state.type(-2) != LuaType.STRING) throw state.error("a snapshot is keyed by bus name");
            out.put(state.toString(-2), state.checkNumber(state.top()));
            state.pop(1);
        }
        return out;
    }

    private static void music(LuaState state, AudioRef audio) {
        if (state.type(1) != LuaType.TABLE) throw state.error("music wants a table of layers and states");
        state.getField(1, "layers");
        if (state.type(-1) != LuaType.TABLE) throw state.error("music lists its sounds in layers = { ... }");
        List<String> layers = strings(state, state.top());
        state.pop(1);
        Map<String, double[]> states = new LinkedHashMap<>();
        state.getField(1, "states");
        if (state.type(-1) == LuaType.TABLE) {
            int table = state.top();
            state.pushNil();
            while (state.next(table)) {
                String name = state.toString(-2);
                int volumes = state.top();
                double[] levels = new double[state.len(volumes)];
                for (int n = 0; n < levels.length; n++) levels[n] = element(state, volumes, n + 1);
                states.put(name, levels);
                state.pop(1);
            }
        }
        state.pop(1);
        AudioRef.Music music = audio.music(text(state, 1, "bus", "music"), layers, states);
        state.newUserDataTaggedWithMetatable(music, MUSIC);
    }

    // {enabled = true, strength = 1, filters = {{kind = "lowpass", frequency = 3000, q = 0.7}}}
    private static void voiceChat(LuaState state, AudioRef audio) {
        if (state.type(1) != LuaType.TABLE) throw state.error("voiceChat wants a table");
        List<AudioRef.Filter> filters = new ArrayList<>();
        state.getField(1, "filters");
        if (state.type(-1) == LuaType.TABLE) {
            int table = state.top();
            int length = state.len(table);
            for (int n = 1; n <= length; n++) {
                state.rawGetI(table, n);
                int one = state.top();
                String kind = text(state, one, "kind", "lowpass");
                if (!List.of("lowpass", "highpass", "bandpass", "peaking").contains(kind)) {
                    throw state.error("a filter is lowpass, highpass, bandpass or peaking, not '%s'", kind);
                }
                filters.add(new AudioRef.Filter(kind, number(state, one, "frequency", 1000),
                        number(state, one, "q", 0.707), number(state, one, "gain", 0)));
                state.pop(1);
            }
        }
        state.pop(1);
        audio.voiceChat(bool(state, 1, "enabled", true), number(state, 1, "strength", 1), filters);
    }
}
