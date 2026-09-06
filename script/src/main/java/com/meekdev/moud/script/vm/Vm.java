package com.meekdev.moud.script.vm;

import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.script.api.Game;
import com.meekdev.moud.script.bind.Proxies;
import com.meekdev.moud.script.bind.Values;
import com.meekdev.moud.script.err.ScriptError;
import net.hollowcube.luau.BuilinLibrary;
import net.hollowcube.luau.LuaState;
import net.hollowcube.luau.compiler.LuauCompileException;
import net.hollowcube.luau.compiler.LuauCompiler;

public final class Vm implements AutoCloseable {

    // no io, no os, no package: a place reaches the world through our api or not at all
    private static final BuilinLibrary[] LIBRARIES = {
        BuilinLibrary.BASE,
        BuilinLibrary.COROUTINE,
        BuilinLibrary.TABLE,
        BuilinLibrary.STRING,
        BuilinLibrary.MATH,
        BuilinLibrary.BIT32,
        BuilinLibrary.BUFFER,
        BuilinLibrary.UTF8,
        BuilinLibrary.VECTOR,
    };

    private final LuaState state;

    public Vm() {
        state = LuaState.newState();
        state.openLibs(LIBRARIES);
    }

    public void bind(Instance world) {
        Values.install(state);
        Proxies.install(state);
        Game.install(state, world);
    }

    public void run(String chunkName, String source) {
        byte[] bytecode;
        try {
            bytecode = LuauCompiler.DEFAULT.compile(source);
        } catch (LuauCompileException e) {
            throw new ScriptError(chunkName, e.getMessage(), e);
        }
        state.load(chunkName, bytecode);
        state.call(0, 0);
    }

    public double number(String global) {
        state.getGlobal(global);
        double value = state.toNumber(-1);
        state.pop(1);
        return value;
    }

    public boolean bool(String global) {
        state.getGlobal(global);
        boolean value = state.toBoolean(-1);
        state.pop(1);
        return value;
    }

    public String text(String global) {
        state.getGlobal(global);
        String value = state.toString(-1);
        state.pop(1);
        return value;
    }

    @Override
    public void close() {
        state.close();
    }
}
