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
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Wired implements Transport {

    private static final Logger LOGGER = LoggerFactory.getLogger("moud/wired");

    static final int INVOKE = 0;
    static final int ANSWERED = 1;
    static final int FAILED = 2;

    private record Sent(UUID from, int remote, byte[] args) {}

    private record Called(UUID from, CallUpPayload payload) {}

    private record Heard<T>(long after, T payload) {}

    private record Out(@Nullable UUID to, CustomPacketPayload payload) {}

    private static final int QUEUED_PER_TICK = 256;

    @FunctionalInterface
    public interface CallSink {
        void deliver(String player, int kind, int remote, int call, List<Object> values);
    }

    private final Queue<Sent> up = new ConcurrentLinkedQueue<>();
    private final Queue<Heard<RemoteDownPayload>> down = new ConcurrentLinkedQueue<>();
    private final Queue<Called> callsUp = new ConcurrentLinkedQueue<>();
    private final Queue<Heard<CallDownPayload>> callsDown = new ConcurrentLinkedQueue<>();
    private final List<Out> outgoing = new ArrayList<>();
    private final Budget queued = new Budget(QUEUED_PER_TICK);
    private final Queue<byte[]> deltas = new ConcurrentLinkedQueue<>();
    private final Queue<Long> reloads = new ConcurrentLinkedQueue<>();
    private final AtomicLong received = new AtomicLong();

    private final Map<UUID, Map<Integer, Integer>> arrived = new HashMap<>();

    private final Map<UUID, Integer> flooding = new HashMap<>();

    public void listen() {
        ServerPlayNetworking.registerGlobalReceiver(RemoteUpPayload.TYPE, (payload, context) -> {
            if (queued.take(context.player().getUUID())) up.add(new Sent(context.player().getUUID(), payload.remote(), payload.args()));
        });
        ServerPlayNetworking.registerGlobalReceiver(CallUpPayload.TYPE, (payload, context) -> {
            if (queued.take(context.player().getUUID())) callsUp.add(new Called(context.player().getUUID(), payload));
        });
    }

    public void listenAsClient() {
        ClientPlayNetworking.registerGlobalReceiver(RemoteDownPayload.TYPE,
                (payload, context) -> down.add(new Heard<>(received.get(), payload)));
        ClientPlayNetworking.registerGlobalReceiver(CallDownPayload.TYPE,
                (payload, context) -> callsDown.add(new Heard<>(received.get(), payload)));
        ClientPlayNetworking.registerGlobalReceiver(DeltaPayload.TYPE, (payload, context) -> {
            received.incrementAndGet();
            deltas.add(payload.bytes());
        });
        ClientPlayNetworking.registerGlobalReceiver(PlaceReloadedPayload.TYPE, (payload, context) -> reloads.add(received.get()));
    }

    public void forgetClient() {
        down.clear();
        callsDown.clear();
        deltas.clear();
        reloads.clear();
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
        byte[] bytes = encode(args, RemoteUpPayload.MOST);
        if (ClientPlayNetworking.canSend(RemoteUpPayload.TYPE)) ClientPlayNetworking.send(new RemoteUpPayload(remote, bytes));
    }

    @Override
    public void toClient(String player, int remote, List<Object> args, boolean reliable) {
        byte[] bytes = encode(args, RemoteDownPayload.MOST);
        ServerPlayer who = playerOf(player);
        if (who != null) outgoing.add(new Out(who.getUUID(), new RemoteDownPayload(remote, bytes)));
    }

    @Override
    public void toAllClients(int remote, List<Object> args, boolean reliable) {
        outgoing.add(new Out(null, new RemoteDownPayload(remote, encode(args, RemoteDownPayload.MOST))));
    }

    public void flush(MinecraftServer server) {
        if (outgoing.isEmpty()) return;
        List<Out> batch = List.copyOf(outgoing);
        outgoing.clear();
        for (Out one : batch) {
            if (one.to() == null) {
                for (ServerPlayer player : server.getPlayerList().getPlayers()) send(player, one.payload());
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(one.to());
            if (player != null) send(player, one.payload());
        }
    }

    private static void send(ServerPlayer player, CustomPacketPayload payload) {
        if (ServerPlayNetworking.canSend(player, payload.type())) ServerPlayNetworking.send(player, payload);
    }

    private static byte[] encode(List<Object> args, int most) {
        byte[] bytes = Args.encode(args);
        if (bytes.length > most) {
            throw new IllegalArgumentException("the message is " + bytes.length / 1000 + " KB, the limit is " + most / 1000 + " KB");
        }
        return bytes;
    }

    @Override
    public void drainServer(ServerSink sink) {
        List<Sent> batch = take(up);
        queued.refill();
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
        drainClient(Long.MAX_VALUE, sink);
    }

    public void drainClient(long applied, ClientSink sink) {
        for (RemoteDownPayload one : upTo(down, applied)) {
            try {
                sink.deliver(one.remote(), Args.decode(one.args()));
            } catch (RuntimeException e) {
                LOGGER.warn("dropped a delivery: {}", e.toString());
            }
        }
    }

    public void callServer(int kind, int remote, int call, List<Object> values) {
        byte[] bytes = encode(values, CallUpPayload.MOST);
        if (ClientPlayNetworking.canSend(CallUpPayload.TYPE)) ClientPlayNetworking.send(new CallUpPayload(kind, remote, call, bytes));
    }

    public void callClient(String player, int kind, int remote, int call, List<Object> values) {
        byte[] bytes = encode(values, CallDownPayload.MOST);
        ServerPlayer who = playerOf(player);
        if (who != null) outgoing.add(new Out(who.getUUID(), new CallDownPayload(kind, remote, call, bytes)));
    }

    public void drainCallsServer(CallSink sink) {
        Map<UUID, Map<Integer, Integer>> counted = new HashMap<>();
        for (Called one : take(callsUp)) {
            CallUpPayload payload = one.payload();
            if (overBudget(counted, one.from(), payload.kind(), payload.remote())) {
                callClient(one.from().toString(), FAILED, payload.remote(), payload.call(),
                        List.of("too many calls to this remote function at once, the server dropped this one"));
                continue;
            }
            try {
                sink.deliver(one.from().toString(), payload.kind(), payload.remote(), payload.call(), Args.decode(payload.values()));
            } catch (RuntimeException e) {
                LOGGER.warn("dropped a call from {}: {}", one.from(), e.toString());
            }
        }
    }

    static boolean overBudget(Map<UUID, Map<Integer, Integer>> counted, UUID from, int kind, int remote) {
        if (kind != INVOKE) return false;
        return counted.computeIfAbsent(from, id -> new HashMap<>()).merge(remote, 1, Integer::sum) > InProcess.PER_TICK;
    }

    public void drainCallsClient(long applied, CallSink sink) {
        for (CallDownPayload one : upTo(callsDown, applied)) {
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

    private static <T> List<T> upTo(Queue<Heard<T>> from, long applied) {
        List<T> batch = new ArrayList<>();
        for (Heard<T> one = from.peek(); one != null && one.after() <= applied; one = from.peek()) {
            from.poll();
            batch.add(one.payload());
        }
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
        callsUp.clear();
        outgoing.clear();
        queued.refill();
        arrived.clear();
        flooding.clear();
    }
}
