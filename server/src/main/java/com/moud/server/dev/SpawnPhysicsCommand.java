package com.moud.server.dev;

import com.moud.api.math.Quaternion;
import com.moud.api.math.Vector3;
import com.moud.server.physics.PhysicsService;
import com.moud.server.proxy.ModelProxy;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.command.CommandSender;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.command.builder.arguments.number.ArgumentDouble;
import net.minestom.server.entity.Player;

public final class SpawnPhysicsCommand extends Command {

    public SpawnPhysicsCommand() {
        super("spawnphysics", "sphys");

        var modelArg = ArgumentType.String("model");
        var widthArg = sizeArgument("width", 1.0);
        var heightArg = sizeArgument("height", 1.0);
        var depthArg = sizeArgument("depth", 1.0);
        var massArg = ArgumentType.Double("mass");
        massArg.setDefaultValue(() -> 5.0);
        var offsetXArg = ArgumentType.Double("offsetX");
        offsetXArg.setDefaultValue(() -> 0.0);
        var offsetYArg = ArgumentType.Double("offsetY");
        offsetYArg.setDefaultValue(() -> 1.0);
        var offsetZArg = ArgumentType.Double("offsetZ");
        offsetZArg.setDefaultValue(() -> 0.0);

        setDefaultExecutor((sender, context) ->
                spawn(sender, "moud:models/capsule.obj", 1.0, 1.0, 1.0, 5.0, 0.0, 1.0, 0.0));

        addSyntax((sender, ctx) -> spawn(
                        sender,
                        ctx.get(modelArg),
                        ctx.get(widthArg),
                        ctx.get(heightArg),
                        ctx.get(depthArg),
                        ctx.get(massArg),
                        ctx.get(offsetXArg),
                        ctx.get(offsetYArg),
                        ctx.get(offsetZArg)),
                modelArg, widthArg, heightArg, depthArg, massArg, offsetXArg, offsetYArg, offsetZArg);
    }

    private ArgumentDouble sizeArgument(String name, double def) {
        ArgumentDouble argument = ArgumentType.Double(name);
        argument.setDefaultValue(() -> def);
        return argument;
    }

    private void spawn(CommandSender sender,
                       String modelPath,
                       double width,
                       double height,
                       double depth,
                       double mass,
                       double offsetX,
                       double offsetY,
                       double offsetZ) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return;
        }
        if (player.getInstance() == null) {
            sender.sendMessage(Component.text("You must be inside an instance.", NamedTextColor.RED));
            return;
        }
        if (mass <= 0) {
            sender.sendMessage(Component.text("Mass must be positive.", NamedTextColor.RED));
            return;
        }

        Vector3 position = new Vector3(
                player.getPosition().x() + offsetX,
                player.getPosition().y() + offsetY,
                player.getPosition().z() + offsetZ
        );

        ModelProxy model = new ModelProxy(
                player.getInstance(),
                modelPath,
                position,
                Quaternion.identity(),
                Vector3.one(),
                null
        );
        model.setCollisionBox(width, height, depth);

        PhysicsService physics = PhysicsService.getInstance();
        physics.attachDynamicModel(
                model,
                new Vector3(width / 2.0, height / 2.0, depth / 2.0),
                (float) mass,
                null
        );

        sender.sendMessage(Component.text(
                "Spawned physics model #" + model.getId() + " (" + modelPath + ") at " +
                        String.format("%.2f %.2f %.2f", position.x, position.y, position.z),
                NamedTextColor.GREEN));
    }
}