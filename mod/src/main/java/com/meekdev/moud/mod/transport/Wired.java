package com.meekdev.moud.mod.transport;

import com.meekdev.moud.mod.server.ServerScene;
import com.meekdev.moud.mod.transport.payload.CallDownPayload;
import com.meekdev.moud.mod.transport.payload.CallUpPayload;
import com.meekdev.moud.mod.transport.payload.DeltaPayload;
import com.meekdev.moud.mod.transport.payload.PlaceReloadedPayload;
import com.meekdev.moud.mod.transport.payload.RemoteDownPayload;
import com.meekdev.moud.mod.transport.payload.RemoteUpPayload;
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
import java.util.concurrent.atomic.AtomicLong;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Wired implements Transport {

    private static final Logger LOGGER = LoggerFactory.getLogger("moud/wired");

    private record Sent(UUID from, int remote, byte[] args) {}

    private record Called(UUID from, CallUpPayload payload) {}

    @FunctionalInterface
    public interface CallSink {
        void deliver(String player, int kind, int remote, int call, List<Object> values);
    }

    private final Queue<Sent> up = new ConcurrentLinkedQueue<>();
    private final Queue<RemoteDownPayload> down = new ConcurrentLinkedQueue<>();
    private final Queue<Called> callsUp = new ConcurrentLinkedQueue<>();
    private final Queue<CallDownPayload> callsDown = new ConcurrentLinkedQueue<>();
    private final Queue<byte[]> deltas = new ConcurrentLinkedQueue<>();
    private final Queue<Long> reloads = new ConcurrentLinkedQueue<>();
    private final AtomicLong received = new AtomicLong();

    private final Map<UUID, Map<Integer, Integer>> arrived = new HashMap<>();

    private final Map<UUID, Integer> flooding = new HashMap<>();

    public void listen() {
        ServerPlayNetworking.registerGlobalReceiver(RemoteUpPayload.TYPE, (payload, context) ->
                up.add(new Sent(context.player().getUUID(), payload.remote(), payload.args())));
        ServerPlayNetworking.registerGlobalReceiver(CallUpPayload.TYPE, (payload, context) ->
                callsUp.add(new Called(context.player().getUUID(), payload)));
    }

    public void listenAsClient() {
        ClientPlayNetworking.registerGlobalReceiver(RemoteDownPayload.TYPE,
                (payload, context) -> down.add(payload));
        ClientPlayNetworking.registerGlobalReceiver(CallDownPayload.TYPE,
                (payload, context) -> callsDown.add(payload));
        ClientPlayNetworking.registerGlobalReceiver(DeltaPayload.TYPE, (payload, context) -> {
            received.incrementAndGet();
            deltas.add(payload.bytes());
        });
        ClientPlayNetworking.registerGlobalReceiver(PlaceReloadedPayload.TYPE, (payload, context) -> reloads.add(received.get()));
    }

    public void sendDelta(ServerPlayer player, byte[] bytes) {
        if (!ServerPlayNetworking.canSend(player, DeltaPayload.TYPE)) return;
        ServerPlayNetworking.send(player, new DeltaPayload(bytes));
    }

    public Queue<byte[]> deltas() {
        return deltas;
    }

    public long received() {
        return received.get();
    }

    public Queue<Long> reloads() {
        return reloads;
    }

    public void sendReloaded(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (ServerPlayNetworking.canSend(player, PlaceReloadedPayload.TYPE)) ServerPlayNetworking.send(player, new PlaceReloadedPayload());
        }
    }

    @Override
    public void toServer(int remote, List<Object> args, boolean reliable) {
        ClientPlayNetworking.send(new RemoteUpPayload(remote, Args.encode(args)));
    }

    @Override
    public void toClient(String player, int remote, List<Object> args, boolean reliable) {
        ServerPlayer who = playerOf(player);
        if (who == null || !ServerPlayNetworking.canSend(who, RemoteDownPayload.TYPE)) return;
        ServerPlayNetworking.send(who, new RemoteDownPayload(remote, Args.encode(args)));
    }

    @Override
    public void toAllClients(int remote, List<Object> args, boolean reliable) {
        MinecraftServer server = ServerScene.server();
        if (server == null) return;
        RemoteDownPayload payload = new RemoteDownPayload(remote, Args.encode(args));
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (ServerPlayNetworking.canSend(player, RemoteDownPayload.TYPE)) {
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
                    LOGGER.warn("{} sent more than {} messages on one remote this tick, dropping the rest", one.from(), InProcess.PER_TICK);
                }
                continue;
            }
            try {
                sink.deliver(one.from().toString(), one.remote(), Args.decode(one.args()));
            } catch (RuntimeException e) {
                LOGGER.warn("dropped a delivery from {}: {}", one.from(), e.toString());
            }
        }
    }

    @Override
    public void drainClient(ClientSink sink) {
        for (RemoteDownPayload one : take(down)) {
            try {
                sink.deliver(one.remote(), Args.decode(one.args()));
            } catch (RuntimeException e) {
                LOGGER.warn("dropped a delivery: {}", e.toString());
            }
        }
    }

    public void callServer(int kind, int remote, int call, List<Object> values) {
        if (!ClientPlayNetworking.canSend(CallUpPayload.TYPE)) return;
        ClientPlayNetworking.send(new CallUpPayload(kind, remote, call, Args.encode(values)));
    }

    public void callClient(String player, int kind, int remote, int call, List<Object> values) {
        ServerPlayer who = playerOf(player);
        if (who == null || !ServerPlayNetworking.canSend(who, CallDownPayload.TYPE)) return;
        ServerPlayNetworking.send(who, new CallDownPayload(kind, remote, call, Args.encode(values)));
    }

    public void drainCallsServer(CallSink sink) {
        Map<UUID, Map<Integer, Integer>> counted = new HashMap<>();
        for (Called one : take(callsUp)) {
            CallUpPayload payload = one.payload();
            int already = counted.computeIfAbsent(one.from(), id -> new HashMap<>()).merge(payload.remote(), 1, Integer::sum);
            if (already > InProcess.PER_TICK) continue;
            try {
                sink.deliver(one.from().toString(), payload.kind(), payload.remote(), payload.call(), Args.decode(payload.values()));
            } catch (RuntimeException e) {
                LOGGER.warn("dropped a call from {}: {}", one.from(), e.toString());
            }
        }
    }

    public void drainCallsClient(CallSink sink) {
        for (CallDownPayload one : take(callsDown)) {
            try {
                sink.deliver("", one.kind(), one.remote(), one.call(), Args.decode(one.values()));
            } catch (RuntimeException e) {
                LOGGER.warn("dropped a call: {}", e.toString());
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
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public void forget() {
        up.clear();
        down.clear();
        callsUp.clear();
        callsDown.clear();
        deltas.clear();
        arrived.clear();
        flooding.clear();
    }
}
