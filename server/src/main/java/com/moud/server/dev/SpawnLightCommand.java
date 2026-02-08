package com.moud.server.dev;

import com.moud.api.math.Vector3;
import com.moud.server.lighting.ServerLightingManager;
import com.moud.server.permissions.PermissionManager;
import com.moud.server.permissions.ServerPermission;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.command.CommandSender;
import net.minestom.server.command.builder.Command;

import net.minestom.server.command.builder.arguments.ArgumentEnum;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.command.builder.arguments.number.ArgumentDouble;
import net.minestom.server.entity.Player;

import java.util.HashMap;
import java.util.Map;

public final class SpawnLightCommand extends Command {
    private enum LightType {
        POINT,
        AREA
    }

    public SpawnLightCommand() {
        super("spawnlight", "slight");

        ArgumentEnum<LightType> typeArg = ArgumentType.Enum("type", LightType.class);
        ArgumentDouble sizeArg = ArgumentType.Double("size");
        sizeArg.setDefaultValue(() -> 5.0);
        ArgumentDouble brightnessArg = ArgumentType.Double("brightness");
        brightnessArg.setDefaultValue(() -> 1.0);

        setDefaultExecutor((sender, context) -> spawnDefault(sender));

        addSyntax((sender, context) -> spawn(sender, context.get(typeArg), context.get(sizeArg), context.get(brightnessArg)),
                typeArg, sizeArg, brightnessArg);
        addSyntax((sender, context) -> spawn(sender, context.get(typeArg), context.get(sizeArg), 1.0),
                typeArg, sizeArg);
        addSyntax((sender, context) -> spawn(sender, context.get(typeArg), 5.0, 1.0), typeArg);
    }

    private void spawnDefault(CommandSender sender) {
        spawn(sender, LightType.POINT, 5.0, 1.0);
    }

    private void spawn(CommandSender sender, LightType type, double size, double brightness) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return;
        }
        if (!PermissionManager.getInstance().has(player, ServerPermission.DEV_UTILS)) {
            sender.sendMessage(Component.text("You do not have permission to use dev utilities.", NamedTextColor.RED));
            return;
        }

        ServerLightingManager manager = ServerLightingManager.getInstance();
        Vector3 position = new Vector3(player.getPosition().x(), player.getPosition().y() + 1.0, player.getPosition().z());

        Map<String, Object> properties = new HashMap<>();
        properties.put("x", position.x);
        properties.put("y", position.y);
        properties.put("z", position.z);
        properties.put("brightness", brightness);
        properties.put("r", 1.0);
        properties.put("g", 0.9);
        properties.put("b", 0.8);

        String typeName;
        if (type == LightType.AREA) {
            typeName = "area";
            properties.put("width", size);
            properties.put("height", size);
            properties.put("distance", Math.max(5.0, size * 2.0));
            properties.put("angle", 45.0);
            properties.put("dirX", 0.0);
            properties.put("dirY", -1.0);
            properties.put("dirZ", 0.0);
        } else {
            typeName = "point";
            properties.put("radius", size);
        }

        long id = manager.spawnLight(typeName, properties);
        sender.sendMessage(Component.text(
                "Spawned " + typeName + " light #" + id + " at " +
                        String.format("(%.2f, %.2f, %.2f)", position.x, position.y, position.z),
                NamedTextColor.AQUA));
    }
}
