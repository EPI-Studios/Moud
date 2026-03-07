package com.moud.client.fabric.editor.overlay;

import java.util.concurrent.atomic.AtomicReference;

public final class EditorOverlayBus {
    private static final AtomicReference<EditorContext> CONTEXT = new AtomicReference<>();

    private EditorOverlayBus() {
    }

    public static void set(EditorContext ctx) {
        CONTEXT.set(ctx);
    }

    public static EditorContext get() {
        return CONTEXT.get();
    }

    public static boolean isActive() {
        EditorContext ctx = CONTEXT.get();
        return ctx != null && ctx.isActive();
    }
}
