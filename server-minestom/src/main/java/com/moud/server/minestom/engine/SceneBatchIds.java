package com.moud.server.minestom.engine;

public final class SceneBatchIds {
    public static final long RUNTIME_FLAG = 1L << 62;

    private SceneBatchIds() {
    }

    public static boolean isRuntime(long batchId) {
        return (batchId & RUNTIME_FLAG) != 0L;
    }

    public static long markRuntime(long batchId) {
        return batchId | RUNTIME_FLAG;
    }

    public static long clearRuntime(long batchId) {
        return batchId & ~RUNTIME_FLAG;
    }
}

