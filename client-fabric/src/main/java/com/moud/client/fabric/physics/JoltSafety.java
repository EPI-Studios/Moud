package com.moud.client.fabric.physics;

import com.github.stephengold.joltjni.Jolt;
import com.moud.client.fabric.util.ClientDebugLog;

final class JoltSafety {
    private static final String LOG = "physics-jolt";

    private static volatile String lastBreadcrumb = "<none>";

    private JoltSafety() {}

    static {
        Thread shutdown = new Thread(() -> {
            String b = lastBreadcrumb;
            if (b != null && !"<none>".equals(b)) {
                System.err.println("[moud physics] last jolt op before shutdown: " + b);
            }
        }, "moud-jolt-breadcrumb-hook");
        shutdown.setDaemon(true);
        try {
            Runtime.getRuntime().addShutdownHook(shutdown);
        } catch (Throwable ignored) {}
    }

    static void breadcrumb(String op, Object... args) {
        StringBuilder sb = new StringBuilder(op.length() + 32).append(op).append('(');
        for (int i = 0; i < args.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(args[i]);
        }
        sb.append(')');
        lastBreadcrumb = sb.toString();
    }

    static boolean checkExtents(String op, float... values) {
        for (int i = 0; i < values.length; i++) {
            float v = values[i];
            if (!Float.isFinite(v)) {
                warnBad(op, "non-finite extent[" + i + "]=" + v);
                return false;
            }
            if (v <= 1.0e-6f) {
                warnBad(op, "non-positive extent[" + i + "]=" + v);
                return false;
            }
        }
        return true;
    }

    static boolean checkVector(String op, double... values) {
        for (int i = 0; i < values.length; i++) {
            double v = values[i];
            if (!Double.isFinite(v)) {
                warnBad(op, "non-finite value[" + i + "]=" + v);
                return false;
            }
        }
        return true;
    }

    static boolean checkCompoundChildCount(String op, int n) {
        if (n < 2) {
            warnBad(op, "compound child count=" + n + " (Jolt requires >=2, use a direct shape instead)");
            return false;
        }
        return true;
    }

    private static void warnBad(String op, String why) {
        String msg = "Rejected " + op + ": " + why;
        ClientDebugLog.warn(LOG, msg);
        System.err.println("[moud physics] REJECT " + op + ": " + why);
        System.err.flush();
    }

    static void installJavaTrace() {
        try {
            Jolt.installJavaTraceCallback(System.err);
        } catch (Throwable t) {
            ClientDebugLog.warn(LOG, "Unable to install Jolt Java trace callback: " + t.getMessage());
        }
    }
}
