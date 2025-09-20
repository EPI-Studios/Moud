package com.moud.server.proxy;

import com.moud.api.math.Vector3;
import com.moud.server.cursor.CursorManager;
import com.moud.server.cursor.CursorVisibilityManager;
import com.moud.server.network.ServerNetworkManager;
import net.minestom.server.entity.Player;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

public class CursorProxy {
    private final Player player;
    private final CursorManager cursorManager;
    private final CursorVisibilityManager visibilityManager;
    private String texture = "default";
    private Vector3 color = new Vector3(1.0f, 1.0f, 1.0f);
    private float scale = 1.0f;
    private boolean visible = true;

    public CursorProxy(Player player) {
        this.player = player;
        this.cursorManager = CursorManager.getInstance();
        this.visibilityManager = CursorVisibilityManager.getInstance();
    }

    @HostAccess.Export
    public Vector3 getWorldPosition() {
        CursorManager.CursorData data = cursorManager.getCursorData(player);
        return data != null ? data.getWorldPosition() : Vector3.zero();
    }

    @HostAccess.Export
    public Vector3 getCameraPosition() {
        CursorManager.CursorData data = cursorManager.getCursorData(player);
        return data != null ? data.getCameraPosition() : Vector3.zero();
    }

    @HostAccess.Export
    public Vector3 getDirection() {
        CursorManager.CursorData data = cursorManager.getCursorData(player);
        return data != null ? data.getCameraDirection() : Vector3.zero();
    }

    @HostAccess.Export
    public String getHitBlock() {
        CursorManager.CursorData data = cursorManager.getCursorData(player);
        return data != null ? data.getHitBlock() : "minecraft:air";
    }

    @HostAccess.Export
    public double getDistance() {
        CursorManager.CursorData data = cursorManager.getCursorData(player);
        return data != null ? data.getDistance() : 0.0;
    }

    @HostAccess.Export
    public boolean isHit() {
        CursorManager.CursorData data = cursorManager.getCursorData(player);
        return data != null && data.isHit();
    }

    @HostAccess.Export
    public void setTexture(String textureId) {
        this.texture = textureId;
        updateAppearance();
    }

    @HostAccess.Export
    public void setColor(Value colorValue) {
        if (colorValue != null && colorValue.hasMembers()) {
            float r = colorValue.hasMember("r") ? colorValue.getMember("r").asFloat() : 1.0f;
            float g = colorValue.hasMember("g") ? colorValue.getMember("g").asFloat() : 1.0f;
            float b = colorValue.hasMember("b") ? colorValue.getMember("b").asFloat() : 1.0f;
            this.color = new Vector3(r, g, b);
            updateAppearance();
        }
    }

    @HostAccess.Export
    public void setScale(float scale) {
        this.scale = Math.max(0.1f, Math.min(5.0f, scale));
        updateAppearance();
    }

    @HostAccess.Export
    public void setVisible(boolean visible) {
        this.visible = visible;
        updateVisibility();
    }

    @HostAccess.Export
    public void setVisibleTo(Value playerList) {
        if (playerList != null && playerList.hasArrayElements()) {
            java.util.List<Player> players = new java.util.ArrayList<>();
            long size = playerList.getArraySize();
            for (long i = 0; i < size; i++) {
                Value element = playerList.getArrayElement(i);
                if (element.hasMembers() && element.hasMember("getName")) {
                    String playerName = element.getMember("getName").execute().asString();
                    Player targetPlayer = net.minestom.server.MinecraftServer.getConnectionManager()
                            .getOnlinePlayers()
                            .stream()
                            .filter(p -> p.getUsername().equals(playerName))
                            .findFirst()
                            .orElse(null);
                    if (targetPlayer != null) {
                        players.add(targetPlayer);
                    }
                }
            }
            visibilityManager.setVisibleTo(player, players);
        }
    }

    @HostAccess.Export
    public void setVisibleToAll() {
        visibilityManager.setVisibleToAll(player);
    }

    @HostAccess.Export
    public void setVisibleToNone() {
        visibilityManager.setVisibleToNone(player);
    }

    @HostAccess.Export
    public void update() {
        cursorManager.updateCursor(player);
        syncToClients();
    }

    private void updateAppearance() {
        if (ServerNetworkManager.getInstance() != null) {
            String eventData = String.format(
                    "{\"texture\":\"%s\",\"color\":{\"r\":%f,\"g\":%f,\"b\":%f},\"scale\":%f}",
                    texture, color.x, color.y, color.z, scale
            );

            for (Player viewer : visibilityManager.getViewers(player)) {
                ServerNetworkManager.getInstance().sendScriptEvent(viewer, "cursor:appearance", eventData);
            }
        }
    }

    private void updateVisibility() {
        if (ServerNetworkManager.getInstance() != null) {
            String eventData = String.format("{\"visible\":%b}", visible);

            for (Player viewer : visibilityManager.getViewers(player)) {
                ServerNetworkManager.getInstance().sendScriptEvent(viewer, "cursor:visibility", eventData);
            }
        }
    }

    private void syncToClients() {
        if (!visible || ServerNetworkManager.getInstance() == null) return;

        CursorManager.CursorData data = cursorManager.getCursorData(player);
        if (data == null) return;

        String eventData = String.format(
                "{\"playerId\":\"%s\",\"worldPos\":{\"x\":%f,\"y\":%f,\"z\":%f},\"hit\":%b,\"distance\":%f}",
                player.getUuid().toString(),
                data.getWorldPosition().x, data.getWorldPosition().y, data.getWorldPosition().z,
                data.isHit(), data.getDistance()
        );

        for (Player viewer : visibilityManager.getViewers(player)) {
            ServerNetworkManager.getInstance().sendScriptEvent(viewer, "cursor:position", eventData);
        }
    }
}