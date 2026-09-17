package com.meekdev.moud.mod.client;

import com.meekdev.moud.mod.transport.payload.WorldPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class ClientWorld {

    private ClientWorld() {}

    public static void listen() {
        ClientPlayNetworking.registerGlobalReceiver(WorldPayload.TYPE, (payload, context) ->
                GameState.INSTANCE.gravityFromServer(payload.gravity()));
    }
}
