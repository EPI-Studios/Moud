package com.meekdev.moud.mod.adapter.physics;

import com.meekdev.bkun.Bkun;
import com.meekdev.bkun.box3d.LevelPhysics;
import com.meekdev.bkun.collision.ColliderProvider;
import com.meekdev.bkun.sublevel.SubLevelEntity;
import com.meekdev.bkun.sublevel.SubLevelIndex;
import com.meekdev.bkun.sublevel.SubLevelPose;
import com.meekdev.bkun.sublevel.SubLevelTracking;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Part;
import com.meekdev.moud.core.instance.Transforms;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.net.replicate.Change;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.Minecraft;
import org.joml.Vector3f;
import com.meekdev.moud.core.math.Vec3;
import net.minecraft.world.entity.Entity;
import org.joml.Vector3d;
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

    // the heading the deck under a player is drawn at, in degrees, or NaN when it is standing on
    // nothing that moves
    //
    // the deck's own pose, read where the frame reads it. a rider's camera is turned from this, so a
    // shudder in it is a shudder in the view -- and the two are worth telling apart, because one is
    // the platform arriving unevenly and the other is the turn being applied wrongly
    public static double riddenYaw() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return Double.NaN;
        SubLevelEntity deck = SubLevelTracking.of(client.player);
        if (deck == null || deck.isRemoved()) return Double.NaN;
        float partialTick = client.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        Quaternionf turn = new Quaternionf(
                deck.renderPose(partialTick, new SubLevelPose()).rotation());
        Vector3f forward = new Vector3f(1, 0, 0).rotate(turn);
        return -Math.toDegrees(Math.atan2(-forward.z, forward.x));
    }

    // the difference between the arc a deck carries a rider along and the straight line between
    // where it was and where it is
    //
    // a tick's two positions are two points on a circle, and interpolating between them cuts the
    // corner. the error is zero at both ends of a tick and worst in the middle, by about
    // r * (1 - cos(w/2)) -- so it appears and vanishes twenty times a second, which is a body that
    // will not sit still
    //
    // bkun already does this for the rider's camera. the camera was therefore travelling the arc
    // while the body it belongs to travelled the chord, and the two disagreed by that much all the
    // way through every tick: in first person the camera is the thing you judge everything else
    // against, so it is the body that looks like it is shaking. in third person our own camera is
    // interpolated the same straight way the body is, they agree, and the shake vanishes -- which
    // is exactly what sneaking did
    public static Vec3 deckArc(@Nullable Entity rider, Vec3 was, Vec3 is, float partialTick) {
        if (rider == null) return Vec3.ZERO;
        SubLevelEntity deck = SubLevelTracking.of(rider);
        if (deck == null || deck.isRemoved()) return Vec3.ZERO;

        // all three have to name one frame or the round trip does not cancel
        SubLevelPose before = deck.previousPose().setOrigin(0, 0, 0);
        SubLevelPose now = deck.currentPose().setOrigin(0, 0, 0);
        SubLevelPose drawn = deck.renderPose(partialTick, new SubLevelPose()).setOrigin(0, 0, 0);

        Vector3d from = before.toLocal(was.x(), was.y(), was.z(), new Vector3d());
        Vector3d to = now.toLocal(is.x(), is.y(), is.z(), new Vector3d());
        from.lerp(to, partialTick);
        Vector3d arc = drawn.toWorld(from.x, from.y, from.z, new Vector3d());

        Vec3 chord = was.lerp(is, partialTick);
        return new Vec3(arc.x - chord.x(), arc.y - chord.y(), arc.z - chord.z());
    }

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
