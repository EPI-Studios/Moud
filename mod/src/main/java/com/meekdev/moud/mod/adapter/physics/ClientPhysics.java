package com.meekdev.moud.mod.adapter.physics;

import com.meekdev.bkun.Bkun;
import com.meekdev.bkun.box3d.LevelPhysics;
import com.meekdev.bkun.collision.ColliderProvider;
import com.meekdev.bkun.sublevel.SubLevelEntity;
import com.meekdev.bkun.sublevel.SubLevelIndex;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Transforms;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.part.Part;
import com.meekdev.moud.net.replicate.Change;
import net.minecraft.client.multiplayer.ClientLevel;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

public final class ClientPhysics {

    private static final Colliders BOXES = new Colliders();
    private static final ColliderProvider PROVIDER = BOXES::collect;
    private static @Nullable ClientLevel attached;

    private ClientPhysics() {}

    public static Colliders boxes() {
        return BOXES;
    }

    public static void settle() {
        if (BOXES.settle() && attached != null) {
            LevelPhysics physics = Bkun.physics(attached);
            if (physics != null) physics.invalidateProviders();
        }
    }

    public static void apply(@Nullable InstanceTree tree, Change change) {
        if (tree == null) return;
        if (BOXES.apply(tree, change) && attached != null) {
            LevelPhysics physics = Bkun.physics(attached);
            if (physics != null) physics.invalidateProviders();
        }
        SubLevels.mirror(tree, change);
        drive(tree, change);
    }

    private static void drive(InstanceTree tree, Change change) {
        ClientLevel level = attached;
        if (level == null) return;
        int id = switch (change) {
            case Change.Created created -> created.id();
            case Change.Wrote wrote -> wrote.id();
            case Change.Moved moved -> moved.id();
            case Change.Reset ignored -> -1;
            case Change.Destroyed ignored -> -1;
            case Change.Tagged ignored -> -1;
        };
        if (id < 0 || !(tree.byId(id) instanceof Part part) || !SubLevels.wantsSubLevel(part)) return;

        CFrame world = Transforms.world(part);
        Quat r = world.rotation();
        for (SubLevelEntity deck : SubLevelIndex.in(level)) {
            if (deck.owner() != id) continue;
            deck.drivePose(world.position().x(), world.position().y(), world.position().z(),
                    new Quaternionf((float) r.x(), (float) r.y(), (float) r.z(), (float) r.w()));
            return;
        }
    }

    public static void attach(@Nullable ClientLevel level) {
        if (level == attached) return;
        if (attached != null) Bkun.collision(attached).removeProvider(PROVIDER);
        attached = level;
        if (level != null) Bkun.collision(level).addProvider(PROVIDER);
    }
}
