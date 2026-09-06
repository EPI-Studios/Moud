package com.meekdev.moud.mod.client;

import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.interp.Motion;
import com.meekdev.moud.mod.adapter.physics.Colliders;
import org.jspecify.annotations.Nullable;

// the client side of the tree: a mirror of the server's, plus the state only rendering needs
public final class ClientScene {

    private static final Motion MOTION = new Motion();
    private static @Nullable Colliders colliders;
    private static @Nullable InstanceTree bound;

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

    public static @Nullable Colliders colliders() {
        return colliders;
    }

    // the mirror replaces its tree on a reload, so everything hanging off it follows
    public static void frame(double dt) {
        Mirror.apply();
        InstanceTree tree = tree();
        if (tree == null) return;
        if (tree != bound) {
            bound = tree;
            colliders = new Colliders(tree, MOTION);
        }
        MOTION.drain(tree, dt);
        colliders.sync();
    }
}
