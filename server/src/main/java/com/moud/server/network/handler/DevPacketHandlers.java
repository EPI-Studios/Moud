package com.moud.server.network.handler;

import com.moud.network.MoudPackets.*;
import com.moud.server.logging.MoudLogger;
import net.minestom.server.entity.Player;


public final class DevPacketHandlers implements PacketHandlerGroup {
    private static final MoudLogger LOGGER = MoudLogger.getLogger(DevPacketHandlers.class);

    @Override
    public void register(PacketRegistry registry) {
        registry.register(PlayerAnimationsListPacket.class, this::handlePlayerAnimationsList);
    }

    private void handlePlayerAnimationsList(Player player, PlayerAnimationsListPacket packet) {
        if (packet.animationIds().isEmpty()) {
            player.sendMessage("§eNo PlayerAnim animations found.");
            player.sendMessage("§7Make sure you have animation files in your resource pack.");
            return;
        }

        player.sendMessage("§6=== PlayerAnim Animations (" + packet.animationIds().size() + ") ===");
        for (String animationId : packet.animationIds()) {
            player.sendMessage("§7- §f" + animationId);
        }

        LOGGER.info("Listed {} PlayerAnim animations for player {}",
                   packet.animationIds().size(), player.getUsername());
    }
}
