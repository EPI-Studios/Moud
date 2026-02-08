package com.moud.server.plugin.impl;

import com.moud.api.math.Vector3;
import com.moud.plugin.api.player.PlayerContext;
import com.moud.plugin.api.services.PlayerService;
import com.moud.server.plugin.player.PlayerContextImpl;
import net.kyori.adventure.text.Component;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public final class PlayerServiceImpl implements PlayerService {

    @Override
    public Collection<PlayerContext> onlinePlayers() {
        return MinecraftServer.getConnectionManager().getOnlinePlayers().stream()
                .map(PlayerContextImpl::new)
                .collect(Collectors.toUnmodifiableList());
    }

    @Override
    public Optional<PlayerContext> byName(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        return MinecraftServer.getConnectionManager().getOnlinePlayers().stream()
                .filter(p -> p.getUsername().equalsIgnoreCase(username))
                .findFirst()
                .map(PlayerContextImpl::new);
    }

    @Override
    public Optional<PlayerContext> byId(UUID uuid) {
        if (uuid == null) {
            return Optional.empty();
        }
        return MinecraftServer.getConnectionManager().getOnlinePlayers().stream()
                .filter(p -> p.getUuid().equals(uuid))
                .findFirst()
                .map(PlayerContextImpl::new);
    }

    @Override
    public void broadcast(String message) {
        if (message == null) return;
        MinecraftServer.getConnectionManager().getOnlinePlayers()
                .forEach(player -> player.sendMessage(message));
    }

    @Override
    public void sendMessage(PlayerContext context, String message) {
        if (context == null || context.player() == null || message == null) return;
        context.player().sendMessage(message);
    }

    @Override
    public void sendActionBar(PlayerContext context, String message) {
        if (context == null || context.player() == null || message == null) return;
        context.player().sendActionBar(Component.text(message));
    }

    @Override
    public void broadcastActionBar(String message) {
        if (message == null) return;
        Component component = Component.text(message);
        MinecraftServer.getConnectionManager().getOnlinePlayers()
                .forEach(player -> player.sendActionBar(component));
    }

    @Override
    public void teleport(PlayerContext context, Vector3 position) {
        if (context == null || context.player() == null || position == null) return;
        Player player = context.player();
        Pos destination = new Pos(position.x, position.y, position.z, player.getPosition().yaw(), player.getPosition().pitch());
        player.teleport(destination);
    }
}
