package com.meekdev.moud.mod.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.addon.Addons;
import com.meekdev.moud.mod.transport.Post;
import com.meekdev.moud.net.replicate.Applier;
import com.meekdev.moud.net.replicate.Change;
import com.meekdev.moud.net.wire.Codec;
import java.util.List;
import java.util.Queue;
import java.util.function.Consumer;
import com.meekdev.moud.mod.transport.payload.ResyncPayload;

public final class Mirror {

    private static final int BACKLOG = 2;

    private static final Applier APPLIER = new Applier(Addons.classes());

    private static boolean resyncing;

    private Mirror() {}

    public static Applier applier() {
        return APPLIER;
    }

    public static void apply(Consumer<Change> also) {
        Queue<byte[]> queue = Post.wired().deltas();
        int ticks = Math.max(1, queue.size() - BACKLOG + 1);
        for (int n = 0; n < ticks; n++) {
            byte[] packet = queue.poll();
            if (packet == null) return;
            List<Change> batch;
            try {
                batch = Codec.decode(packet, APPLIER.tree(), Addons.classes());
            } catch (RuntimeException broken) {
                if (!resyncing) {
                    MoudMod.LOG.warn("client tree out of sync ({}), requesting a full resync",
                            broken.getMessage());
                    resyncing = true;
                    if (ClientPlayNetworking.canSend(ResyncPayload.TYPE)) ClientPlayNetworking.send(new ResyncPayload());
                }
                queue.clear();
                return;
            }
            if (resyncing) {
                if (batch.isEmpty() || !(batch.getFirst() instanceof Change.Reset)) continue;
                resyncing = false;
            }
            for (int i = 0; i < batch.size(); i++) {
                APPLIER.apply(batch.get(i));
                also.accept(batch.get(i));
            }
        }
    }
}
