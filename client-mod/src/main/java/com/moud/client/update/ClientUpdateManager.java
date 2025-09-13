package com.moud.client.update;

import com.moud.client.api.service.ClientAPIService;

public record ClientUpdateManager(ClientAPIService apiService) {

    public void tick() {
        if (apiService.input != null) {
            apiService.input.update();
        }
    }

    public void cleanup() {
    }
}