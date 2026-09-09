package com.meekdev.moud.mod.client;

import com.meekdev.bkun.sublevel.SubLevelEntity;
import com.meekdev.bkun.sublevel.SubLevelIndex;
import com.meekdev.bkun.sublevel.SubLevelPose;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Part;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.mod.MoudMod;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;

// what is drawn against what is collided with, for the same part, on the same frame
//
// the part is drawn from the mirrored tree and the deck under a player is the sub level entity's
// pose, and the two travel by different routes. everything else about a rider is downstream of
// them agreeing, so the number worth having is how far apart they are
final class Divergence {

    private double elapsed;

    void tick(double dt, float partialTick) {
        Level level = Minecraft.getInstance().level;
        InstanceTree tree = ClientScene.tree();
        if (level == null || tree == null) {
            elapsed = 0;
            return;
        }
        elapsed += dt;
        if (elapsed < 1.0) return;
        elapsed = 0;

        // why a fall beside a deck is slow: whether anything is telling the player they landed,
        // and whether gravity is still building. a y that sits at one value instead of growing is
        // something zeroing it every tick
        var player = Minecraft.getInstance().player;
        if (player != null) {
            MoudMod.LOG.info("fall onGround={} dY={} fallDistance={} riding={}",
                    player.onGround(), String.format("%.4f", player.getDeltaMovement().y),
                    String.format("%.2f", player.fallDistance),
                    com.meekdev.bkun.sublevel.SubLevelTracking.of(player) != null);
        }

        for (SubLevelEntity platform : SubLevelIndex.in(level)) {
            SubLevelPose drawn = platform.renderPose(partialTick, new SubLevelPose());
            Part nearest = nearestPart(tree, drawn.x(), drawn.y(), drawn.z());
            if (nearest == null) continue;

            CFrame part = ClientScene.motion().sample(nearest, partialTick);
            Quat deck = quatOf(drawn);
            MoudMod.LOG.info("diverge deck=({}) part=({}) dPos={} dYaw={}deg",
                    fmt(deck), fmt(part.rotation()),
                    String.format("%.4f", part.position().distance(
                            new com.meekdev.moud.core.math.Vec3(drawn.x(), drawn.y(), drawn.z()))),
                    String.format("%.2f", Math.toDegrees(yaw(part.rotation()) - yaw(deck))));
        }
    }

    private static Part nearestPart(InstanceTree tree, double x, double y, double z) {
        Part best = null;
        double closest = 4.0;
        for (Instance instance : tree.ofClass(Classes.PART)) {
            if (!(instance instanceof Part part)) continue;
            double d = part.cframe.position().distance(new com.meekdev.moud.core.math.Vec3(x, y, z));
            if (d < closest) {
                closest = d;
                best = part;
            }
        }
        return best;
    }

    private static Quat quatOf(SubLevelPose pose) {
        return new Quat(pose.rotation().x(), pose.rotation().y(),
                pose.rotation().z(), pose.rotation().w());
    }

    private static double yaw(Quat q) {
        return Math.atan2(2.0 * (q.w() * q.y() + q.x() * q.z()),
                1.0 - 2.0 * (q.y() * q.y() + q.x() * q.x()));
    }

    private static String fmt(Quat q) {
        return String.format("%.3f %.3f %.3f %.3f", q.x(), q.y(), q.z(), q.w());
    }
}
