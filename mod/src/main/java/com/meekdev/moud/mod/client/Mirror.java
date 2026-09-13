package com.meekdev.moud.mod.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import com.meekdev.moud.mod.transport.Packets;
import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.addon.Addons;
import com.meekdev.moud.mod.transport.Post;
import com.meekdev.moud.net.replicate.Applier;
import com.meekdev.moud.net.replicate.Change;
import com.meekdev.moud.net.wire.Codec;
import java.util.List;

public final class Mirror {

    private static final int BACKLOG = 2;

    private static final Applier APPLIER = new Applier(Addons.classes());

    private static boolean resyncing;

    private Mirror() {}

    public static Applier applier() {
        return APPLIER;
    }

    public static void apply(java.util.function.Consumer<Change> also) {
        java.util.Queue<byte[]> queue = Post.wired().deltas();
        int ticks = Math.max(1, queue.size() - BACKLOG + 1);
        for (int n = 0; n < ticks; n++) {
            byte[] packet = queue.poll();
            if (packet == null) return;
            List<Change> batch;
            try {
                batch = Codec.decode(packet, APPLIER.tree(), Addons.classes());
            } catch (RuntimeException broken) {
                if (!resyncing) {
                    MoudMod.LOG.warn("the copy of the place drifted from the server's ({}), asking for all of it again",
                            broken.getMessage());
                    resyncing = true;
                    if (ClientPlayNetworking.canSend(Packets.ResyncUp.TYPE)) ClientPlayNetworking.send(new Packets.ResyncUp());
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
