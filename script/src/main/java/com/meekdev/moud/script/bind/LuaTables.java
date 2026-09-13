package com.meekdev.moud.script.bind;

import java.util.function.ToIntFunction;
import net.hollowcube.luau.LuaFunc;
import net.hollowcube.luau.LuaState;

public final class LuaTables {

    private LuaTables() {}

    public static void function(LuaState state, String owner, String name, ToIntFunction<LuaState> body) {
        state.pushFunction(LuaFunc.wrap(body::applyAsInt, owner + "." + name));
        state.rawSetField(-2, name);
    }

    public static void global(LuaState state, String name, ToIntFunction<LuaState> body) {
        state.pushFunction(LuaFunc.wrap(body::applyAsInt, name));
        state.setGlobal(name);
    }
}
