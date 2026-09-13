package com.meekdev.moud.mod.transport;

import com.meekdev.moud.mod.server.ServerScene;
import com.meekdev.moud.net.transport.InProcess;
import com.meekdev.moud.net.transport.Transport;
import com.meekdev.moud.net.wire.Args;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Wired implements Transport {

    private static final Logger LOGGER = LoggerFactory.getLogger("moud/wired");

    private record Sent(UUID from, int remote, byte[] args) {}

    private final Queue<Sent> up = new ConcurrentLinkedQueue<>();
    private final Queue<Packets.Down> down = new ConcurrentLinkedQueue<>();
    private final Queue<byte[]> deltas = new ConcurrentLinkedQueue<>();

    private final Map<UUID, Map<Integer, Integer>> arrived = new HashMap<>();

    private final Map<UUID, Integer> flooding = new HashMap<>();

    public void listen() {
        ServerPlayNetworking.registerGlobalReceiver(Packets.Up.TYPE, (payload, context) ->
                up.add(new Sent(context.player().getUUID(), payload.remote(), payload.args())));
    }

    public void listenAsClient() {
        ClientPlayNetworking.registerGlobalReceiver(Packets.Down.TYPE,
                (payload, context) -> down.add(payload));
        ClientPlayNetworking.registerGlobalReceiver(Packets.Delta.TYPE,
                (payload, context) -> deltas.add(payload.bytes()));
    }

    public void sendDelta(ServerPlayer player, byte[] bytes) {
        if (!ServerPlayNetworking.canSend(player, Packets.Delta.TYPE)) return;
        ServerPlayNetworking.send(player, new Packets.Delta(bytes));
    }

    public Queue<byte[]> deltas() {
        return deltas;
    }

    @Override
    public void toServer(int remote, List<Object> args, boolean reliable) {
        ClientPlayNetworking.send(new Packets.Up(remote, Args.encode(args)));
    }

    @Override
    public void toClient(String player, int remote, List<Object> args, boolean reliable) {
        ServerPlayer who = playerOf(player);
        if (who == null || !ServerPlayNetworking.canSend(who, Packets.Down.TYPE)) return;
        ServerPlayNetworking.send(who, new Packets.Down(remote, Args.encode(args)));
    }

    @Override
    public void toAllClients(int remote, List<Object> args, boolean reliable) {
        MinecraftServer server = ServerScene.server();
        if (server == null) return;
        Packets.Down payload = new Packets.Down(remote, Args.encode(args));
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (ServerPlayNetworking.canSend(player, Packets.Down.TYPE)) {
                ServerPlayNetworking.send(player, payload);
            }
        }
    }

    @Override
    public void drainServer(ServerSink sink) {
        List<Sent> batch = take(up);
        arrived.clear();
        for (Sent one : batch) {
            int already = arrived.computeIfAbsent(one.from(), id -> new HashMap<>())
                    .merge(one.remote(), 1, Integer::sum);
            if (already > InProcess.PER_TICK) {
                if (flooding.merge(one.from(), 1, Integer::sum) == 1) {
                    LOGGER.warn("{} is sending down one channel faster than {} a tick, dropping"
                            + " the rest of the tick", one.from(), InProcess.PER_TICK);
                }
                continue;
            }
            try {
                sink.deliver(one.from().toString(), one.remote(), Args.decode(one.args()));
            } catch (RuntimeException bad) {
                LOGGER.warn("dropped a delivery from {}: {}", one.from(), bad.toString());
            }
        }
    }

    @Override
    public void drainClient(ClientSink sink) {
        for (Packets.Down one : take(down)) {
            try {
                sink.deliver(one.remote(), Args.decode(one.args()));
            } catch (RuntimeException bad) {
                LOGGER.warn("dropped a delivery: {}", bad.toString());
            }
        }
    }

    private static <T> List<T> take(Queue<T> from) {
        if (from.isEmpty()) return List.of();
        List<T> batch = new ArrayList<>();
        for (T one = from.poll(); one != null; one = from.poll()) batch.add(one);
        return batch;
    }

    private static ServerPlayer playerOf(String uuid) {
        MinecraftServer server = ServerScene.server();
        if (server == null || uuid.isEmpty()) return null;
        try {
            return server.getPlayerList().getPlayer(UUID.fromString(uuid));
        } catch (IllegalArgumentException notAPlayer) {
            return null;
        }
    }

    public void forget() {
        up.clear();
        down.clear();
        deltas.clear();
        arrived.clear();
        flooding.clear();
    }
}
