package com.meekdev.moud.script.bind;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.Character;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.Zone;
import com.meekdev.moud.core.instance.Zones;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;
import net.hollowcube.luau.LuaFunc;
import net.hollowcube.luau.LuaState;

// zone:players, zone:occupants, zone:contains, and game.zones:at
public final class ZoneMethods {

    private ZoneMethods() {}

    public static void install(LuaState state, Instance world) {
        Map<String, ToIntFunction<LuaState>> methods = new LinkedHashMap<>();
        methods.put("players", s -> {
            List<Instance> out = new ArrayList<>();
            for (Instance occupant : zone(s).occupants()) {
                if (occupant instanceof Character body && body.worn()) out.add(occupant);
            }
            Plain.push(s, out);
            return 1;
        });
        methods.put("occupants", s -> {
            Plain.push(s, new ArrayList<>(zone(s).occupants()));
            return 1;
        });
        methods.put("contains", s -> {
            s.pushBoolean(Zones.contains(zone(s), Values.vec3(s, 2)));
            return 1;
        });
        Proxies.classMethods(state, Classes.ZONE, methods);

        state.getGlobal("game");
        state.newTable();
        state.pushFunction(LuaFunc.wrap(s -> {
            Plain.push(s, new ArrayList<Instance>(Zones.at(world.tree(), Values.vec3(s, 2))));
            return 1;
        }, "zones:at"));
        state.rawSetField(-2, "at");
        state.rawSetField(-2, "zones");
        state.pop(1);
    }

    private static Zone zone(LuaState s) {
        if (!(s.toUserDataTagged(1, Proxies.TAG) instanceof Zone zone)) throw s.error("expected a zone");
        return zone;
    }
}
