package com.meekdev.moud.script.host;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class Ownership {

    private Object current;
    private final Map<Object, List<Runnable>> undo = new IdentityHashMap<>();

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
        if (owner != null) undo.computeIfAbsent(owner, key -> new ArrayList<>()).add(action);
    }

    public void release(Object owner) {
        List<Runnable> actions = undo.remove(owner);
        if (actions != null) for (Runnable action : actions) action.run();
    }

    public void releaseAll() {
        for (Object owner : new ArrayList<>(undo.keySet())) release(owner);
    }
}
