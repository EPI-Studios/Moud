package com.moud.server.minestom.runtime;

import com.moud.core.scene.Node;
import com.moud.core.scene.PlainNode;
import com.moud.core.util.ParseUtils;
import com.moud.net.protocol.PlayerMotion;
import com.moud.net.transport.Lane;
import com.moud.server.minestom.engine.ServerScene;
import com.moud.server.minestom.net.PlayerMessageSink;
import com.moud.server.minestom.util.DebugLog;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;

public final class PlayerBodyManager {

    private record AnchorSnapshot(boolean active, float x, float y, float z, float yaw) {
        static final AnchorSnapshot INACTIVE = new AnchorSnapshot(false, 0f, 0f, 0f, 0f);
    }

    private final Map<UUID, Long> bodyNodeIdByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, ServerScene> sceneByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, AnchorSnapshot> lastAnchorByPlayer = new ConcurrentHashMap<>();
    private final PlayerMessageSink playerMessageSink;

    public PlayerBodyManager() {
        this(PlayerMessageSink.NOOP);
    }

    public PlayerBodyManager(PlayerMessageSink playerMessageSink) {
        this.playerMessageSink = Objects.requireNonNull(playerMessageSink, "playerMessageSink");
    }

    public void onPlayerSpawn(Player player, ServerScene scene) {
        if (player == null || scene == null) {
            return;
        }
        UUID uuid = player.getUuid();
        onPlayerLeave(uuid);

        PlainNode body = new PlainNode(player.getUsername() + "_body");
        body.setProperty("@type", "PlayerAttachment");
        body.setProperty("target", uuid.toString());
        body.setProperty("player_name", player.getUsername());
        body.setProperty("attachment_point", "root");

        Pos pos = player.getPosition();
        body.setProperty("x", String.valueOf((float) pos.x()));
        body.setProperty("y", String.valueOf((float) pos.y()));
        body.setProperty("z", String.valueOf((float) pos.z()));
        body.setProperty("ry", String.valueOf(pos.yaw()));

        scene.engine().sceneTree().root().addChild(body);
        long nodeId = body.nodeId();
        if (nodeId <= 0L) {
            DebugLog.error("player-body", "PlayerBody node did not receive a valid nodeId for player " + player.getUsername(), null);
            return;
        }

        bodyNodeIdByPlayer.put(uuid, nodeId);
        sceneByPlayer.put(uuid, scene);
        scene.engine().bumpSceneRevision();

        DebugLog.debug("player-body", "Created PlayerAttachment nodeId=" + nodeId + " for player=" + player.getUsername());
    }

    public void onPlayerLeave(UUID uuid) {
        if (uuid == null) {
            return;
        }
        lastAnchorByPlayer.remove(uuid);
        Long nodeId = bodyNodeIdByPlayer.remove(uuid);
        ServerScene scene = sceneByPlayer.remove(uuid);
        if (nodeId == null || scene == null) {
            return;
        }
        Node body = scene.engine().sceneTree().getNode(nodeId);
        if (body == null) {
            return;
        }
        body.queueFree();
        scene.engine().bumpSceneRevision();
        DebugLog.debug("player-body", "Removed PlayerBody nodeId=" + nodeId + " for uuid=" + uuid);
    }

    public void tick(Player player) {
        if (player == null) {
            return;
        }
        UUID uuid = player.getUuid();
        Long nodeId = bodyNodeIdByPlayer.get(uuid);
        ServerScene scene = sceneByPlayer.get(uuid);
        if (nodeId == null || scene == null) {
            return;
        }

        Node body = scene.engine().sceneTree().getNode(nodeId);
        if (body == null) {
            bodyNodeIdByPlayer.remove(uuid);
            sceneByPlayer.remove(uuid);
            return;
        }

        Pos pos = player.getPosition();
        body.setProperty("x", String.valueOf((float) pos.x()));
        body.setProperty("y", String.valueOf((float) pos.y()));
        body.setProperty("z", String.valueOf((float) pos.z()));
        body.setProperty("ry", String.valueOf(pos.yaw()));

        tickAnchor(player, body, scene);
        scene.engine().bumpSceneRevision();
    }

    private void tickAnchor(Player player, Node body, ServerScene scene) {
        UUID uuid = player.getUuid();
        String anchorRaw = body.getProperty("anchor_node_id");
        if (anchorRaw == null || anchorRaw.isBlank()) {
            AnchorSnapshot prev = lastAnchorByPlayer.getOrDefault(uuid, AnchorSnapshot.INACTIVE);
            if (prev.active) {
                lastAnchorByPlayer.put(uuid, AnchorSnapshot.INACTIVE);
                playerMessageSink.send(uuid, Lane.EVENTS, PlayerMotion.anchorClear());
            }
            return;
        }
        long anchorId;
        try {
            anchorId = Long.parseLong(anchorRaw.trim());
        } catch (NumberFormatException e) {
            return;
        }
        Node anchor = scene.engine().sceneTree().getNode(anchorId);
        if (anchor == null) {
            return;
        }
        float ax = ParseUtils.parseFloat(anchor.getProperty("x"), 0f);
        float ay = ParseUtils.parseFloat(anchor.getProperty("y"), 0f);
        float az = ParseUtils.parseFloat(anchor.getProperty("z"), 0f);
        float ary = ParseUtils.parseFloat(anchor.getProperty("ry"), 0f);

        AnchorSnapshot next = new AnchorSnapshot(true, ax, ay, az, ary);
        AnchorSnapshot prev = lastAnchorByPlayer.get(uuid);
        if (prev == null || !prev.equals(next)) {
            lastAnchorByPlayer.put(uuid, next);
            playerMessageSink.send(uuid, Lane.EVENTS, PlayerMotion.anchorSet(ax, ay, az, ary));
        }

        Pos current = player.getPosition();
        player.teleport(new Pos(ax, ay, az, ary, current.pitch()));
    }

    public long getBodyNodeId(UUID uuid) {
        Long id = bodyNodeIdByPlayer.get(uuid);
        return id == null ? 0L : id;
    }
}