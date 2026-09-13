package com.meekdev.moud.mod.place;

import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.script.err.ScriptError;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

public final class Errors {

    private static final int KEEP = 20;
    private static final Deque<ScriptError> RECENT = new ArrayDeque<>();

    private Errors() {}

    public static synchronized void record(ScriptError error) {
        MoudMod.LOG.error("script error", error);
        if (RECENT.size() == KEEP) RECENT.removeFirst();
        RECENT.addLast(error);
    }

    public static synchronized List<ScriptError> recent() {
        return List.copyOf(RECENT);
    }

    public static synchronized void clear() {
        RECENT.clear();
    }
}
