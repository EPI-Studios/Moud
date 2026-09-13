package com.meekdev.moud.mod.server;

import com.meekdev.moud.core.character.Character;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.nav.Walkers;
import com.meekdev.moud.mod.adapter.physics.Physics;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import com.meekdev.moud.mod.transport.payload.PilotDownPayload;
import com.meekdev.moud.mod.transport.payload.PilotUpPayload;

public final class ServerPilot implements Walkers.Pilot {

    public static final ServerPilot INSTANCE = new ServerPilot();

    private static final Queue<UUID> CANCELLED = new ConcurrentLinkedQueue<>();

    private ServerPilot() {}

    public static void listen() {
        ServerPlayNetworking.registerGlobalReceiver(PilotUpPayload.TYPE, (payload, context) ->
                CANCELLED.add(context.player().getUUID()));
    }

    public static void tick(MinecraftServer server) {
        InstanceTree tree = ServerScene.tree();
        for (UUID id; (id = CANCELLED.poll()) != null; ) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            Character body = player == null || tree == null ? null : Physics.bodies().of(player, tree);
            if (body != null) Walkers.cancelled(body);
        }
    }

    @Override
    public void walk(Character body, List<Vector3> waypoints) {
        double[] flat = new double[waypoints.size() * 3];
        for (int n = 0; n < waypoints.size(); n++) {
            flat[n * 3] = waypoints.get(n).x();
            flat[n * 3 + 1] = waypoints.get(n).y();
            flat[n * 3 + 2] = waypoints.get(n).z();
        }
        send(body, new PilotDownPayload(PilotDownPayload.WALK, flat));
    }

    @Override
    public void jump(Character body) {
        send(body, new PilotDownPayload(PilotDownPayload.JUMP, new double[0]));
    }

    @Override
    public void stop(Character body) {
        send(body, new PilotDownPayload(PilotDownPayload.STOP, new double[0]));
    }

    private static void send(Character body, PilotDownPayload payload) {
        MinecraftServer server = ServerScene.server();
        if (server == null) return;
        try {
            ServerPlayer player = server.getPlayerList().getPlayer(UUID.fromString(body.owner));
            if (player != null && ServerPlayNetworking.canSend(player, PilotDownPayload.TYPE)) {
                ServerPlayNetworking.send(player, payload);
            }
        } catch (IllegalArgumentException notAPlayer) {
        }
    }
}
