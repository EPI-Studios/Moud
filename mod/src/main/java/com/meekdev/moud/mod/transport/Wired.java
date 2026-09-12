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

// the carrier that goes over the game's own connection
//
// this is the one that runs, in a solo game as much as on somebody else's server. §10.2 allows a local
// server to hand the objects over in memory instead, and that is the wrong reading of it now that the
// codec exists: the integrated server already talks to its client down a connection, so taking that
// same path costs a memory copy and removes the entire class of bug that only shows up the first time
// two people play together. the in memory carrier stays, as what the tests run on
//
// the rate limit is counted here, on receive, and that is the whole point of moving it: it used to be
// counted where a delivery was sent, which on the way up is the client -- so a client that had been
// tampered with simply did not count. a limit that the far side enforces is a limit
public final class Wired implements Transport {

    private static final Logger LOGGER = LoggerFactory.getLogger("moud/wired");

    // what a client sent, waiting for the server's tick
    private record Sent(UUID from, int remote, byte[] args) {}

    private final Queue<Sent> up = new ConcurrentLinkedQueue<>();
    private final Queue<Packets.Down> down = new ConcurrentLinkedQueue<>();
    private final Queue<byte[]> deltas = new ConcurrentLinkedQueue<>();

    // per player per channel, cleared by the drain. the server drains once a tick, so a count since
    // the last drain *is* a per tick rate
    private final Map<UUID, Map<Integer, Integer>> arrived = new HashMap<>();

    // said once rather than per delivery, or a client that floods also floods the log
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

    // the server's side of the tree stream. one payload per tick per player, which is what the client
    // paces on: a quiet tick is sent too, because it is what says a tick happened and nothing moved
    public void sendDelta(ServerPlayer player, byte[] bytes) {
        ServerPlayNetworking.send(player, new Packets.Delta(bytes));
    }

    // and the client's side of it, drained on the client tick
    public Queue<byte[]> deltas() {
        return deltas;
    }

    @Override
    public void toServer(int remote, List<Object> args, boolean reliable) {
        // reliable is not a choice here: the connection is ordered and lossless either way. it stays
        // in the signature because the carrier the tests run on honours it, and because a future
        // channel of our own would
        ClientPlayNetworking.send(new Packets.Up(remote, Args.encode(args)));
    }

    @Override
    public void toClient(String player, int remote, List<Object> args, boolean reliable) {
        ServerPlayer who = playerOf(player);
        if (who == null) return;
        ServerPlayNetworking.send(who, new Packets.Down(remote, Args.encode(args)));
    }

    @Override
    public void toAllClients(int remote, List<Object> args, boolean reliable) {
        MinecraftServer server = ServerScene.server();
        if (server == null) return;
        // encoded once for everybody, because the bytes are the same bytes
        Packets.Down payload = new Packets.Down(remote, Args.encode(args));
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(player, payload);
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
            // a packet that does not decode is a client sending something we did not write. it is
            // dropped and named, never allowed to throw into the tick
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

    // a queue that is never drained is a leak, and the one case is a client that loaded a place and
    // then left before a tick ran
    public void forget() {
        up.clear();
        down.clear();
        deltas.clear();
        arrived.clear();
        flooding.clear();
    }
}
