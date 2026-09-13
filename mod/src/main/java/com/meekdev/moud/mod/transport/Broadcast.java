package com.meekdev.moud.mod.transport;

import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.math.Vec3;
import com.meekdev.moud.mod.addon.Addons;
import com.meekdev.moud.mod.server.ServerScene;
import com.meekdev.moud.net.replicate.Audience;
import com.meekdev.moud.net.replicate.Change;
import com.meekdev.moud.net.replicate.Recorder;
import com.meekdev.moud.net.wire.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.Queue;
import java.util.function.Consumer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

// the server's side of the copy every client holds
//
// the tree is drained once and the tick is then cut up per player, because what changed is one
// question and who may hear it is another. a player who just joined hears all of it: the recorder only
// ever says what changed since the last time it was asked, so the state a place was already in when
// somebody arrived reaches them through their audience and nowhere else
public final class Broadcast {

    private static final Recorder RECORDER = new Recorder();

    private static final Map<UUID, Audience> AUDIENCES = new HashMap<>();

    // players whose copy went wrong, heard on the network thread and handled on the server's
    private static final Queue<UUID> RESYNC = new ConcurrentLinkedQueue<>();

    private Broadcast() {}

    public static void listen() {
        ServerPlayNetworking.registerGlobalReceiver(Packets.ResyncUp.TYPE, (payload, context) ->
                RESYNC.add(context.player().getUUID()));
    }

    // the server thread's one drain. dirty is cleared by it, so a second consumer that drained for
    // itself would find nothing -- everything that wants the tick reads the same batch
    public static void tick(MinecraftServer server, Consumer<Change> also) {
        if (!ServerScene.running()) return;
        // a fresh audience holds nothing, so its first drain resets the client and sends the whole place
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
            // where this player holds the place around them, which is where the player is
            Vec3 focus = new Vec3(player.getX(), player.getY(), player.getZ());
            audience.drain(tree, batch, focus, Audience.RADIUS, mine::add);
            // a quiet tick is sent too, and it is not waste: it is what tells a client that a tick
            // happened and nothing moved. without it a client paces on busy ticks alone
            Post.wired().sendDelta(player, Codec.encode(mine, tree, Addons.classes()));
        }
    }

    // a player who left holds nothing, and the copy they held is not something to keep around: they
    // come back through a fresh audience and hear the whole place again
    public static void forget(UUID player) {
        AUDIENCES.remove(player);
    }

    public static void stop() {
        AUDIENCES.clear();
    }
}
