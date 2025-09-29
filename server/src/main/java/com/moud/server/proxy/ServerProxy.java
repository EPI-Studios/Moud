package com.moud.server.proxy;

import com.moud.server.api.exception.APIException;
import com.moud.server.api.validation.APIValidator;
import net.kyori.adventure.text.Component;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class ServerProxy {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServerProxy.class);
    private final APIValidator validator = new APIValidator();

    @HostAccess.Export
    public void broadcast(String message) {
        MinecraftServer.getConnectionManager().getOnlinePlayers()
                .forEach(player -> player.sendMessage(message));
    }

    @HostAccess.Export
    public int getPlayerCount() {
        return MinecraftServer.getConnectionManager().getOnlinePlayerCount();
    }

    @HostAccess.Export
    public ProxyArray getPlayers() {
        List<PlayerProxy> playerList = MinecraftServer.getConnectionManager().getOnlinePlayers()
                .stream()
                .map(PlayerProxy::new)
                .collect(Collectors.toList());
        return new JsArrayProxy(playerList);
    }

    @HostAccess.Export
    public PlayerProxy getPlayer(String username) {
        validator.validateString(username, "username");
        Player player = MinecraftServer.getConnectionManager().getPlayer(username);
        return wrapPlayer(player);
    }

    @HostAccess.Export
    public PlayerProxy getPlayerByUuid(String uuid) {
        validator.validateString(uuid, "uuid");
        try {
            UUID parsed = UUID.fromString(uuid);
            Player player = MinecraftServer.getConnectionManager().getPlayer(parsed);
            return wrapPlayer(player);
        } catch (IllegalArgumentException e) {
            LOGGER.warn("Attempted to lookup player with invalid UUID '{}'", uuid);
            return null;
        }
    }

    @HostAccess.Export
    public boolean hasPlayer(String username) {
        validator.validateString(username, "username");
        return MinecraftServer.getConnectionManager().getPlayer(username) != null;
    }

    @HostAccess.Export
    public ProxyArray getPlayerNames() {
        List<String> names = MinecraftServer.getConnectionManager().getOnlinePlayers()
                .stream()
                .map(Player::getUsername)
                .collect(Collectors.toList());
        return new JsArrayProxy(names);
    }

    @HostAccess.Export
    public void runCommand(String command) {
        validator.validateString(command, "command");
        try {
            MinecraftServer.getCommandManager().execute(
                    MinecraftServer.getCommandManager().getConsoleSender(),
                    command
            );
        } catch (Exception e) {
            LOGGER.error("Failed to execute console command '{}'", command, e);
            throw new APIException("COMMAND_EXECUTION_FAILED", "Failed to execute command: " + command, e);
        }
    }

    @HostAccess.Export
    public void broadcastActionBar(String message) {
        validator.validateString(message, "message");
        Component component = Component.text(message);
        MinecraftServer.getConnectionManager().getOnlinePlayers()
                .forEach(player -> player.sendActionBar(component));
    }

    private PlayerProxy wrapPlayer(Player player) {
        return player != null ? new PlayerProxy(player) : null;
    }
}
