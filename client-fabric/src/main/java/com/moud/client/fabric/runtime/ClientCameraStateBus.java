package com.moud.client.fabric.runtime;

/**
 * Static accessor giving {@code MouseMixin} (and other mixins) access to
 * {@link ClientCameraState} without circular dependencies.
 *
 * <p>Same pattern as {@link PlayRuntimeBus}.
 */
public final class ClientCameraStateBus {
    private static volatile ClientCameraState state;

    private ClientCameraStateBus() {
    }

    public static ClientCameraState get() {
        return state;
    }

    public static void set(ClientCameraState state) {
        ClientCameraStateBus.state = state;
    }
}
