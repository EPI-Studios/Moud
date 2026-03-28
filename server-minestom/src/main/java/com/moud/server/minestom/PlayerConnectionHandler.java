package com.moud.server.minestom;

import com.moud.core.ProtocolVersions;
import com.moud.net.protocol.ServerHello;
import com.moud.net.session.Session;
import com.moud.net.session.SessionRole;
import com.moud.net.session.SessionState;
import com.moud.server.minestom.engine.ServerScene;
import com.moud.server.minestom.engine.ServerScenes;
import com.moud.server.minestom.net.MinestomPlayerTransport;
import com.moud.server.minestom.util.DebugLog;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.event.player.PlayerPluginMessageEvent;

final class PlayerConnectionHandler {
    private final String channel;
    private final boolean devMode;
    private final ServerScenes scenes;
    private final ServerScene mainScene;
    private final PlayModeManager playModeManager;
    private final MessageRouter messageRouter;
    private final Map<UUID, PlayerState> playerStates;

    PlayerConnectionHandler(String channel,
                            boolean devMode,
                            ServerScenes scenes,
                            ServerScene mainScene,
                            PlayModeManager playModeManager,
                            MessageRouter messageRouter,
                            Map<UUID, PlayerState> playerStates) {
        this.channel = Objects.requireNonNull(channel, "channel");
        this.devMode = devMode;
        this.scenes = Objects.requireNonNull(scenes, "scenes");
        this.mainScene = Objects.requireNonNull(mainScene, "mainScene");
        this.playModeManager = Objects.requireNonNull(playModeManager, "playModeManager");
        this.messageRouter = Objects.requireNonNull(messageRouter, "messageRouter");
        this.playerStates = Objects.requireNonNull(playerStates, "playerStates");
    }

    Map<UUID, PlayerState> states() {
        return playerStates;
    }

    PlayerState get(UUID uuid) {
        return uuid == null ? null : playerStates.get(uuid);
    }

    private PlayerState state(Player player) {
        return playerStates.computeIfAbsent(player.getUuid(), ignored -> new PlayerState());
    }

    void onPlayerSpawn(Player player) {
        if (player == null) {
            return;
        }
        PlayerState ps = state(player);

        player.setRespawnPoint(new Pos(0, 64, 0));
        player.setGameMode(GameMode.ADVENTURE);
        player.sendPluginMessage("minecraft:register", channel.getBytes(StandardCharsets.UTF_8));

        if (ps.transport == null) {
            ps.transport = new MinestomPlayerTransport(player, channel);
        }
        if (ps.session == null) {
            Session session = new Session(SessionRole.SERVER, ps.transport);
            session.setServerHelloSupplier(() -> new ServerHello(ProtocolVersions.PROTOCOL_VERSION, devMode));
            session.setLogSink(msg -> DebugLog.debug("session/" + player.getUsername(), msg));
            session.setMessageHandler((lane, message) -> messageRouter.onSessionMessage(player, ps, lane, message));
            session.start();
            ps.session = session;
        }

        ServerScene spawnScene = scenes.get(ps.activeSceneId);
        if (spawnScene == null) {
            spawnScene = mainScene;
        }
        playModeManager.onPlayerSpawn(player, ps, spawnScene);
    }

    void onPluginMessage(PlayerPluginMessageEvent event) {
        if (event == null) {
            return;
        }
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        PlayerState ps = playerStates.get(player.getUuid());
        if (ps == null) {
            return;
        }
        MinestomPlayerTransport transport = ps.transport;
        if (transport == null) {
            return;
        }
        transport.acceptPluginMessage(event.getIdentifier(), event.getMessage());
        Session session = ps.session;
        if (session != null && session.state() == SessionState.CONNECTED) {
            session.tick();
        }
    }

    void onDisconnect(Player player) {
        if (player == null) {
            return;
        }
        playerStates.remove(player.getUuid());
        playModeManager.onDisconnect(player.getUuid());
    }
}
