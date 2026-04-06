package com.moud.server.minestom;

import com.moud.server.minestom.engine.ServerScenes;
import com.moud.server.minestom.scripting.ScriptService;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;

final class ServerTickLoop {
    private final double dtSeconds;
    private final ServerScenes scenes;
    private final ScriptService scripts;
    private final PlayModeManager playModeManager;
    private final PlayerConnectionHandler connections;

    ServerTickLoop(double dtSeconds,
                   ServerScenes scenes,
                   ScriptService scripts,
                   PlayModeManager playModeManager,
                   PlayerConnectionHandler connections) {
        this.dtSeconds = dtSeconds;
        this.scenes = Objects.requireNonNull(scenes, "scenes");
        this.scripts = Objects.requireNonNull(scripts, "scripts");
        this.playModeManager = Objects.requireNonNull(playModeManager, "playModeManager");
        this.connections = Objects.requireNonNull(connections, "connections");
    }

    void tick() {
        boolean pausedForEditor = playModeManager.isPausedForEditor(connections.states());
        if (pausedForEditor) {
            scenes.tickAllEditor();
        } else {
            scenes.tickAllPlay(dtSeconds);
        }

        if (!pausedForEditor) {
            Map<UUID, float[]> playerPositions = new HashMap<>();
            Map<UUID, String> playerNames = new HashMap<>();
            for (Player p : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
                Pos pos = p.getPosition();
                playerPositions.put(p.getUuid(), new float[]{(float) pos.x(), (float) pos.y(), (float) pos.z(), pos.yaw()});
                playerNames.put(p.getUuid(), p.getUsername());
            }
            scripts.updatePlayerPositions(playerPositions, dtSeconds);
            scripts.updatePlayerNames(playerNames);
        }

        playModeManager.tickScenes(dtSeconds, connections.states());

        for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            PlayerState ps = connections.get(player.getUuid());
            if (ps == null || ps.session == null) {
                continue;
            }
            playModeManager.tickPlayer(player, ps);
            ps.session.tick();
        }
    }
}
