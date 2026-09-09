package com.meekdev.moud.mod.adapter.physics;

import com.meekdev.bkun.Bkun;
import com.meekdev.bkun.box3d.LevelPhysics;
import com.meekdev.bkun.collision.ColliderProvider;
import com.meekdev.bkun.sublevel.SubLevelEntity;
import com.meekdev.bkun.sublevel.SubLevelIndex;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Part;
import com.meekdev.moud.core.instance.Transforms;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.net.replicate.Change;
import net.minecraft.client.multiplayer.ClientLevel;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

// bkun's collide mixin skips its providers for a ServerPlayer, because movement is client
// authoritative and the player's own client has already resolved it. so a part is only solid to
// the player if the client level carries the colliders too, fed from the mirror
public final class ClientPhysics {

    private static final Colliders BOXES = new Colliders();
    private static final ColliderProvider PROVIDER = BOXES::collect;
    private static @Nullable ClientLevel attached;

    private ClientPhysics() {}

    public static Colliders boxes() {
        return BOXES;
    }

    public static void apply(@Nullable InstanceTree tree, Change change) {
        if (tree == null) return;
        // the client predicts against its own baked copy of these boxes, so it has to be told
        // the set moved for the same reason the server does
        if (BOXES.apply(tree, change) && attached != null) {
            LevelPhysics physics = Bkun.physics(attached);
            if (physics != null) physics.invalidateProviders();
        }
        SubLevels.mirror(tree, change);
        drive(tree, change);
    }

    // the deck a part stands for is put where the part is, on the tick the part arrives
    //
    // its own pose reaches here through entity data, which the tracker has already broadcast by the
    // time the entity writes it, so it lands a tick late. the part does not: the mirror hands it
    // over in process. measured at 4.58 degrees behind on a deck turning at 1.6 rad/s, which is
    // exactly one tick, and half a body of deck standing where nothing is drawn
    private static void drive(InstanceTree tree, Change change) {
        ClientLevel level = attached;
        if (level == null) return;
        int id = switch (change) {
            case Change.Created created -> created.id();
            case Change.Wrote wrote -> wrote.id();
            case Change.Moved moved -> moved.id();
            case Change.Reset ignored -> -1;
            case Change.Destroyed ignored -> -1;
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

    // the level a client is in changes without a load event we can hold a provider across, so the
    // attachment is rechecked each frame and moved when it differs
    public static void attach(@Nullable ClientLevel level) {
        if (level == attached) return;
        if (attached != null) Bkun.collision(attached).removeProvider(PROVIDER);
        attached = level;
        if (level != null) Bkun.collision(level).addProvider(PROVIDER);
    }
}
