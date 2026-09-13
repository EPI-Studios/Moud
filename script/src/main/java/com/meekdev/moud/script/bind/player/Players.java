package com.meekdev.moud.script.bind.player;

import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.script.api.PlayerRef;
import com.meekdev.moud.script.bind.LuaTables;
import com.meekdev.moud.script.bind.Proxies;
import com.meekdev.moud.script.bind.Values;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.ToIntFunction;
import net.hollowcube.luau.LuaState;
import net.hollowcube.luau.LuaType;

public final class Players {

    static final int TAG = 8;

    private static final String METHODS = "moud.player.methods";

    private static final Set<String> NAMES = new LinkedHashSet<>();

    private Players() {}

    public static void install(LuaState state) {
        state.newTable();
        LuaTables.function(state, "Player", "__index", Players::index);
        LuaTables.function(state, "Player", "__tostring", Players::text);
        LuaTables.function(state, "Player", "__eq", Players::same);
        state.setUserDataMetaTable(TAG);

        state.newTable();
        method(state, "spawn", Players::spawn);
        method(state, "ping", s -> {
            s.pushNumber(self(s).ping());
            return 1;
        });
        method(state, "viewTime", s -> {
            s.pushNumber(self(s).viewTime());
            return 1;
        });
        state.rawSetField(LuaState.REGISTRY_INDEX, METHODS);
    }

    private static void method(LuaState state, String name, ToIntFunction<LuaState> body) {
        NAMES.add(name);
        LuaTables.function(state, "Player", name, body);
    }

    public static Set<String> methodNames() {
        return Collections.unmodifiableSet(NAMES);
    }

    public static void push(LuaState state, PlayerRef player) {
        state.newUserDataTaggedWithMetatable(player, TAG);
    }

    private static PlayerRef self(LuaState state) {
        PlayerRef player = (PlayerRef) state.toUserDataTagged(1, TAG);
        if (player == null) throw state.error("not a player");
        return player;
    }

    private static int index(LuaState state) {
        PlayerRef player = self(state);
        String key = state.checkString(2);
        switch (key) {
            case "name" -> { state.pushString(player.name()); return 1; }
            case "character" -> {
                Instance character = player.character();
                if (character == null) state.pushNil(); else Proxies.push(state, character);
                return 1;
            }
            default -> { }
        }
        state.rawGetField(LuaState.REGISTRY_INDEX, METHODS);
        if (state.rawGetField(-1, key) != LuaType.NIL) {
            state.remove(-2);
            return 1;
        }
        throw state.error("player has no member '%s'", key);
    }

    private static int spawn(LuaState state) {
        self(state).spawn(Values.vec3(state, 2));
        return 0;
    }

    private static int same(LuaState state) {
        state.pushBoolean(state.toUserDataTagged(1, TAG) == state.toUserDataTagged(2, TAG));
        return 1;
    }

    private static int text(LuaState state) {
        state.pushString(self(state).name());
        return 1;
    }
}
