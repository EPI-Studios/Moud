package com.moud.client.update;

import com.moud.client.api.service.ClientAPIService;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.graalvm.polyglot.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClientUpdateManager {
    private final ClientAPIService apiService;
    private static final Logger LOGGER = LoggerFactory.getLogger("ClientScript.ClientUpdateManager");
    private Context jsContext;
    public ClientUpdateManager(ClientAPIService apiService) {
        this.apiService = apiService;
        registerTickHandlers();
    }

    private void registerTickHandlers() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            apiService.input.update();
        });
    }
    public void cleanup() {

        jsContext = null;
        LOGGER.info("InputService cleaned up.");
    }

}