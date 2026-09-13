package com.meekdev.moud.script.bind.world;

import com.meekdev.moud.core.character.Character;
import com.meekdev.moud.core.query.Queries;
import com.meekdev.moud.script.api.HistoryRef;
import net.hollowcube.luau.LuaFunc;
import net.hollowcube.luau.LuaState;
import com.meekdev.moud.script.bind.Proxies;

public final class History {

    private History() {}

    public static void install(LuaState state, HistoryRef history) {
        state.getGlobal("game");
        state.newTable();
        state.pushFunction(LuaFunc.wrap(s -> {
            s.pushNumber(history.rewind().now());
            return 1;
        }, "history:now"));
        state.rawSetField(-2, "now");
        state.pushFunction(LuaFunc.wrap(s -> {
            if (!(s.toUserDataTagged(2, Proxies.TAG) instanceof Character body)) {
                throw s.error("history:viewTime expects a body");
            }
            s.pushNumber(history.viewTime(body.owner));
            return 1;
        }, "history:viewTime"));
        state.rawSetField(-2, "viewTime");
        state.pushFunction(LuaFunc.wrap(s -> {
            double seconds = s.checkNumber(2);
            if (!s.isFunction(3)) throw s.error("history:rewind expects a time and a function");
            int base = 3;
            s.pushValue(3);
            Queries.withFrames(part -> history.rewind().at(part, seconds), () -> {
                s.call(0, -1);
                return null;
            });
            return s.top() - base;
        }, "history:rewind"));
        state.rawSetField(-2, "rewind");
        state.rawSetField(-2, "history");
        state.pop(1);
    }
}
