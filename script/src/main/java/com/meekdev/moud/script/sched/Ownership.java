package com.meekdev.moud.script.sched;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.hollowcube.luau.LuaState;

public final class Ownership {

    private static final Map<LuaState, Ownership> VMS = new HashMap<>();

    private Object current;
    private final Map<Object, List<Runnable>> undo = new IdentityHashMap<>();

    public static Ownership of(LuaState state) {
        return VMS.computeIfAbsent(state.mainThread(), s -> new Ownership());
    }

    public static void forget(LuaState state) {
        VMS.remove(state.mainThread());
    }

    public Object current() {
        return current;
    }

    public Object enter(Object owner) {
        Object before = current;
        current = owner;
        return before;
    }

    public void leave(Object before) {
        current = before;
    }

    public void onRelease(Object owner, Runnable action) {
        if (owner != null) undo.computeIfAbsent(owner, o -> new ArrayList<>()).add(action);
    }

    public void release(Object owner) {
        List<Runnable> actions = undo.remove(owner);
        if (actions != null) for (Runnable action : actions) action.run();
    }
}
