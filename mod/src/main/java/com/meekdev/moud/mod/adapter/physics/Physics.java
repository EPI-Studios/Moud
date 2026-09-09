package com.meekdev.moud.mod.adapter.physics;

import com.meekdev.bkun.Bkun;
import com.meekdev.bkun.box3d.LevelPhysics;
import com.meekdev.bkun.collision.ColliderProvider;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.net.replicate.Change;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

// collision is the server's, because the server owns the tree and sub levels only exist there
public final class Physics {

    private static final Colliders BOXES = new Colliders();
    private static final SubLevels SHAPES = new SubLevels();
    private static final Characters BODIES = new Characters();
    private static @Nullable ServerLevel level;

    private Physics() {}

    public static Colliders boxes() {
        return BOXES;
    }

    public static SubLevels shapes() {
        return SHAPES;
    }

    public static Characters bodies() {
        return BODIES;
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

    public static void apply(InstanceTree tree, Change change, MinecraftServer server) {
        boolean statics = BOXES.apply(tree, change);
        SHAPES.apply(tree, change);
        BODIES.apply(tree, change, server);
        // the boxes a character sweeps against are baked into box3d and cached there, so a part
        // that moved has to say so or the world it collides with is the one from the first tick
        if (statics && level != null) {
            LevelPhysics physics = Bkun.physics(level);
            if (physics != null) physics.invalidateProviders();
        }
    }

    public static void settle() {
        SHAPES.settle();
    }
}
