package com.meekdev.moud.script.host;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Map;

public final class Ownership {

    private Object current;
    private final Map<Object, Set<Runnable>> undo = new IdentityHashMap<>();

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

    public Runnable onRelease(Object owner, Runnable action) {
        if (owner == null) return () -> { };
        undo.computeIfAbsent(owner, key -> new LinkedHashSet<>()).add(action);
        return () -> {
            Set<Runnable> actions = undo.get(owner);
            if (actions != null) actions.remove(action);
        };
    }

    public void release(Object owner) {
        Set<Runnable> actions = undo.remove(owner);
        if (actions != null) for (Runnable action : actions) action.run();
    }

    public void releaseAll() {
        for (Object owner : new ArrayList<>(undo.keySet())) release(owner);
    }
}
