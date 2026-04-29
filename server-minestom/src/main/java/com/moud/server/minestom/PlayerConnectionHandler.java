package com.moud.server.minestom;

import com.moud.core.ProtocolVersions;
import com.moud.net.protocol.MatchmakerStatus;
import com.moud.net.protocol.ServerHello;
import com.moud.net.session.Session;
import com.moud.net.session.SessionRole;
import com.moud.net.session.SessionState;
import com.moud.net.transport.Lane;
import com.moud.server.minestom.engine.InstanceMatchmaker;
import com.moud.server.minestom.engine.JoinContext;
import com.moud.server.minestom.engine.ServerScene;
import com.moud.server.minestom.engine.ServerScenes;
import com.moud.server.minestom.net.MinestomPlayerTransport;
import com.moud.server.minestom.runtime.PlayRuntime;
import com.moud.server.minestom.util.DebugLog;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.event.player.PlayerPluginMessageEvent;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.timer.TaskSchedule;

final class PlayerConnectionHandler {
    private static final String QUEUED_STATUS_CODE = "matchmaker:queue";
    private static final String QUEUED_STATUS_LABEL = "Waiting for an open slot";
    private static final long RETRY_INTERVAL_MS = 1_000L;

    private final String channel;
    private final boolean devMode;
    private final ServerScenes scenes;
    private final ServerScene mainScene;
    private final InstanceMatchmaker matchmaker;
    private final PlayModeManager playModeManager;
    private final MessageRouter messageRouter;
    private final Map<UUID, PlayerState> playerStates;
    private final Map<UUID, String> pendingInstanceByUuid = new ConcurrentHashMap<>();
    private final InstanceContainer limboInstance;

    PlayerConnectionHandler(String channel,
                            boolean devMode,
                            ServerScenes scenes,
                            ServerScene mainScene,
                            InstanceMatchmaker matchmaker,
                            PlayModeManager playModeManager,
                            MessageRouter messageRouter,
                            Map<UUID, PlayerState> playerStates,
                            InstanceContainer limboInstance) {
        this.channel = Objects.requireNonNull(channel, "channel");
        this.devMode = devMode;
        this.scenes = Objects.requireNonNull(scenes, "scenes");
        this.mainScene = Objects.requireNonNull(mainScene, "mainScene");
        this.matchmaker = Objects.requireNonNull(matchmaker, "matchmaker");
        this.playModeManager = Objects.requireNonNull(playModeManager, "playModeManager");
        this.messageRouter = Objects.requireNonNull(messageRouter, "messageRouter");
        this.playerStates = Objects.requireNonNull(playerStates, "playerStates");
        this.limboInstance = Objects.requireNonNull(limboInstance, "limboInstance");
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

    void onConfiguration(AsyncPlayerConfigurationEvent event) {
        if (event == null) {
            return;
        }
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        PlayerState ps = state(player);
        ServerScene chosen = matchmake(player, ps);
        if (chosen != null) {
            pendingInstanceByUuid.put(player.getUuid(), chosen.instanceId());
            event.setSpawningInstance(chosen.instance());
            Pos startPos = PlayRuntime.findPlayerStartPos(chosen);
            player.setRespawnPoint(startPos != null ? startPos : new Pos(0, 64, 0));
            return;
        }
        ps.inLimbo = true;
        ps.queuedPlaceId = ps.activeSceneId == null || ps.activeSceneId.isBlank() ? "main" : ps.activeSceneId;
        DebugLog.info("matchmaker", "queueing player=" + player.getUsername() + " place=" + ps.queuedPlaceId + " into limbo");
        event.setSpawningInstance(limboInstance);
        player.setRespawnPoint(new Pos(0, 64, 0));
    }

    private ServerScene matchmake(Player player, PlayerState ps) {
        String placeId = ps != null && ps.activeSceneId != null && !ps.activeSceneId.isBlank()
                ? ps.activeSceneId
                : "main";
        String reservedInstanceId = ps == null ? null : ps.pendingReservedInstanceId;
        String reservationToken = ps == null ? null : ps.pendingReservationToken;
        JoinContext ctx = reservedInstanceId != null && !reservedInstanceId.isBlank()
                ? JoinContext.forInstance(placeId, reservedInstanceId, reservationToken)
                : JoinContext.forPlace(placeId);
        return matchmaker.joinOrCreate(ctx, placeId);
    }

    void onPlayerSpawn(Player player) {
        if (player == null) {
            return;
        }
        PlayerState ps = state(player);

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

        if (ps.inLimbo) {
            sendStatus(ps, true);
            scheduleQueueRetry(player, ps);
            return;
        }

        finalizePlacement(player, ps);
    }

    private void finalizePlacement(Player player, PlayerState ps) {
        ServerScene spawnScene = resolveSpawnScene(player.getUuid(), ps);
        if (spawnScene == null) {
            return;
        }
        ps.activeInstanceId = spawnScene.instanceId();
        ps.activeSceneId = spawnScene.placeId();
        ps.pendingReservedInstanceId = null;
        ps.pendingReservationToken = null;
        Pos startPos = PlayRuntime.findPlayerStartPos(spawnScene);
        player.setRespawnPoint(startPos != null ? startPos : new Pos(0, 64, 0));
        playModeManager.onPlayerSpawn(player, ps, spawnScene);
    }

    private void scheduleQueueRetry(Player player, PlayerState ps) {
        MinecraftServer.getSchedulerManager()
                .buildTask(() -> tryDequeue(player, ps))
                .delay(TaskSchedule.duration(Duration.ofMillis(RETRY_INTERVAL_MS)))
                .schedule();
    }

    private void tryDequeue(Player player, PlayerState ps) {
        if (player == null || ps == null) return;
        if (!player.isOnline() || !ps.inLimbo) {
            return;
        }
        ServerScene chosen = matchmake(player, ps);
        if (chosen == null) {
            scheduleQueueRetry(player, ps);
            return;
        }
        Pos startPos = PlayRuntime.findPlayerStartPos(chosen);
        Pos targetPos = startPos != null ? startPos : new Pos(0, 64, 0);
        player.setInstance(chosen.instance(), targetPos).whenComplete((unused, error) -> {
            if (error != null) {
                DebugLog.error("matchmaker", "failed to dequeue player=" + player.getUsername() + ": " + error.getMessage());
                scheduleQueueRetry(player, ps);
                return;
            }
            ps.inLimbo = false;
            ps.queuedPlaceId = null;
            pendingInstanceByUuid.put(player.getUuid(), chosen.instanceId());
            sendStatus(ps, false);
            finalizePlacement(player, ps);
        });
    }

    private void sendStatus(PlayerState ps, boolean show) {
        Session session = ps == null ? null : ps.session;
        if (session == null || session.state() != SessionState.CONNECTED) {
            return;
        }
        session.send(Lane.STATE, new MatchmakerStatus(QUEUED_STATUS_CODE, QUEUED_STATUS_LABEL, show));
    }

    private ServerScene resolveSpawnScene(UUID uuid, PlayerState ps) {
        String pendingInstanceId = pendingInstanceByUuid.remove(uuid);
        if (pendingInstanceId != null) {
            ServerScene scene = scenes.getByInstanceId(pendingInstanceId);
            if (scene != null && !scene.isDisposed()) {
                return scene;
            }
        }
        return ps.activeInstanceId == null ? null : scenes.getByInstanceId(ps.activeInstanceId);
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
        pendingInstanceByUuid.remove(player.getUuid());
        playerStates.remove(player.getUuid());
        playModeManager.onDisconnect(player.getUuid());
    }
}
