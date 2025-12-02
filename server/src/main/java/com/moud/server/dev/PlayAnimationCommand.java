package com.moud.server.dev;

import com.moud.network.MoudPackets;
import com.moud.server.camera.CameraService;
import com.moud.server.editor.AnimationManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.command.CommandSender;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentString;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.entity.Player;

/**
 * Plays an animation clip and optionally snaps the caller into a given camera object.
 * Usage: /playanim <animationId> [cameraId]
 */
public final class PlayAnimationCommand extends Command {
    public PlayAnimationCommand() {
        super("playanim", "panim");

        ArgumentString animationId = ArgumentType.String("animationId");
        ArgumentString cameraId = ArgumentType.String("cameraId");
        cameraId.setDefaultValue(() -> "");

        setDefaultExecutor((sender, context) -> sender.sendMessage(Component.text(
                "Usage: /playanim <animationId> [cameraId]", NamedTextColor.RED)));

        addSyntax((sender, context) -> execute(sender, context.get(animationId), context.get(cameraId)),
                animationId, cameraId);
        addSyntax((sender, context) -> execute(sender, context.get(animationId), ""),
                animationId);
    }

    private void execute(CommandSender sender, String animationId, String cameraId) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return;
        }
        if (animationId == null || animationId.isBlank()) {
            sender.sendMessage(Component.text("Animation id required.", NamedTextColor.RED));
            return;
        }

        // Trigger animation playback server-side.
        AnimationManager.getInstance().handlePlay(new MoudPackets.AnimationPlayPacket(animationId, false, 1f));
        sender.sendMessage(Component.text("Playing animation " + animationId, NamedTextColor.GREEN));

        // Optionally snap the player into the provided camera object.
        if (cameraId != null && !cameraId.isBlank()) {
            boolean ok = CameraService.getInstance().setPlayerCamera(player, cameraId);
            if (!ok) {
                sender.sendMessage(Component.text("Camera not found: " + cameraId, NamedTextColor.YELLOW));
            } else {
                sender.sendMessage(Component.text("Switched to camera " + cameraId, NamedTextColor.GREEN));
            }
        }
    }
}
