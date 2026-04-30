package com.moud.server.minestom.scripting.player;

import com.moud.net.protocol.CursorState;
import com.moud.net.protocol.EditorDiagnosticEvent;
import com.moud.net.protocol.Message;
import com.moud.net.protocol.PlayerMotion;
import com.moud.net.protocol.TweenCancel;
import com.moud.net.protocol.TweenStart;
import com.moud.net.transport.Lane;
import com.moud.server.minestom.engine.ServerScene;
import com.moud.server.minestom.net.PlayerMessageSink;
import net.minestom.server.entity.Player;

import java.util.UUID;

public final class PlayerNetworkSink {
    private final PlayerMessageSink playerMessageSink;

    public PlayerNetworkSink(PlayerMessageSink playerMessageSink) {
        this.playerMessageSink = playerMessageSink;
    }

    public void sendPlayerPosition(String playerUuid, float x, float y, float z, float yawDeg) {
        send(playerUuid, PlayerMotion.position(x, y, z, yawDeg));
    }

    public void sendPlayerVelocity(String playerUuid, float vx, float vy, float vz) {
        send(playerUuid, PlayerMotion.velocity(vx, vy, vz));
    }

    public void sendCursorState(String playerUuid, boolean enabled, boolean osVisible) {
        send(playerUuid, new CursorState(enabled, osVisible));
    }

    public void publishEditorDiagnostic(ServerScene scene, String severity, String source, String message) {
        if (scene == null || scene.instance() == null || message == null || message.isBlank()) {
            return;
        }
        EditorDiagnosticEvent event = new EditorDiagnosticEvent(
                severity == null ? "ERROR" : severity,
                source == null ? "Runtime" : source,
                message
        );
        for (Player player : scene.instance().getPlayers()) {
            if (player == null) {
                continue;
            }
            try {
                playerMessageSink.send(player.getUuid(), Lane.EVENTS, event);
            } catch (Exception ignored) {
            }
        }
    }

    public void broadcastTweenStart(ServerScene scene, TweenStart packet) {
        broadcast(scene, packet);
    }

    public void broadcastTweenCancel(ServerScene scene, TweenCancel packet) {
        broadcast(scene, packet);
    }

    private void broadcast(ServerScene scene, Message message) {
        if (scene == null || scene.instance() == null || message == null) {
            return;
        }
        for (Player player : scene.instance().getPlayers()) {
            if (player == null) {
                continue;
            }
            try {
                playerMessageSink.send(player.getUuid(), Lane.EVENTS, message);
            } catch (Exception ignored) {
            }
        }
    }

    private void send(String playerUuid, Message message) {
        if (playerUuid == null || playerUuid.isBlank() || message == null) {
            return;
        }
        try {
            playerMessageSink.send(UUID.fromString(playerUuid), Lane.EVENTS, message);
        } catch (Exception ignored) {
        }
    }
}
