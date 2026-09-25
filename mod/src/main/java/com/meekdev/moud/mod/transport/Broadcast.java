package com.meekdev.moud.mod.transport;

import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.addon.Addons;
import com.meekdev.moud.core.place.PlaceConfig;
import com.meekdev.moud.mod.place.Place;
import com.meekdev.moud.mod.place.PlaceToml;
import com.meekdev.moud.mod.server.MoudServer;
import com.meekdev.moud.mod.server.ServerScene;
import com.meekdev.moud.mod.transport.payload.DeltaPayload;
import com.meekdev.moud.mod.transport.payload.ResyncPayload;
import com.meekdev.moud.net.replicate.Audience;
import com.meekdev.moud.net.replicate.Change;
import com.meekdev.moud.net.replicate.Recorder;
import com.meekdev.moud.net.wire.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class Broadcast {

    private static final Recorder RECORDER = new Recorder();

    private static final Map<UUID, Audience> AUDIENCES = new HashMap<>();

    private static final Queue<UUID> RESYNC = new ConcurrentLinkedQueue<>();

    private static final Map<UUID, Long> RESYNCED = new HashMap<>();

    private static final long RESYNC_MILLIS = 2000;

    private static final int PACKET = DeltaPayload.MAX_BYTES / 2;

    private Broadcast() {}

    public static void listen() {
        ServerPlayNetworking.registerGlobalReceiver(ResyncPayload.TYPE, (payload, context) ->
                RESYNC.add(context.player().getUUID()));
    }

    public static void tick(MinecraftServer server, Consumer<Change> also) {
        if (!ServerScene.running()) return;
        resync();
        InstanceTree tree = ServerScene.tree();
        List<Change> batch = new ArrayList<>();
        RECORDER.follow(tree, batch::add);
        RECORDER.drain(batch::add);
        for (Change change : batch) also.accept(change);

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Audience audience = AUDIENCES.computeIfAbsent(player.getUUID(),
                    id -> new Audience(id.toString()));
            List<Change> mine = new ArrayList<>();
            Vector3 focus = new Vector3(player.getX(), player.getY(), player.getZ());
            Place place = MoudServer.place();
            PlaceConfig.Streaming streaming = PlaceToml.config().streaming();
            boolean editing = place != null && place.editing();
            double radius = streaming.enabled() && !editing ? streaming.radius() : Double.MAX_VALUE;
            audience.drain(tree, batch, focus, radius, editing, mine::add);
            send(player, mine, tree);
        }
    }

    private static void resync() {
        long now = System.currentTimeMillis();
        List<UUID> later = new ArrayList<>();
        for (UUID player; (player = RESYNC.poll()) != null; ) {
            Long last = RESYNCED.get(player);
            if (last != null && now - last < RESYNC_MILLIS) {
                if (!later.contains(player)) later.add(player);
                continue;
            }
            RESYNCED.put(player, now);
            AUDIENCES.remove(player);
        }
        RESYNC.addAll(later);
    }

    private static void send(ServerPlayer player, List<Change> changes, InstanceTree tree) {
        byte[] bytes = Codec.encode(changes, tree, Addons.classes());
        if (bytes.length <= PACKET) {
            Post.wired().sendDelta(player, bytes);
            return;
        }
        List<Change> ordered = new ArrayList<>(changes.size());
        for (Change change : changes) if (!(change instanceof Change.Wrote)) ordered.add(change);
        for (Change change : changes) if (change instanceof Change.Wrote) ordered.add(change);
        split(player, ordered, tree);
    }

    private static void split(ServerPlayer player, List<Change> changes, InstanceTree tree) {
        byte[] bytes = Codec.encode(changes, tree, Addons.classes());
        if (bytes.length <= PACKET) {
            Post.wired().sendDelta(player, bytes);
            return;
        }
        if (changes.size() == 1) {
            MoudMod.LOG.warn("dropped a change of {} bytes to {}, it is over the packet limit", bytes.length, player.getGameProfile().name());
            return;
        }
        int half = changes.size() / 2;
        split(player, changes.subList(0, half), tree);
        split(player, changes.subList(half, changes.size()), tree);
    }

    public static void forget(UUID player) {
        AUDIENCES.remove(player);
        RESYNCED.remove(player);
    }

    public static void stop() {
        AUDIENCES.clear();
        RESYNCED.clear();
        RESYNC.clear();
    }
}
