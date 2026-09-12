package com.meekdev.moud.script.bind;

import com.meekdev.moud.script.api.InputRef;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.ToIntFunction;
import net.hollowcube.luau.LuaFunc;
import net.hollowcube.luau.LuaState;
import net.hollowcube.luau.LuaType;

public final class Inputs {

    static final int TAG = 9;

    private static final String METHODS = "moud.input.methods";

    private static final Set<String> NAMES = new LinkedHashSet<>();

    private Inputs() {}

    public static void install(LuaState state) {
        state.newTable();
        state.pushFunction(LuaFunc.wrap(Inputs::index, "input.__index"));
        state.rawSetField(-2, "__index");
        state.pushFunction(LuaFunc.wrap(Inputs::newIndex, "input.__newindex"));
        state.rawSetField(-2, "__newindex");
        state.setUserDataMetaTable(TAG);

        state.newTable();
        method(state, "down", Inputs::down);
        method(state, "lockMouse", Inputs::lockMouse);
        method(state, "releaseMouse", Inputs::releaseMouse);
        state.rawSetField(LuaState.REGISTRY_INDEX, METHODS);
    }

    private static void method(LuaState state, String name, ToIntFunction<LuaState> body) {
        NAMES.add(name);
        state.pushFunction(LuaFunc.wrap(body, "input:" + name));
        state.rawSetField(-2, name);
    }

    public static Set<String> methodNames() {
        return Collections.unmodifiableSet(NAMES);
    }

    public static void push(LuaState state, InputRef input) {
        state.newUserDataTaggedWithMetatable(input, TAG);
    }

    private static InputRef self(LuaState state) {
        InputRef input = (InputRef) state.toUserDataTagged(1, TAG);
        if (input == null) throw state.error("not input");
        return input;
    }

    private static int index(LuaState state) {
        InputRef input = self(state);
        String key = state.checkString(2);
        switch (key) {
            case "mouseX" -> { state.pushNumber(input.mouseX()); return 1; }
            case "mouseY" -> { state.pushNumber(input.mouseY()); return 1; }
            case "mouseDeltaX" -> { state.pushNumber(input.mouseDeltaX()); return 1; }
            case "mouseDeltaY" -> { state.pushNumber(input.mouseDeltaY()); return 1; }
            case "screenWidth" -> { state.pushNumber(input.screenWidth()); return 1; }
            case "screenHeight" -> { state.pushNumber(input.screenHeight()); return 1; }
            case "mouseLocked" -> { state.pushBoolean(input.mouseLocked()); return 1; }
            case "sensitivity" -> { state.pushNumber(input.sensitivity()); return 1; }
            default -> { }
        }
        state.rawGetField(LuaState.REGISTRY_INDEX, METHODS);
        if (state.rawGetField(-1, key) != LuaType.NIL) {
            state.remove(-2);
            return 1;
        }
        throw state.error("input has no member '%s'", key);
    }

    private static int newIndex(LuaState state) {
        InputRef input = self(state);
        String key = state.checkString(2);
        if (!key.equals("sensitivity")) throw state.error("input has no property '%s'", key);
        input.sensitivity(state.checkNumber(3));
        return 0;
    }

    private static int lockMouse(LuaState state) {
        self(state).lockMouse(true);
        return 0;
    }

    private static int releaseMouse(LuaState state) {
        self(state).lockMouse(false);
        return 0;
    }

    private static int down(LuaState state) {
        InputRef input = self(state);
        String action = state.checkString(2);
        if (!input.known(action)) throw state.error("'%s' is not an action", action);
        state.pushBoolean(input.down(action));
        return 1;
    }
}
