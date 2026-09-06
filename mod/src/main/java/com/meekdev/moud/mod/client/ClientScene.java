package com.meekdev.moud.mod.client;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.interp.Motion;
import com.meekdev.moud.mod.adapter.physics.Colliders;

public final class ClientScene {

    private static final InstanceTree TREE = new InstanceTree();
    private static final Motion MOTION = new Motion();
    private static final Colliders COLLIDERS = new Colliders(TREE, MOTION);
    private static Instance world;

    private ClientScene() {}

    public static InstanceTree tree() {
        return TREE;
    }

    public static Instance world() {
        return world;
    }

    public static Motion motion() {
        return MOTION;
    }

    public static Colliders colliders() {
        return COLLIDERS;
    }

    public static void start() {
        if (world == null) world = Instances.createRoot(TREE, Classes.SPATIAL, "World");
    }
}
