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
import net.minecraft.world.entity.Entity;
import org.joml.Vector3d;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;
import java.util.Locale;

public final class ClientPhysics {

    private static final Colliders BOXES = new Colliders();
    private static final ColliderProvider PROVIDER = BOXES::collect;
    private static @Nullable ClientLevel attached;

    private ClientPhysics() {}

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

    public static String deckTrace(double x, double y, double z) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return "none\t\t\t\t\t\t\t\t\t\t\t";
        SubLevelEntity deck = SubLevelTracking.of(client.player);
        if (deck == null || deck.isRemoved()) return "none\t\t\t\t\t\t\t\t\t\t\t";

        float partialTick = client.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        SubLevelPose was = deck.previousPose().setOrigin(0, 0, 0);
        SubLevelPose is = deck.currentPose().setOrigin(0, 0, 0);
        SubLevelPose drawn = deck.renderPose(partialTick, new SubLevelPose()).setOrigin(0, 0, 0);
        Vector3d inBody = is.toLocal(x, y, z, new Vector3d());

        return deck.getId()
                + "\t" + fmt(was.x()) + "\t" + fmt(was.y()) + "\t" + fmt(was.z())
                + "\t" + fmt(yawOf(was)) + "\t" + fmt(yawOf(is)) + "\t" + fmt(yawOf(drawn))
                + "\t" + fmt(drawn.x()) + "\t" + fmt(drawn.y()) + "\t" + fmt(drawn.z())
                + "\t" + fmt(inBody.x) + "\t" + fmt(inBody.z);
    }

    public static String deckColumns() {
        return "deck\twasX\twasY\twasZ\twasYaw\tisYaw\tdrawnYaw\tdrawnX\tdrawnY\tdrawnZ"
                + "\tinBodyX\tinBodyZ";
    }

    private static double yawOf(SubLevelPose pose) {
        Vector3f forward = new Vector3f(1, 0, 0).rotate(new Quaternionf(pose.rotation()));
        return -Math.toDegrees(Math.atan2(-forward.z, forward.x));
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.6f", v);
    }

    public static Colliders boxes() {
        return BOXES;
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
