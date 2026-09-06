package com.meekdev.moud.script.api;

import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.script.bind.Proxies;
import com.meekdev.moud.script.bind.Signals;
import net.hollowcube.luau.LuaState;

public final class Game {

    private final Signals.Handlers stepped = new Signals.Handlers();

    public Signals.Handlers stepped() {
        return stepped;
    }

    public void install(LuaState state, Instance world) {
        state.newTable();
        Proxies.push(state, world);
        state.rawSetField(-2, "world");
        Signals.push(state, stepped);
        state.rawSetField(-2, "stepped");
        state.setGlobal("game");
    }
}
