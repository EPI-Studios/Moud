package com.moud.server.dev;

import com.moud.api.math.Vector3;
import com.moud.server.physics.PhysicsService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.command.builder.arguments.number.ArgumentDouble;
import net.minestom.server.entity.Player;

public final class PhysicsExplosionCommand extends Command {

    public PhysicsExplosionCommand() {
        super("physboom", "explosion");

        ArgumentDouble radiusArg = ArgumentType.Double("radius");
        radiusArg.setDefaultValue(() -> 8.0);
        ArgumentDouble strengthArg = ArgumentType.Double("strength");
        strengthArg.setDefaultValue(() -> 200.0);
        ArgumentDouble verticalArg = ArgumentType.Double("verticalBoost");
        verticalArg.setDefaultValue(() -> 1.0);

        addSyntax((sender, ctx) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
                return;
            }
            double radius = Math.max(0.1, ctx.get(radiusArg));
            double strength = ctx.get(strengthArg);
            double verticalBoost = ctx.get(verticalArg);
            Vector3 center = new Vector3(
                    player.getPosition().x(),
                    player.getEyeHeight() + player.getPosition().y(),
                    player.getPosition().z()
            );
            int affected = PhysicsService.getInstance().applyExplosion(center, radius, strength, verticalBoost);
            sender.sendMessage(Component.text(
                    String.format("(r=%.2f, strength=%.2f, up=%.2f) -> %d bodies", radius, strength, verticalBoost, affected),
                    NamedTextColor.GREEN
            ));
        }, radiusArg, strengthArg, verticalArg);
    }
}
