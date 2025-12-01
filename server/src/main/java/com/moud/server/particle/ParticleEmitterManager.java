package com.moud.server.particle;

import com.moud.api.particle.ParticleEmitterConfig;
import com.moud.network.MoudPackets;
import com.moud.server.logging.LogContext;
import com.moud.server.logging.MoudLogger;
import com.moud.server.network.ServerNetworkManager;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public final class ParticleEmitterManager {
    private static final MoudLogger LOGGER = MoudLogger.getLogger(ParticleEmitterManager.class);
    private static final ParticleEmitterManager INSTANCE = new ParticleEmitterManager();

    private final Map<String, ParticleEmitterConfig> emitters = new ConcurrentHashMap<>();
    private ServerNetworkManager networkManager;

    private ParticleEmitterManager() {
    }

    public static ParticleEmitterManager getInstance() {
        return INSTANCE;
    }

    public void initialize(ServerNetworkManager networkManager) {
        this.networkManager = networkManager;
    }

    public void upsert(ParticleEmitterConfig config) {
        emitters.put(config.id(), config);
        broadcastUpsert(List.of(config));
    }

    public ParticleEmitterConfig get(String id) {
        if (id == null) {
            return null;
        }
        return emitters.get(id);
    }

    public void upsertAll(List<ParticleEmitterConfig> configs) {
        if (configs == null || configs.isEmpty()) {
            return;
        }
        for (ParticleEmitterConfig config : configs) {
            emitters.put(config.id(), config);
        }
        broadcastUpsert(configs);
    }

    public void remove(String id) {
        if (id == null || id.isBlank()) {
            return;
        }
        if (emitters.remove(id) != null) {
            broadcastRemove(List.of(id));
        }
    }

    public void syncToPlayer(Player player) {
        if (emitters.isEmpty() || networkManager == null || !networkManager.isMoudClient(player)) {
            return;
        }
        List<ParticleEmitterConfig> configs = new ArrayList<>(emitters.values());
        LOGGER.info(LogContext.builder()
                .put("player", player.getUsername())
                .put("emitters", configs.size())
                .build(), "Syncing particle emitters to player");
        networkManager.send(player, new MoudPackets.ParticleEmitterUpsertPacket(configs));
    }

    private void broadcastUpsert(List<ParticleEmitterConfig> configs) {
        if (networkManager == null || configs == null || configs.isEmpty()) {
            return;
        }
        List<ParticleEmitterConfig> snapshot = List.copyOf(configs);
        LogContext context = LogContext.builder()
                .put("count", snapshot.size())
                .build();
        LOGGER.debug(context, "Broadcasting particle emitter upsert");
        for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            if (networkManager.isMoudClient(player)) {
                networkManager.send(player, new MoudPackets.ParticleEmitterUpsertPacket(snapshot));
            }
        }
    }

    private void broadcastRemove(List<String> ids) {
        if (networkManager == null || ids == null || ids.isEmpty()) {
            return;
        }
        List<String> snapshot = List.copyOf(ids);
        LogContext context = LogContext.builder()
                .put("count", snapshot.size())
                .build();
        LOGGER.debug(context, "Broadcasting particle emitter removal");
        for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            if (networkManager.isMoudClient(player)) {
                networkManager.send(player, new MoudPackets.ParticleEmitterRemovePacket(snapshot));
            }
        }
    }
}
