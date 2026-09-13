package com.meekdev.moud.script.bind.audio;

import com.meekdev.moud.core.audio.Sound;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.script.bind.Proxies;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.ToIntFunction;
import net.hollowcube.luau.LuaState;

public final class SoundMethods {

    private SoundMethods() {}

    public static void install(LuaState state) {
        Map<String, ToIntFunction<LuaState>> methods = new LinkedHashMap<>();
        methods.put("play", s -> {
            Sound sound = sound(s);
            Instances.setBool(sound, Classes.SOUND.property("playing"), true);
            Instances.setNum(sound, Classes.SOUND.property("plays"), sound.plays + 1);
            return 0;
        });
        methods.put("stop", s -> {
            Instances.setBool(sound(s), Classes.SOUND.property("playing"), false);
            return 0;
        });
        Proxies.classMethods(state, Classes.SOUND, methods);
    }

    private static Sound sound(LuaState state) {
        if (!(state.toUserDataTagged(1, Proxies.TAG) instanceof Sound sound)) {
            throw state.error("expected a sound");
        }
        return sound;
    }
}
