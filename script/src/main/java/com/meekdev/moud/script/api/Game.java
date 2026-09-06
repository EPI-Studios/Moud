package com.meekdev.moud.script.api;

import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.script.bind.Proxies;
import com.meekdev.moud.script.bind.Signals;
import net.hollowcube.luau.LuaState;

public final class Game {

    private final Signals.Handlers stepped = new Signals.Handlers();
    private final Signals.Handlers renderStepped = new Signals.Handlers();
    private final Signals.Handlers reloaded = new Signals.Handlers();

    public Signals.Handlers stepped() {
        return stepped;
    }

    public Signals.Handlers renderStepped() {
        return renderStepped;
    }

    public Signals.Handlers reloaded() {
        return reloaded;
    }

    public void install(LuaState state, Instance world) {
        state.newTable();
        Proxies.push(state, world);
        state.rawSetField(-2, "world");
        Signals.push(state, stepped);
        state.rawSetField(-2, "stepped");
        Signals.push(state, renderStepped);
        state.rawSetField(-2, "renderStepped");
        Signals.push(state, reloaded);
        state.rawSetField(-2, "reloaded");
        // a plain table the place owns, carried across a reload as data
        state.newTable();
        state.rawSetField(-2, "persist");
        state.setGlobal("game");
    }
}
