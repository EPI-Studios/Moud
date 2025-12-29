package com.moud.server.network.handler;

import com.moud.network.MoudPackets.ClientReadyPacket;
import com.moud.network.MoudPackets.ZoneDefinition;
import com.moud.network.MoudPackets.ZoneSyncPacket;
import com.moud.server.MoudEngine;
import com.moud.server.lighting.ServerLightingManager;
import com.moud.server.logging.LogContext;
import com.moud.server.logging.MoudLogger;
import com.moud.server.movement.PlayerMovementSimService;
import com.moud.server.network.ServerNetworkManager;
import com.moud.server.particle.ParticleEmitterManager;
import com.moud.server.rendering.PostEffectStateManager;
import com.moud.server.primitives.PrimitiveServiceImpl;
import com.moud.server.ui.UIOverlayService;
import com.moud.server.zone.Zone;
import com.moud.server.zone.ZoneManager;
import net.minestom.server.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class ClientReadyPacketHandler implements PacketHandlerGroup {
    private static final MoudLogger LOGGER = MoudLogger.getLogger(ClientReadyPacketHandler.class);

    private final ServerNetworkManager networkManager;

    public ClientReadyPacketHandler(ServerNetworkManager networkManager) {
        this.networkManager = networkManager;
    }

    @Override
    public void register(PacketRegistry registry) {
        registry.register(ClientReadyPacket.class, this::handleClientReady);
    }

    private void handleClientReady(Player player, ClientReadyPacket packet) {
        if (!networkManager.isMoudClient(player)) {
            return;
        }
        LogContext context = LogContext.builder()
                .put("player", player.getUsername())
                .build();
        LOGGER.info(context, "Client {} is ready, syncing lights and particle emitters", player.getUsername());

        ServerLightingManager.getInstance().syncLightsToPlayer(player);
        ParticleEmitterManager.getInstance().syncToPlayer(player);
        PostEffectStateManager.getInstance().syncToPlayer(player);
        UIOverlayService.getInstance().resend(player);
        PrimitiveServiceImpl.getInstance().syncToPlayer(player);

        ZoneManager zones = MoudEngine.getInstance().getZoneManager();
        if (zones != null) {
            List<Zone> existing = zones.getZones();
            List<ZoneDefinition> defs = new ArrayList<>(existing.size());
            for (Zone zone : existing) {
                if (zone == null || zone.getId() == null || zone.getId().isBlank()) {
                    continue;
                }
                defs.add(new ZoneDefinition(zone.getId(), zone.getMin(), zone.getMax()));
            }
            networkManager.send(player, new ZoneSyncPacket(defs));
        } else {
            networkManager.send(player, new ZoneSyncPacket(List.of()));
        }
        PlayerMovementSimService.getInstance().flushClientMode(player);
    }
}
