package com.meekdev.moud.mod.server.zone;

import com.meekdev.moud.core.character.Character;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.zone.ProximityPrompt;
import com.meekdev.moud.core.instance.Transforms;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.mod.adapter.physics.Physics;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import com.meekdev.moud.mod.transport.payload.PromptPayload;
import com.meekdev.moud.mod.server.ServerScene;

public final class ServerPrompts {

    private record Heard(UUID player, int prompt, int kind) {}

    private static final Queue<Heard> HEARD = new ConcurrentLinkedQueue<>();

    private static final double SLACK = 2;

    private ServerPrompts() {}

    public static void listen() {
        ServerPlayNetworking.registerGlobalReceiver(PromptPayload.TYPE, (payload, context) ->
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
                case PromptPayload.TRIGGERED -> prompt.triggered.fire(body);
                case PromptPayload.HOLD_BEGAN -> prompt.holdBegan.fire(body);
                case PromptPayload.HOLD_ENDED -> prompt.holdEnded.fire(body);
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
