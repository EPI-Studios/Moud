package com.moud.server.dev;

import com.moud.network.MoudPackets;
import com.moud.server.network.ServerNetworkManager;
import net.minestom.server.command.builder.Command;
import net.minestom.server.entity.Player;


public class ListAnimationsCommand extends Command {

    public ListAnimationsCommand() {
        super("listanims", "animations", "lsanims");

        setDefaultExecutor((sender, context) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("This command can only be used by players.");
                return;
            }

            player.sendMessage("§6Requesting PlayerAnim animation list from client...");

            ServerNetworkManager networkManager = ServerNetworkManager.getInstance();
            if (networkManager != null) {
                networkManager.send(player, new MoudPackets.RequestPlayerAnimationsPacket());
            } else {
                player.sendMessage("§cNetwork manager not available!");
            }
        });
    }
}
