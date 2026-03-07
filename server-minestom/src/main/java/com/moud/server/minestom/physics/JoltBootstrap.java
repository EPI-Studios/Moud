package com.moud.server.minestom.physics;

import com.github.stephengold.joltjni.Jolt;
import com.moud.server.minestom.util.DebugLog;

public final class JoltBootstrap {
    private static final Object LOCK = new Object();
    private static volatile boolean initialized;
    private static volatile Throwable initFailure;

    private JoltBootstrap() {
    }

    /**
     * @return whether Jolt is usable in this runtime (native binary loaded and initialized)
     */
    public static boolean isAvailable() {
        ensureInitializedQuietly();
        return initFailure == null;
    }

    public static void ensureInitialized() {
        ensureInitializedQuietly();
        if (initFailure != null) {
            throw new IllegalStateException("Jolt init failed (native library missing?)", initFailure);
        }
    }

    private static void ensureInitializedQuietly() {
        if (initialized) {
            return;
        }
        synchronized (LOCK) {
            if (initialized) {
                return;
            }
            try {
                if (!JoltNativeLoader.loadOnce()) {
                    throw new UnsatisfiedLinkError("joltjni native library not found");
                }
                // TODO: JoltPhysicsObject.startCleaner(); - class not found
                // JoltPhysicsObject.startCleaner();
                Jolt.registerDefaultAllocator();
                if (!Jolt.newFactory()) {
                    throw new IllegalStateException("Jolt.newFactory() failed");
                }
                Jolt.registerTypes();
                Jolt.installDefaultAssertCallback();
                Jolt.installDefaultTraceCallback();
                initFailure = null;
            } catch (Throwable t) {
                initFailure = t;
                DebugLog.warn("physics", "Jolt disabled: " + rootMessage(t));
                DebugLog.warn("physics", "To enable Jolt collisions, put the platform-native jolt-jni binaries on the runtime classpath,");
                DebugLog.warn("physics", "or set MOUD_JOLT_NATIVE (or -Dmoud.physics.native) to the native library path (e.g. libjoltjni.so).");
                DebugLog.warn("physics", "MOUD will also attempt to auto-download the native jar from Maven Central when needed.");
            } finally {
                initialized = true;
            }
        }
    }

    private static String rootMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null) {
            cur = cur.getCause();
        }
        String msg = cur.getMessage();
        if (msg == null || msg.isBlank()) {
            msg = cur.toString();
        }
        return msg;
    }
}
