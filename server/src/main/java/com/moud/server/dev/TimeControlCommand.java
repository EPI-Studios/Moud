package com.moud.server.dev;

import com.moud.server.instance.InstanceManager;
import com.moud.server.permissions.PermissionManager;
import com.moud.server.permissions.ServerPermission;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.command.CommandSender;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentLiteral;

import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.command.builder.arguments.number.ArgumentInteger;
import net.minestom.server.command.builder.arguments.number.ArgumentLong;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;

public final class TimeControlCommand extends Command {

    public TimeControlCommand() {
        super("timecontrol", "time");

        ArgumentLiteral infoLiteral = ArgumentType.Literal("info");
        ArgumentLiteral setLiteral = ArgumentType.Literal("set");
        ArgumentLiteral rateLiteral = ArgumentType.Literal("rate");
        ArgumentLiteral syncLiteral = ArgumentType.Literal("sync");

        ArgumentLong timeArg = ArgumentType.Long("time");
        ArgumentInteger rateValueArg = ArgumentType.Integer("rate-value");
        ArgumentInteger syncArg = ArgumentType.Integer("ticks");

        setDefaultExecutor((sender, context) -> showInfo(sender));

        addSyntax((sender, context) -> showInfo(sender), infoLiteral);
        addSyntax((sender, context) -> handleSet(sender, context.get(timeArg)), setLiteral, timeArg);
        addSyntax((sender, context) -> handleRate(sender, context.get(rateValueArg)), rateLiteral, rateValueArg);
        addSyntax((sender, context) -> handleSync(sender, context.get(syncArg)), syncLiteral, syncArg);
    }

    private void handleSet(CommandSender sender, long time) {
        if (!ensureAllowed(sender)) {
            return;
        }
        Instance instance = targetInstance(sender);
        if (instance == null) {
            sender.sendMessage(Component.text("No active instance to modify time.", NamedTextColor.RED));
            return;
        }
        instance.setTime(time);
        sender.sendMessage(Component.text("Set time to " + time, NamedTextColor.GREEN));
    }

    private void handleRate(CommandSender sender, int rate) {
        if (!ensureAllowed(sender)) {
            return;
        }
        if (rate < 0) {
            sender.sendMessage(Component.text("Rate must be zero or positive.", NamedTextColor.RED));
            return;
        }
        Instance instance = targetInstance(sender);
        if (instance == null) {
            sender.sendMessage(Component.text("No active instance to modify rate.", NamedTextColor.RED));
            return;
        }
        instance.setTimeRate(rate);
        sender.sendMessage(Component.text("Set time rate to " + rate, NamedTextColor.GREEN));
    }

    private void handleSync(CommandSender sender, int ticks) {
        if (!ensureAllowed(sender)) {
            return;
        }
        if (ticks < 0) {
            sender.sendMessage(Component.text("Sync ticks must be zero or positive.", NamedTextColor.RED));
            return;
        }
        Instance instance = targetInstance(sender);
        if (instance == null) {
            sender.sendMessage(Component.text("No active instance to modify synchronization.", NamedTextColor.RED));
            return;
        }
        instance.setTimeSynchronizationTicks(ticks);
        sender.sendMessage(Component.text("Set time synchronization ticks to " + ticks, NamedTextColor.GREEN));
    }

    private void showInfo(CommandSender sender) {
        if (!ensureAllowed(sender)) {
            return;
        }
        Instance instance = targetInstance(sender);
        if (instance == null) {
            sender.sendMessage(Component.text("No instance available. Join the server first.", NamedTextColor.RED));
            return;
        }
        long time = instance.getTime();
        int rate = instance.getTimeRate();
        int sync = instance.getTimeSynchronizationTicks();
        sender.sendMessage(Component.text(
                String.format("Time: %d | Rate: %d | Sync Ticks: %d", time, rate, sync),
                NamedTextColor.AQUA));
    }

    private boolean ensureAllowed(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            return true;
        }
        if (PermissionManager.getInstance().has(player, ServerPermission.DEV_UTILS)) {
            return true;
        }
        sender.sendMessage(Component.text("You do not have permission to use dev utilities.", NamedTextColor.RED));
        return false;
    }

    private Instance targetInstance(CommandSender sender) {
        if (sender instanceof Player player) {
            return player.getInstance();
        }
        return InstanceManager.getInstance().getDefaultInstance();
    }
}
