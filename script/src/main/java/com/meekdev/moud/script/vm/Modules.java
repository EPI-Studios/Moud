package com.meekdev.moud.script.vm;

import com.meekdev.moud.core.asset.Res;
import com.meekdev.moud.script.api.ModuleSource;
import net.hollowcube.luau.LuaError;
import net.hollowcube.luau.LuaFunc;
import net.hollowcube.luau.LuaState;
import net.hollowcube.luau.LuaType;
import net.hollowcube.luau.compiler.LuauCompileException;
import net.hollowcube.luau.compiler.LuauCompiler;

// require(path): runs a module once per vm and hands every caller the same value
final class Modules {

    private static final String CACHE = "moud.modules";
    private static final String LOADING = "moud.modules.loading";

    private Modules() {}

    static void install(LuaState state, ModuleSource source) {
        state.newTable();
        state.rawSetField(LuaState.REGISTRY_INDEX, CACHE);
        state.newTable();
        state.rawSetField(LuaState.REGISTRY_INDEX, LOADING);
        state.pushFunction(LuaFunc.wrap(s -> require(s, source), "require"));
        state.setGlobal("require");
    }

    private static int require(LuaState state, ModuleSource source) {
        String path;
        try {
            path = Res.parse(state.checkString(1));
        } catch (IllegalArgumentException wrong) {
            throw state.error("%s", wrong.getMessage());
        }

        state.rawGetField(LuaState.REGISTRY_INDEX, CACHE);
        int cache = state.top();
        if (state.rawGetField(cache, path) != LuaType.NIL) return 1;
        state.pop(1);

        if (loading(state, path)) {
            throw state.error("res://%s is required again while it is still loading. two modules that"
                    + " require each other have to move what they share into a third", path);
        }

        String code;
        try {
            code = source.read(path);
        } catch (IllegalArgumentException wrong) {
            throw state.error("%s", wrong.getMessage());
        }
        if (code == null) throw state.error("there is no res://%s", path);

        byte[] bytecode;
        try {
            bytecode = LuauCompiler.DEFAULT.compile(code);
        } catch (LuauCompileException e) {
            throw state.error("res://%s does not compile: %s", path, e.getMessage());
        }

        mark(state, path, true);
        int before = state.top();
        try {
            state.load(path, bytecode);
            state.call(0, -1);
        } catch (LuaError failed) {
            String why = failed.getMessage();
            if (why != null && why.contains("attempt to yield across")) {
                throw state.error("res://%s waits while it loads, and a module has to finish loading"
                        + " before require can hand it back. start the waiting from a function it"
                        + " returns, or from task.spawn", path);
            }
            // the error arrives without the file it came from, and a place with ten modules needs it
            throw state.error("res://%s: %s", path, why);
        } finally {
            // a module that failed is not still loading, so requiring it again fails the same way
            // rather than claiming a cycle
            mark(state, path, false);
        }

        int returned = state.top() - before;
        if (returned != 1) {
            throw state.error("res://%s has to return exactly one value and returned %d", path, returned);
        }
        state.pushValue(-1);
        state.rawSetField(cache, path);
        return 1;
    }

    private static boolean loading(LuaState state, String path) {
        state.rawGetField(LuaState.REGISTRY_INDEX, LOADING);
        boolean yes = state.rawGetField(-1, path) != LuaType.NIL;
        state.pop(2);
        return yes;
    }

    private static void mark(LuaState state, String path, boolean on) {
        state.rawGetField(LuaState.REGISTRY_INDEX, LOADING);
        if (on) state.pushBoolean(true); else state.pushNil();
        state.rawSetField(-2, path);
        state.pop(1);
    }
}
