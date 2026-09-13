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

// the client renders its own copy, never the server's tree. sharing one would race: the place adds
// a hundred thousand parts on the server thread while the renderer walks the same list
//
// and the copy is made out of bytes even here, where the two sides are one process and handing the
// objects over would obviously work. §10.2 is explicit about it and it is the right call: a codec bug
// that only shows once the bytes are real is one nobody meets until the first time two people play
// together, which is far too late to be finding out that a colour does not survive the trip
public final class Mirror {

    // how many ticks of slack the queue holds before it is caught up rather than played
    //
    // this is a jitter buffer and it is what §10.2 asks for -- "fed to an interpolation buffer with a
    // fixed delay". the two clocks are not locked and a connection does not deliver evenly, so a
    // client tick sometimes finds two ticks' worth waiting and sometimes none. draining whatever had
    // arrived replayed two ticks of travel in one, and a track then played a leg of twice the length
    // over a single tick: a part that jumps ahead and settles back, on whatever frame the two clocks
    // happened to slip
    //
    // so it costs a tick or two of delay on purpose, and buys evenness. one tick in, one tick out,
    // and the slack sits in the queue instead of in what is drawn
    private static final int BACKLOG = 2;

    private static final Applier APPLIER = new Applier(Addons.classes());

    private static boolean resyncing;

    private Mirror() {}

    public static Applier applier() {
        return APPLIER;
    }

    // client thread: everything the client tree ever sees is applied here, one server tick's worth
    // per client tick. a backlog past a couple of ticks is caught up rather than carried, or a
    // client that fell behind once would stay that far behind for ever
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
                // a tick this copy cannot read means it has drifted from the server's: every tick after it
                // would be read against the wrong tree too. the game keeps running, the queue is thrown away
                // and the server is asked for the whole place again
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
                // until the server starts over, whatever still arrives was cut for the old copy
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
