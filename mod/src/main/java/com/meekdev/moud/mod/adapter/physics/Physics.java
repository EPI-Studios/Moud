package com.meekdev.moud.mod.adapter.physics;

import com.meekdev.bkun.Bkun;
import com.meekdev.bkun.collision.ColliderProvider;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.net.replicate.Change;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

// collision is the server's, because the server owns the tree and sub levels only exist there
public final class Physics {

    private static final Colliders BOXES = new Colliders();
    private static final SubLevels SHAPES = new SubLevels();
    private static @Nullable ServerLevel level;

    private Physics() {}

    public static Colliders boxes() {
        return BOXES;
    }

    public static SubLevels shapes() {
        return SHAPES;
    }

    public static void install() {
        ColliderProvider provider = BOXES::collect;
        ServerLevelEvents.LOAD.register((server, loaded) -> {
            level = loaded;
            SHAPES.level(loaded);
            Bkun.collision(loaded).addProvider(provider);
        });
        ServerLevelEvents.UNLOAD.register((server, unloaded) -> {
            if (unloaded == level) level = null;
            Bkun.collision(unloaded).removeProvider(provider);
        });
    }

    public static void apply(InstanceTree tree, Change change) {
        BOXES.apply(tree, change);
        SHAPES.apply(tree, change);
    }

    public static void settle() {
        SHAPES.settle();
    }
}
