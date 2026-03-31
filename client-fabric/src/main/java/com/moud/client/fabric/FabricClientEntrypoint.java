package com.moud.client.fabric;

import net.fabricmc.api.ClientModInitializer;

public final class FabricClientEntrypoint implements ClientModInitializer {
    private final MoudClient client = new MoudClient();

    @Override
    public void onInitializeClient() {
        System.out.println("[MOUD] Engine initialized — auto-update pipeline working!");
        client.init();
    }
}
