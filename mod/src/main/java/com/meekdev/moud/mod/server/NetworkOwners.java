package com.meekdev.moud.mod.server;

import com.meekdev.moud.core.character.Character;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.instance.Transforms;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.part.Part;
import com.meekdev.moud.mod.adapter.physics.Joints;
import com.meekdev.moud.mod.adapter.physics.Physics;
import com.meekdev.moud.mod.transport.payload.OwnedPosePayload;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

public final class NetworkOwners {

    private static final PropertyDef OWNER = Classes.PART.property("networkOwner");
    private static final PropertyDef CFRAME = Classes.SPATIAL.property("cframe");
    private static final PropertyDef VELOCITY = Classes.PART.property("velocity");
    private static final PropertyDef ANGULAR_VELOCITY = Classes.PART.property("angularVelocity");

    private static final double TAKE = 10;
    private static final double KEEP = 14;
    private static final double REACH = 64;
    private static final double FASTEST = 400;
    private static final int FIRST_REPORT_TICKS = 100;
    private static final int QUIET_TICKS = 40;
    private static final int TAKEN_BACK_TICKS = 100;

    private record Report(String player, OwnedPosePayload payload) {}

    private static final Queue<Report> REPORTS = new ConcurrentLinkedQueue<>();

    private NetworkOwners() {}

    public static void listen() {
        ServerPlayNetworking.registerGlobalReceiver(OwnedPosePayload.TYPE, (payload, context) ->
                REPORTS.add(new Report(context.player().getUUID().toString(), payload)));
    }

    static void tick(MinecraftServer server, @Nullable InstanceTree tree, boolean editing) {
        if (tree == null) {
            REPORTS.clear();
            return;
        }
        if (editing) {
            REPORTS.clear();
            for (Part part : List.copyOf(tree.ofClass(Classes.PART))) {
                part.ownershipSet(false);
                if (!part.networkOwner.isEmpty()) Instances.setObj(part, OWNER, "");
            }
            return;
        }
        Map<String, Vector3> players = new HashMap<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Character body = Physics.bodies().of(player, tree);
            if (body != null && body.isAlive()) players.put(player.getUUID().toString(), Transforms.world(body).position());
        }
        for (Part part : List.copyOf(tree.ofClass(Classes.PART))) decide(part, players);
        for (Report report = REPORTS.poll(); report != null; report = REPORTS.poll()) take(tree, report, players);
    }

    public static boolean ownable(Part part) {
        return part.isAlive() && !part.anchored && part.collides && !Instance.outOfWorld(part)
                && !(part.parent() instanceof Character) && !Joints.holds(part.id());
    }

    private static void decide(Part part, Map<String, Vector3> players) {
        String owner = part.networkOwner;
        if (!ownable(part) || Physics.shapes().body(part.id()) == null) {
            if (!owner.isEmpty()) write(part, "");
            if (part.anchored || !part.isAlive()) part.ownershipSet(false);
            return;
        }
        boolean silent = part.ownerSilence() > (part.simulatedRemotely() ? QUIET_TICKS : FIRST_REPORT_TICKS);
        if (part.ownershipSet()) {
            if (silent) part.ownerChanged();
            if (!owner.isEmpty() && !players.containsKey(owner)) {
                part.ownershipSet(false);
                write(part, "");
            }
            return;
        }
        if (silent) {
            part.holdForServer(TAKEN_BACK_TICKS);
            write(part, "");
            return;
        }
        if (part.heldForServer()) {
            if (!owner.isEmpty()) write(part, "");
            return;
        }
        Vector3 at = Transforms.world(part).position();
        Vector3 holder = players.get(owner);
        if (holder != null && holder.sub(at).lengthSq() <= KEEP * KEEP) return;
        String nearest = "";
        double best = TAKE * TAKE;
        for (Map.Entry<String, Vector3> player : players.entrySet()) {
            double away = player.getValue().sub(at).lengthSq();
            if (away <= best) {
                best = away;
                nearest = player.getKey();
            }
        }
        if (!nearest.equals(owner)) write(part, nearest);
    }

    public static void write(Part part, String owner) {
        if (part.networkOwner.equals(owner)) return;
        part.ownerChanged();
        Instances.setObj(part, OWNER, owner);
    }

    private static void take(InstanceTree tree, Report report, Map<String, Vector3> players) {
        Vector3 body = players.get(report.player());
        if (body == null) return;
        for (OwnedPosePayload.Pose pose : report.payload().poses()) {
            if (!(tree.byId(pose.id()) instanceof Part part) || !part.simulatedBy(report.player())) continue;
            Vector3 position = new Vector3(pose.x(), pose.y(), pose.z());
            Vector3 speed = new Vector3(pose.vx(), pose.vy(), pose.vz());
            Vector3 spin = new Vector3(pose.wx(), pose.wy(), pose.wz());
            double turn = Math.sqrt(pose.qx() * pose.qx() + pose.qy() * pose.qy() + pose.qz() * pose.qz() + pose.qw() * pose.qw());
            if (!finite(position) || !finite(speed) || !finite(spin) || !(turn > 0.5 && turn < 2)) continue;
            if (position.sub(body).lengthSq() > REACH * REACH || speed.lengthSq() > FASTEST * FASTEST) continue;
            Quat rotation = new Quat(pose.qx() / turn, pose.qy() / turn, pose.qz() / turn, pose.qw() / turn);
            Instances.setObj(part, CFRAME, Transforms.localFor(part, new CFrame(position, rotation)));
            Instances.setObj(part, VELOCITY, speed);
            Instances.setObj(part, ANGULAR_VELOCITY, spin);
            part.ownerHeard();
        }
    }

    private static boolean finite(Vector3 v) {
        return Double.isFinite(v.x()) && Double.isFinite(v.y()) && Double.isFinite(v.z());
    }
}
