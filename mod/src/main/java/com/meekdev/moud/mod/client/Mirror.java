package com.meekdev.moud.mod.client;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.mod.server.ServerScene;
import com.meekdev.moud.net.replicate.Applier;
import com.meekdev.moud.net.replicate.Change;
import com.meekdev.moud.net.replicate.Recorder;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

// the client renders its own copy, never the server's tree. sharing one would race: the place adds
// a hundred thousand parts on the server thread while the renderer walks the same list.
// this is the in process stand in for design 10 and it goes when the real transport lands
public final class Mirror {

    private static final Recorder RECORDER = new Recorder();
    private static final Queue<Change> QUEUE = new ConcurrentLinkedQueue<>();
    private static final Applier APPLIER = new Applier(Classes.registry());

    private Mirror() {}

    public static Applier applier() {
        return APPLIER;
    }

    // server thread: drain the authority once, then let every consumer read the same batch.
    // dirty is cleared by the drain, so a second consumer that drained for itself would see nothing
    public static void record(java.util.function.Consumer<Change> also) {
        if (!ServerScene.running()) return;
        Queue<Change> batch = new ArrayDeque<>();
        RECORDER.follow(ServerScene.tree(), batch::add);
        RECORDER.drain(batch::add);
        for (Change change : batch) also.accept(change);
        QUEUE.addAll(batch);
    }

    // client thread: everything the client tree ever sees is applied here, at one point in the frame
    public static void apply() {
        Change change;
        while ((change = QUEUE.poll()) != null) APPLIER.apply(change);
    }
}
