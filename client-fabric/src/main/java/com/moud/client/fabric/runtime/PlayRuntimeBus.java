package com.moud.client.fabric.runtime;

public final class PlayRuntimeBus {
    private static volatile PlayRuntimeClient runtime;

    private PlayRuntimeBus() {
    }

    public static PlayRuntimeClient get() {
        return runtime;
    }

    public static void set(PlayRuntimeClient runtime) {
        PlayRuntimeBus.runtime = runtime;
    }
}

