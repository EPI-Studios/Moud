package com.moud.client.fabric;

public final class MoudRuntimeClientBridge {

    private static final FabricClientEntrypoint ENTRYPOINT = new FabricClientEntrypoint();

    private MoudRuntimeClientBridge() {
    }

    public static void initializeClient() {
        ENTRYPOINT.onInitializeClient();
    }
}
