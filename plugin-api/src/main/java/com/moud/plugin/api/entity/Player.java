package com.moud.plugin.api.entity;

import com.moud.api.math.Vector3;
import com.moud.plugin.api.PluginContext;
import com.moud.plugin.api.network.ClientMessageBuilder;
import com.moud.plugin.api.player.PlayerContext;

public interface Player {
    String name();
    Vector3 position();
    Player sendMessage(String message);
    Player toast(String title, String body);
    Player teleport(Vector3 position);
    ClientMessageBuilder send(String eventName);

    static Player wrap(PluginContext context, PlayerContext playerContext) {
        return new PlayerImpl(context, playerContext);
    }
}
