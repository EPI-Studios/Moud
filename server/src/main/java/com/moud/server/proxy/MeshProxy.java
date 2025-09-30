package com.moud.server.proxy;

import com.moud.api.math.Vector3;
import com.moud.api.rendering.mesh.Mesh;
import com.moud.api.rendering.mesh.MeshData;
import com.moud.network.MoudPackets;
import com.moud.server.network.ServerPacketWrapper;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.event.player.PlayerSpawnEvent;
import org.graalvm.polyglot.HostAccess;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class MeshProxy {
    private static final AtomicLong ID_COUNTER = new AtomicLong(1L);
    private static final Map<Long, MeshProxy> ALL_MESHES = new ConcurrentHashMap<>();

    static {
        MinecraftServer.getGlobalEventHandler().addListener(PlayerSpawnEvent.class, event -> {
            if (!event.isFirstSpawn()) {
                return;
            }
            Player player = event.getPlayer();
            UUID playerId = player.getUuid();
            for (MeshProxy proxy : ALL_MESHES.values()) {
                if (proxy.shouldSendTo(playerId)) {
                    proxy.sendCreate(player);
                }
            }
        });
    }

    private final long meshId;
    private MeshData meshData;
    private Vector3 position;
    private Vector3 rotation;
    private Vector3 scale;
    private final Set<UUID> viewers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> hiddenPlayers = ConcurrentHashMap.newKeySet();
    private volatile boolean visibleToAll;

    public MeshProxy(Mesh mesh, Vector3 position, Vector3 rotation, Vector3 scale) {
        this.meshId = ID_COUNTER.getAndIncrement();
        this.meshData = MeshData.fromMesh(mesh);
        this.position = copyVector(position, Vector3.zero());
        this.rotation = copyVector(rotation, Vector3.zero());
        this.scale = copyVector(scale, Vector3.one());
        this.visibleToAll = false;
        ALL_MESHES.put(meshId, this);
    }

    private static Vector3 copyVector(Vector3 value, Vector3 fallback) {
        return value != null ? new Vector3(value) : new Vector3(fallback);
    }

    private boolean shouldSendTo(UUID uuid) {
        if (visibleToAll) {
            return !hiddenPlayers.contains(uuid);
        }
        return viewers.contains(uuid);
    }

    private Collection<Player> targetPlayers() {
        if (visibleToAll) {
            Collection<Player> players = MinecraftServer.getConnectionManager().getOnlinePlayers();
            if (hiddenPlayers.isEmpty()) {
                return players;
            }
            List<Player> filtered = new ArrayList<>();
            for (Player player : players) {
                if (!hiddenPlayers.contains(player.getUuid())) {
                    filtered.add(player);
                }
            }
            return filtered;
        }

        List<Player> players = new ArrayList<>();
        for (UUID viewer : viewers) {
            Player player = findPlayer(viewer);
            if (player != null) {
                players.add(player);
            }
        }
        return players;
    }

    private void sendPacketToTargets(Object packet) {
        Collection<Player> targets = targetPlayers();
        if (targets.isEmpty()) {
            return;
        }
        for (Player player : targets) {
            player.sendPacket(ServerPacketWrapper.createPacket(packet));
        }
    }

    private void sendCreate(Player player) {
        MoudPackets.MeshCreatePacket packet = new MoudPackets.MeshCreatePacket(
                meshId,
                meshData,
                new Vector3(position),
                new Vector3(rotation),
                new Vector3(scale)
        );
        player.sendPacket(ServerPacketWrapper.createPacket(packet));
    }

    private void broadcastCreate() {
        sendPacketToTargets(new MoudPackets.MeshCreatePacket(
                meshId,
                meshData,
                new Vector3(position),
                new Vector3(rotation),
                new Vector3(scale)
        ));
    }

    private void broadcastTransform() {
        sendPacketToTargets(new MoudPackets.MeshTransformPacket(
                meshId,
                new Vector3(position),
                new Vector3(rotation),
                new Vector3(scale)
        ));
    }

    private void broadcastRemove() {
        sendPacketToTargets(new MoudPackets.MeshRemovePacket(meshId));
    }

    @HostAccess.Export
    public long getMeshId() {
        return meshId;
    }

    @HostAccess.Export
    public void showToAll() {
        visibleToAll = true;
        viewers.clear();
        hiddenPlayers.clear();
        Collection<Player> players = MinecraftServer.getConnectionManager().getOnlinePlayers();
        for (Player player : players) {
            sendCreate(player);
        }
    }

    @HostAccess.Export
    public void hideFromAll() {
        broadcastRemove();
        visibleToAll = false;
        viewers.clear();
        hiddenPlayers.clear();
    }

    @HostAccess.Export
    public void showTo(PlayerProxy playerProxy) {
        UUID uuid = UUID.fromString(playerProxy.getUuid());
        hiddenPlayers.remove(uuid);
        viewers.add(uuid);
        Player player = findPlayer(uuid);
        if (player != null) {
            sendCreate(player);
        }
    }

    @HostAccess.Export
    public void hideFrom(PlayerProxy playerProxy) {
        UUID uuid = UUID.fromString(playerProxy.getUuid());
        viewers.remove(uuid);
        hiddenPlayers.add(uuid);
        Player player = findPlayer(uuid);
        if (player != null) {
            player.sendPacket(ServerPacketWrapper.createPacket(new MoudPackets.MeshRemovePacket(meshId)));
        }
    }

    private Player findPlayer(UUID uuid) {
        for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            if (player.getUuid().equals(uuid)) {
                return player;
            }
        }
        return null;
    }

    @HostAccess.Export
    public void setPosition(Vector3 position) {
        this.position = copyVector(position, this.position);
        broadcastTransform();
    }

    @HostAccess.Export
    public Vector3 getPosition() {
        return new Vector3(position);
    }

    @HostAccess.Export
    public void setRotation(Vector3 rotation) {
        this.rotation = copyVector(rotation, this.rotation);
        broadcastTransform();
    }

    @HostAccess.Export
    public Vector3 getRotation() {
        return new Vector3(rotation);
    }

    @HostAccess.Export
    public void setScale(Vector3 scale) {
        this.scale = copyVector(scale, this.scale);
        broadcastTransform();
    }

    @HostAccess.Export
    public Vector3 getScale() {
        return new Vector3(scale);
    }

    @HostAccess.Export
    public void setMesh(Mesh mesh) {
        this.meshData = MeshData.fromMesh(mesh);
        broadcastCreate();
    }

    @HostAccess.Export
    public void remove() {
        broadcastRemove();
        ALL_MESHES.remove(meshId);
        viewers.clear();
        hiddenPlayers.clear();
    }
}
