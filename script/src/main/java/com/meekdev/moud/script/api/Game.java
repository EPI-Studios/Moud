package com.meekdev.moud.script.api;

import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.script.bind.Proxies;
import net.hollowcube.luau.LuaState;

public final class Game {

    private Game() {}

    public static void install(LuaState state, Instance world) {
        state.newTable();
        Proxies.push(state, world);
        state.rawSetField(-2, "world");
        state.setGlobal("game");
    }
}
