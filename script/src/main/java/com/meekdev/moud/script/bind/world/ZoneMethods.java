package com.meekdev.moud.script.bind.world;

import com.meekdev.moud.core.character.Character;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.Transforms;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.zone.ProximityPrompt;
import com.meekdev.moud.core.zone.Zone;
import com.meekdev.moud.core.zone.Zones;
import com.meekdev.moud.script.bind.LuaTables;
import com.meekdev.moud.script.bind.Plain;
import com.meekdev.moud.script.bind.Proxies;
import com.meekdev.moud.script.bind.Values;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;
import net.hollowcube.luau.LuaState;

public final class ZoneMethods {

    private ZoneMethods() {}

    public static void install(LuaState state, Instance world) {
        Map<String, ToIntFunction<LuaState>> methods = new LinkedHashMap<>();
        methods.put("players", s -> {
            List<Instance> out = new ArrayList<>();
            for (Instance occupant : zone(s).occupants()) {
                if (occupant instanceof Character body && body.hasPlayer()) out.add(occupant);
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
        LuaTables.function(state, "zones", "at", s -> {
            Plain.push(s, new ArrayList<Instance>(Zones.at(world.tree(), Values.vec3(s, 2))));
            return 1;
        });
        state.rawSetField(-2, "zones");

        state.newTable();
        LuaTables.function(state, "proximity", "closestInteractable", s -> {
            if (!(s.toUserDataTagged(2, Proxies.TAG) instanceof Instance body)) throw s.error("expects a body");
            Vector3 at = Transforms.world(body).position();
            ProximityPrompt best = null;
            double bestDistance = Double.MAX_VALUE;
            for (ProximityPrompt prompt : world.tree().ofClass(Classes.PROXIMITY_PROMPT)) {
                if (!prompt.enabled || prompt.parent() == null) continue;
                double d = Transforms.world(prompt.parent()).position().add(prompt.offset).distance(at);
                if (d <= prompt.maxActivationDistance && d < bestDistance) {
                    best = prompt;
                    bestDistance = d;
                }
            }
            if (best == null) {
                s.pushNil();
                return 1;
            }
            Proxies.push(s, best);
            s.pushNumber(bestDistance);
            return 2;
        });
        state.rawSetField(-2, "proximity");
        state.pop(1);
    }

    private static Zone zone(LuaState s) {
        if (!(s.toUserDataTagged(1, Proxies.TAG) instanceof Zone zone)) throw s.error("expected a zone");
        return zone;
    }
}
