package com.meekdev.moud.mod.server.zone;

import com.meekdev.moud.core.character.Character;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Transforms;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.zone.ProximityPrompt;
import com.meekdev.moud.mod.adapter.physics.Physics;
import com.meekdev.moud.mod.server.ServerScene;
import com.meekdev.moud.mod.transport.Budget;
import com.meekdev.moud.mod.transport.payload.PromptPayload;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class ServerPrompts {

    private record Heard(UUID player, int prompt, int kind, long at) {}

    private record Hold(int prompt, long since) {}

    private static final Queue<Heard> HEARD = new ConcurrentLinkedQueue<>();

    private static final double SLACK = 2;

    private static final double LATENCY = 0.25;

    private static final Budget BUDGET = new Budget(8);

    private static final Map<UUID, Hold> HOLDS = new HashMap<>();

    private ServerPrompts() {}

    public static void listen() {
        ServerPlayNetworking.registerGlobalReceiver(PromptPayload.TYPE, (payload, context) -> {
            if (BUDGET.take(context.player().getUUID())) HEARD.add(new Heard(context.player().getUUID(), payload.prompt(), payload.kind(), System.nanoTime()));
        });
    }

    public static void tick(MinecraftServer server) {
        InstanceTree tree = ServerScene.tree();
        BUDGET.refill();
        for (Heard heard; (heard = HEARD.poll()) != null; ) {
            if (tree == null) continue;
            ServerPlayer player = server.getPlayerList().getPlayer(heard.player());
            Character body = player == null ? null : Physics.bodies().of(player, tree);
            if (body == null || !(tree.byId(heard.prompt()) instanceof ProximityPrompt prompt) || !prompt.enabled) continue;
            if (position(prompt).distance(Transforms.world(body).position()) > prompt.maxActivationDistance + SLACK) continue;
            switch (heard.kind()) {
                case PromptPayload.TRIGGERED -> {
                    if (heldLongEnough(heard, prompt)) prompt.triggered.fire(body);
                }
                case PromptPayload.HOLD_BEGAN -> {
                    if (prompt.holdDuration <= 0) continue;
                    HOLDS.put(heard.player(), new Hold(heard.prompt(), heard.at()));
                    prompt.holdBegan.fire(body);
                }
                case PromptPayload.HOLD_ENDED -> {
                    Hold hold = HOLDS.get(heard.player());
                    if (hold == null || hold.prompt() != heard.prompt()) continue;
                    HOLDS.remove(heard.player());
                    prompt.holdEnded.fire(body);
                }
                default -> {}
            }
        }
    }

    private static boolean heldLongEnough(Heard heard, ProximityPrompt prompt) {
        if (prompt.holdDuration <= 0) return true;
        Hold hold = HOLDS.get(heard.player());
        if (hold == null || hold.prompt() != heard.prompt()) return false;
        HOLDS.remove(heard.player());
        return (heard.at() - hold.since()) / 1e9 >= prompt.holdDuration - LATENCY;
    }

    public static void left(UUID player) {
        HOLDS.remove(player);
    }

    public static void stop() {
        HOLDS.clear();
        HEARD.clear();
    }

    public static Vector3 position(ProximityPrompt prompt) {
        Instance parent = prompt.parent();
        Vector3 at = parent == null ? Vector3.ZERO : Transforms.world(parent).position();
        return at.add(prompt.offset);
    }
}
