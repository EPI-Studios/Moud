package com.moud.server.minestom.scripting.api.modules;

import com.moud.core.scripts.luau.LuauExport;
import com.moud.server.minestom.engine.InstanceMatchmaker;
import com.moud.server.minestom.engine.PrivateInstanceTicket;
import com.moud.server.minestom.engine.ServerScene;
import com.moud.server.minestom.scripting.runtime.RuntimeFacade;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import org.graalvm.polyglot.HostAccess;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@LuauExport(name = "ServerApi", doc = "Multi-instance routing: teleport players, create private game instances, query current instance.")
public final class ServerApi {
    private final ServerScene scene;
    private final RuntimeFacade runtime;

    public ServerApi(ServerScene scene, RuntimeFacade runtime) {
        this.scene = scene;
        this.runtime = runtime;
    }

    @HostAccess.Export
    @LuauExport
    public String currentInstanceId() {
        return scene == null ? "" : scene.instanceId();
    }

    @HostAccess.Export
    @LuauExport
    public String currentPlaceId() {
        return scene == null ? "" : scene.placeId();
    }

    @HostAccess.Export
    @LuauExport
    public boolean teleport(String playerUuid, String placeId) {
        return teleport(playerUuid, placeId, null);
    }

    @HostAccess.Export
    @LuauExport
    public boolean teleport(String playerUuid, String placeId, String payload) {
        InstanceMatchmaker mm = runtime == null ? null : runtime.matchmaker();
        if (mm == null) return false;
        Player player = findPlayer(playerUuid);
        if (player == null) return false;
        byte[] payloadBytes = payload == null ? null : payload.getBytes(StandardCharsets.UTF_8);
        mm.teleport(player, placeId, payloadBytes);
        return true;
    }

    @HostAccess.Export
    @LuauExport
    public boolean teleportToInstance(String playerUuid, String instanceId, String reservationToken) {
        return teleportToInstance(playerUuid, instanceId, reservationToken, null);
    }

    @HostAccess.Export
    @LuauExport
    public boolean teleportToInstance(String playerUuid, String instanceId, String reservationToken, String payload) {
        InstanceMatchmaker mm = runtime == null ? null : runtime.matchmaker();
        if (mm == null) return false;
        Player player = findPlayer(playerUuid);
        if (player == null) return false;
        byte[] payloadBytes = payload == null ? null : payload.getBytes(StandardCharsets.UTF_8);
        mm.teleportToInstance(player, instanceId, reservationToken, payloadBytes);
        return true;
    }

    @HostAccess.Export
    @LuauExport
    public PrivateInstanceTicket createPrivateInstance(String placeId) {
        InstanceMatchmaker mm = runtime == null ? null : runtime.matchmaker();
        if (mm == null) return null;
        return mm.createPrivateInstance(placeId, placeId);
    }

    private Player findPlayer(String playerUuid) {
        if (playerUuid == null || playerUuid.isBlank()) return null;
        UUID uuid;
        try {
            uuid = UUID.fromString(playerUuid.trim());
        } catch (Exception ignored) {
            return null;
        }
        return MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(uuid);
    }
}
