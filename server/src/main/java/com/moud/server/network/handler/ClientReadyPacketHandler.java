package com.moud.server.network.handler;

import com.moud.network.MoudPackets.*;
import com.moud.server.lighting.ServerLightingManager;
import com.moud.server.logging.LogContext;
import com.moud.server.logging.MoudLogger;
import com.moud.server.network.ServerNetworkManager;
import com.moud.server.particle.ParticleEmitterManager;
import com.moud.server.rendering.PostEffectStateManager;
import com.moud.server.primitives.PrimitiveServiceImpl;
import com.moud.server.ui.UIOverlayService;
import net.minestom.server.entity.Player;

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
    }
}
