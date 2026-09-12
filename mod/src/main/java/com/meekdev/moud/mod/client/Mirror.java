package com.meekdev.moud.mod.client;

import com.meekdev.moud.mod.addon.Addons;
import com.meekdev.moud.mod.server.ServerScene;
import com.meekdev.moud.net.replicate.Applier;
import com.meekdev.moud.net.replicate.Change;
import com.meekdev.moud.net.replicate.Recorder;
import com.meekdev.moud.net.wire.Codec;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

// the client renders its own copy, never the server's tree. sharing one would race: the place adds
// a hundred thousand parts on the server thread while the renderer walks the same list
//
// and the copy is made out of bytes even here, where the two sides are one process and handing the
// objects over would obviously work. §10.2 is explicit about it and it is the right call: a codec bug
// that only shows once the bytes are real is one nobody meets until the first time two people play
// together, which is far too late to be finding out that a colour does not survive the trip
public final class Mirror {

    // one entry per server tick, not one per change
    //
    // the two clocks are not locked, so a client tick sometimes finds two ticks' worth waiting and
    // sometimes none. draining whatever had arrived replayed two ticks of travel in one, and a
    // track then played a leg of twice the length over a single tick -- a part that jumps ahead
    // and settles back, on whatever frame the two clocks happened to slip. one tick in, one tick
    // out, and the slack sits in the queue instead of in what is drawn
    private static final int BACKLOG = 2;

    private static final Recorder RECORDER = new Recorder();
    private static final Queue<byte[]> QUEUE = new ConcurrentLinkedQueue<>();
    private static final Applier APPLIER = new Applier(Addons.classes());

    private Mirror() {}

    public static Applier applier() {
        return APPLIER;
    }

    // server thread: drain the authority once, then let every consumer read the same batch.
    // dirty is cleared by the drain, so a second consumer that drained for itself would see nothing
    public static void record(java.util.function.Consumer<Change> also) {
        if (!ServerScene.running()) return;
        List<Change> batch = new ArrayList<>();
        RECORDER.follow(ServerScene.tree(), batch::add);
        RECORDER.drain(batch::add);
        for (Change change : batch) also.accept(change);
        // a quiet tick is queued too, because it is what tells the client that a tick happened
        // and nothing moved. dropping it would let the queue pace on busy ticks alone
        QUEUE.add(Codec.encode(batch, ServerScene.tree(), Addons.classes()));
    }

    // client thread: everything the client tree ever sees is applied here, one server tick's worth
    // per client tick. a backlog past a couple of ticks is caught up rather than carried, or a
    // client that fell behind once would stay that far behind for ever
    public static void apply(java.util.function.Consumer<Change> also) {
        int ticks = Math.max(1, QUEUE.size() - BACKLOG + 1);
        for (int n = 0; n < ticks; n++) {
            byte[] packet = QUEUE.poll();
            if (packet == null) return;
            List<Change> batch = Codec.decode(packet, APPLIER.tree(), Addons.classes());
            for (int i = 0; i < batch.size(); i++) {
                APPLIER.apply(batch.get(i));
                also.accept(batch.get(i));
            }
        }
    }
}
