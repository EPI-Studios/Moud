package com.moud.client.fabric.editor.net;

import com.moud.net.protocol.SceneOp;
import java.util.ArrayList;
import java.util.List;

public final class HudEditBus {
    private static final Object LOCK = new Object();
    private static final List<SceneOp> PENDING = new ArrayList<>();

    private HudEditBus() {
    }

    public static void push(SceneOp op) {
        if (op == null) return;
        synchronized (LOCK) {
            PENDING.add(op);
        }
    }

    public static void pushAll(List<SceneOp> ops) {
        if (ops == null || ops.isEmpty()) return;
        synchronized (LOCK) {
            PENDING.addAll(ops);
        }
    }

    public static List<SceneOp> drain() {
        synchronized (LOCK) {
            if (PENDING.isEmpty()) return List.of();
            List<SceneOp> out = List.copyOf(PENDING);
            PENDING.clear();
            return out;
        }
    }
}
