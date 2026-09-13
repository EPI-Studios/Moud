package com.meekdev.moud.mod.server;

import com.meekdev.moud.core.instance.Character;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.ProximityPrompt;
import com.meekdev.moud.core.instance.Transforms;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.mod.adapter.physics.Physics;
import com.meekdev.moud.mod.transport.Packets;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class ServerPrompts {

    private record Heard(UUID player, int prompt, int kind) {}

    private static final Queue<Heard> HEARD = new ConcurrentLinkedQueue<>();

    private static final double SLACK = 2;

    private ServerPrompts() {}

    public static void listen() {
        ServerPlayNetworking.registerGlobalReceiver(Packets.PromptUp.TYPE, (payload, context) ->
                HEARD.add(new Heard(context.player().getUUID(), payload.prompt(), payload.kind())));
    }

    public static void tick(MinecraftServer server) {
        InstanceTree tree = ServerScene.tree();
        for (Heard heard; (heard = HEARD.poll()) != null; ) {
            if (tree == null) continue;
            ServerPlayer player = server.getPlayerList().getPlayer(heard.player());
            Character body = player == null ? null : Physics.bodies().of(player, tree);
            if (body == null || !(tree.byId(heard.prompt()) instanceof ProximityPrompt prompt) || !prompt.enabled) continue;
            if (position(prompt).distance(Transforms.world(body).position()) > prompt.maxActivationDistance + SLACK) continue;
            switch (heard.kind()) {
                case Packets.PromptUp.TRIGGERED -> prompt.triggered.fire(body);
                case Packets.PromptUp.HOLD_BEGAN -> prompt.holdBegan.fire(body);
                case Packets.PromptUp.HOLD_ENDED -> prompt.holdEnded.fire(body);
                default -> {}
            }
        }
    }

    public static Vector3 position(ProximityPrompt prompt) {
        Instance parent = prompt.parent();
        Vector3 at = parent == null ? Vector3.ZERO : Transforms.world(parent).position();
        return at.add(prompt.offset);
    }
}
