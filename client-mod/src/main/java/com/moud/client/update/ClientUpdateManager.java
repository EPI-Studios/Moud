package com.moud.client.update;

import com.moud.client.api.service.ClientAPIService;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public class ClientUpdateManager {
    private final ClientAPIService apiService;

    public ClientUpdateManager(ClientAPIService apiService) {
        this.apiService = apiService;
        registerTickHandlers();
    }

    private void registerTickHandlers() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            apiService.input.update();
            apiService.rendering.triggerRenderEvents();
        });
    }
}