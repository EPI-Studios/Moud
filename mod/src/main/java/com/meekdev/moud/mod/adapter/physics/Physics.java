package com.meekdev.moud.mod.adapter.physics;

import com.meekdev.bkun.Bkun;
import com.meekdev.bkun.box3d.LevelPhysics;
import com.meekdev.bkun.collision.ColliderProvider;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.mod.server.ServerScene;
import com.meekdev.moud.mod.transport.Post;
import com.meekdev.moud.net.replicate.Change;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

public final class Physics {

    private static final Colliders BOXES = new Colliders();
    private static final SubLevels SHAPES = new SubLevels();
    private static final Characters BODIES = new Characters(BOXES);
    private static final Joints JOINTS = new Joints();
    private static @Nullable ServerLevel level;

    private Physics() {}

    public static @Nullable ServerLevel level() {
        return level;
    }

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
            if (unloaded == level) {
                JOINTS.clear();
                level = null;
            }
            Bkun.collision(unloaded).removeProvider(provider);
        });
    }

    public static void apply(InstanceTree tree, Change change, MinecraftServer server) {
        boolean statics = BOXES.apply(tree, change);
        SHAPES.apply(tree, change);
        BODIES.apply(tree, change, server);
        if (statics && level != null) {
            LevelPhysics physics = Bkun.physics(level);
            if (physics != null) physics.invalidateProviders();
        }
    }

    public static double gravity() {
        return Gravity.value();
    }

    public static final double DEFAULT_GRAVITY = Gravity.DEFAULT;

    public static void gravity(double metresPerSecondSquared) {
        Gravity.set(metresPerSecondSquared, level == null ? null : Bkun.physics(level));
        BODIES.refreshProfiles(ServerScene.server(), ServerScene.tree());
        Post.tellWorld(ServerScene.server(), Gravity.value());
    }

    public static void settle(@Nullable InstanceTree tree, boolean simulating) {
        if (BOXES.settle() && level != null) {
            LevelPhysics physics = Bkun.physics(level);
            if (physics != null) physics.invalidateProviders();
        }
        LevelPhysics physics = level == null ? null : Bkun.physics(level);
        JOINTS.settle(simulating ? tree : null, SHAPES, physics == null ? null : physics.world());
        SHAPES.simulating(simulating);
        SHAPES.settle();
    }
}
