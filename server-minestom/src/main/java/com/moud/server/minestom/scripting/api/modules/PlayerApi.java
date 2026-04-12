package com.moud.server.minestom.scripting.api.modules;


import com.moud.core.scene.Node;
import com.moud.net.protocol.PlayerMotion;
import com.moud.net.transport.Lane;
import com.moud.server.minestom.engine.ServerScene;
import com.moud.server.minestom.scripting.player.PlayerInfo;
import com.moud.server.minestom.scripting.player.PlayerStateManager;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import org.graalvm.polyglot.HostAccess;

import java.util.UUID;

public final class PlayerApi {
    private final ServerScene scene;
    private final PlayerStateManager playerState;
    private final double[] zero = new double[]{0.0, 0.0, 0.0};

    public PlayerApi(ServerScene scene, PlayerStateManager playerState) {
        this.scene = scene;
        this.playerState = playerState;
    }

    @HostAccess.Export
    public double playerX(Node node) {
        return playerState.playerCoord(node, 0);
    }

    @HostAccess.Export
    public double playerY(Node node) {
        return playerState.playerCoord(node, 1);
    }

    @HostAccess.Export
    public double playerZ(Node node) {
        return playerState.playerCoord(node, 2);
    }

    @HostAccess.Export
    public double playerYaw(Node node) {
        return playerState.playerCoord(node, 3);
    }

    @HostAccess.Export
    public boolean teleportPlayer(String playerUuid, double x, double y, double z) {
        return teleportPlayer(playerUuid, x, y, z, Double.NaN, Double.NaN);
    }

    @HostAccess.Export
    public boolean teleportPlayer(String playerUuid, double x, double y, double z, double yawDeg, double pitchDeg) {
        if (playerUuid == null || playerUuid.isBlank()) {
            return false;
        }
        Player player = findPlayer(playerUuid);
        if (player == null) {
            return false;
        }
        Pos cur = player.getPosition();
        float yaw = Double.isFinite(yawDeg) ? (float) yawDeg : cur.yaw();
        float pitch = Double.isFinite(pitchDeg) ? (float) pitchDeg : cur.pitch();
        player.teleport(new Pos(x, y, z, yaw, pitch));
        return true;
    }

    @HostAccess.Export
    public void playerSetVelocity(String playerUuid, double vx, double vy, double vz) {
        Player player = findPlayer(playerUuid);
        if (player != null) {
            scene.instance();
        }
    }

    @HostAccess.Export
    public void playerAddVelocity(String playerUuid, double vx, double vy, double vz) {
        playerSetVelocity(playerUuid, vx, vy, vz);
    }

    @HostAccess.Export
    public double[] playerGetVelocity(String playerUuid) {
        return playerState.getPlayerVelocity(playerUuid);
    }

    @HostAccess.Export
    public PlayerInfo[] getPlayers() {
        return playerState.getPlayers();
    }

    private Player findPlayer(String playerUuid) {
        UUID uuid;
        try {
            uuid = UUID.fromString(playerUuid.trim());
        } catch (Exception ignored) {
            return null;
        }
        for (Player player : scene.instance().getPlayers()) {
            if (player != null && uuid.equals(player.getUuid())) {
                return player;
            }
        }
        return null;
    }
}
