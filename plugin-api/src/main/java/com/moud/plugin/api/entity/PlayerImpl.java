package com.moud.plugin.api.entity;

import com.moud.api.math.Vector3;
import com.moud.plugin.api.PluginContext;
import com.moud.plugin.api.network.ClientMessageBuilder;
import com.moud.plugin.api.player.PlayerContext;
import com.moud.plugin.api.ui.PlayerOverlay;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.GameMode;
import net.minestom.server.instance.Instance;

import java.util.UUID;

final class PlayerImpl implements Player {
    private final PluginContext context;
    private final PlayerContext playerContext;
    private final PlayerOverlay overlay;

    PlayerImpl(PluginContext context, PlayerContext playerContext) {
        this.context = context;
        this.playerContext = playerContext;
        this.overlay = new PlayerOverlayImpl(context, playerContext, this);
    }

    @Override
    public String name() {
        return playerContext.username();
    }

    @Override
    public Vector3 position() {
        Pos pos = playerContext.player().getPosition();
        return new Vector3(pos.x(), pos.y(), pos.z());
    }

    @Override
    public Player sendMessage(String message) {
        playerContext.player().sendMessage(message);
        return this;
    }

    @Override
    public Player toast(String title, String body) {
        context.rendering().toast(playerContext, title, body);
        return this;
    }

    @Override
    public Player teleport(Vector3 position) {
        playerContext.player().teleport(new Pos(position.x, position.y, position.z, playerContext.player().getPosition().yaw(), playerContext.player().getPosition().pitch()));
        return this;
    }

    @Override
    public ClientMessageBuilder send(String eventName) {
        return ClientMessageBuilder.toPlayer(context.clients(), playerContext, eventName);
    }

    @Override
    public UUID uuid() {
        return playerContext.player().getUuid();
    }

    @Override
    public PlayerContext context() {
        return playerContext;
    }

    @Override
    public GameMode gameMode() {
        return playerContext.player().getGameMode();
    }

    @Override
    public Player setGameMode(GameMode gameMode) {
        playerContext.player().setGameMode(gameMode);
        return this;
    }


    @Override
    public Instance instance() {
        return playerContext.player().getInstance();
    }

    @Override
    public Player teleportInstance(Vector3 position, UUID instanceId) {
        Instance instance = MinecraftServer.getInstanceManager().getInstance(instanceId);
        if (instance != null) {
            playerContext.player().setInstance(instance);
           teleport(position);
        }
        return this;
    }

    @Override
    public PlayerOverlay uiOverlay() {
        return overlay;
    }
}
