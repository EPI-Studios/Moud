package com.meekdev.moud.mod.transport;

import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.mod.addon.Addons;
import com.meekdev.moud.mod.server.ServerScene;
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

    private Broadcast() {}

    public static void listen() {
        ServerPlayNetworking.registerGlobalReceiver(ResyncPayload.TYPE, (payload, context) ->
                RESYNC.add(context.player().getUUID()));
    }

    public static void tick(MinecraftServer server, Consumer<Change> also) {
        if (!ServerScene.running()) return;
        for (UUID player; (player = RESYNC.poll()) != null; ) AUDIENCES.remove(player);
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
            audience.drain(tree, batch, focus, Audience.RADIUS, mine::add);
            Post.wired().sendDelta(player, Codec.encode(mine, tree, Addons.classes()));
        }
    }

    public static void forget(UUID player) {
        AUDIENCES.remove(player);
    }

    public static void stop() {
        AUDIENCES.clear();
    }
}
