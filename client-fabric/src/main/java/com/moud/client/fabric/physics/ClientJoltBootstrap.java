package com.moud.client.fabric.physics;

import com.github.stephengold.joltjni.Jolt;
import com.moud.client.fabric.util.ClientDebugLog;

public final class ClientJoltBootstrap {
    private static final Object LOCK = new Object();
    private static volatile boolean initialized;
    private static volatile Throwable initFailure;

    private ClientJoltBootstrap() {}

    public static boolean isAvailable() {
        ensureInitializedQuietly();
        return initFailure == null;
    }

    private static void ensureInitializedQuietly() {
        if (initialized) return;
        synchronized (LOCK) {
            if (initialized) return;
            try {
                if (!ClientJoltNativeLoader.loadOnce()) {
                    throw new UnsatisfiedLinkError("joltjni native library not found");
                }
                Jolt.registerDefaultAllocator();
                if (!Jolt.newFactory()) {
                    throw new IllegalStateException("Jolt.newFactory() failed");
                }
                Jolt.registerTypes();
                Jolt.installDefaultAssertCallback();
                JoltSafety.installJavaTrace();
                initFailure = null;
            } catch (Throwable t) {
                initFailure = t;
                ClientDebugLog.warn("physics", "Client Jolt disabled: " + rootMessage(t));
            } finally {
                initialized = true;
            }
        }
    }

    private static String rootMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null) cur = cur.getCause();
        String msg = cur.getMessage();
        return msg == null || msg.isBlank() ? cur.toString() : msg;
    }
}
