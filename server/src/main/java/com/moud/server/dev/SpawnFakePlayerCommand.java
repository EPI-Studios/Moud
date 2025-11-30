package com.moud.server.dev;

import com.moud.api.math.Quaternion;
import com.moud.api.math.Vector3;
import com.moud.network.MoudPackets;
import com.moud.server.fakeplayer.FakePlayerManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.command.CommandSender;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.command.builder.arguments.number.ArgumentDouble;
import net.minestom.server.entity.Player;

import java.util.List;

public final class SpawnFakePlayerCommand extends Command {

    public SpawnFakePlayerCommand() {
        super("spawnfakeplayer", "sfp");

        var labelArg = ArgumentType.String("label").setDefaultValue(() -> "Fake Player");
        ArgumentDouble xArg = (ArgumentDouble) ArgumentType.Double("x").setDefaultValue(() -> 0.0);
        ArgumentDouble yArg = (ArgumentDouble) ArgumentType.Double("y").setDefaultValue(() -> 65.0);
        ArgumentDouble zArg = (ArgumentDouble) ArgumentType.Double("z").setDefaultValue(() -> 0.0);

        setDefaultExecutor((sender, ctx) -> spawn(sender, "Fake Player",
                ctx.get(xArg), ctx.get(yArg), ctx.get(zArg)));

        addSyntax((sender, ctx) -> spawn(sender, ctx.get(labelArg),
                        ctx.get(xArg), ctx.get(yArg), ctx.get(zArg)),
                labelArg, xArg, yArg, zArg);
    }

    private void spawn(CommandSender sender, String label, double x, double y, double z) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return;
        }

        Vector3 pos = new Vector3(x, y, z);
        MoudPackets.FakePlayerDescriptor descriptor = new MoudPackets.FakePlayerDescriptor(
                0L,
                label,
                "",
                pos,
                Quaternion.identity(),
                0.6,
                1.8,
                false,
                false,
                false,
                false,
                false,
                List.of(),
                0.0,
                false,
                false
        );

        FakePlayerManager.getInstance().spawn(descriptor);
        sender.sendMessage(Component.text("Spawned fake player '" + label + "' at " +
                String.format("%.2f %.2f %.2f", x, y, z), NamedTextColor.GREEN));
    }
}
