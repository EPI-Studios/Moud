package com.meekdev.moud.mod.client;

import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.interp.Motion;
import com.meekdev.moud.mod.adapter.physics.ClientPhysics;
import org.jspecify.annotations.Nullable;

// the client side of the tree: a mirror of the server's, plus the state only rendering needs
public final class ClientScene {

    private static final Motion MOTION = new Motion();

    private ClientScene() {}

    public static @Nullable InstanceTree tree() {
        return Mirror.applier().tree();
    }

    public static @Nullable Instance world() {
        return Mirror.applier().world();
    }

    public static Motion motion() {
        return MOTION;
    }

    // the mirror is drained on the tick, because that is the clock the changes are produced on.
    // draining it per frame meant two ticks could land in one frame and none in the next, and the
    // interpolation then measured the render loop instead of the stream
    public static void tick() {
        Mirror.apply(change -> ClientPhysics.apply(tree(), change));
        InstanceTree tree = tree();
        if (tree != null) MOTION.drain(tree);
    }

    public static void frame() {
        ClientPhysics.attach(net.minecraft.client.Minecraft.getInstance().level);
    }
}
