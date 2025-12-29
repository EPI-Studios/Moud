package com.moud.server.dev;

import com.moud.server.instance.InstanceManager;
import net.minestom.server.command.builder.Command;
import net.minestom.server.entity.Player;

import java.nio.file.Path;

public class BackupWorldCommand extends Command {

    public BackupWorldCommand() {
        super("backup", "backupworld");

        setDefaultExecutor((sender, context) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("This command can only be used by players.");
                return;
            }

            player.sendMessage("§6Creating world backup...");

            InstanceManager instanceManager = InstanceManager.getInstance();
            if (instanceManager == null) {
                player.sendMessage("§cInstance manager not available!");
                return;
            }

            Path backupPath = instanceManager.createWorldBackup();
            if (backupPath != null) {
                player.sendMessage("§aBackup created successfully!");
                player.sendMessage("§7Location: §f" + backupPath);
            } else {
                player.sendMessage("§c✗ Failed to create backup. Check server logs for details.");
            }
        });
    }
}
