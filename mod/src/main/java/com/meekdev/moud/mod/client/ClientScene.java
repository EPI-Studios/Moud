package com.meekdev.moud.mod.client;

import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.interp.Motion;
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

    public static void frame(double dt) {
        Mirror.apply();
        InstanceTree tree = tree();
        if (tree != null) MOTION.drain(tree, dt);
    }
}
