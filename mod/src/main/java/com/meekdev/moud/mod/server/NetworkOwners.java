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
import com.meekdev.moud.core.character.Humanoid;
import com.meekdev.moud.core.part.Seat;
import com.meekdev.moud.mod.adapter.physics.Assemblies;
import com.meekdev.moud.mod.adapter.physics.PartBodies;
import com.meekdev.moud.mod.adapter.physics.Physics;
import com.meekdev.moud.mod.transport.payload.OwnedPosePayload;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
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
        Set<String> connected = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            connected.add(player.getUUID().toString());
            Character body = Physics.bodies().of(player, tree);
            if (body != null && body.isAlive()) players.put(player.getUUID().toString(), Transforms.world(body).position());
        }
        Assemblies.build(tree);
        Set<Part> done = new HashSet<>();
        for (Part part : List.copyOf(tree.ofClass(Classes.PART))) {
            if (done.contains(part)) continue;
            List<Part> group = Assemblies.of(part);
            done.addAll(group);
            decide(group, players, connected);
        }
        for (Report report = REPORTS.poll(); report != null; report = REPORTS.poll()) take(tree, report, players);
    }

    public static boolean ownable(Part part) {
        return part.isAlive() && PartBodies.INSTANCE.whyNotOwnable(part).isEmpty();
    }

    private static void decide(List<Part> group, Map<String, Vector3> players, Set<String> connected) {
        boolean ownable = true;
        for (Part part : group) {
            if (!part.collides && !part.anchored) continue;
            if (!ownable(part) || Physics.shapes().body(part.id()) == null) ownable = false;
        }
        if (!ownable) {
            for (Part part : group) {
                write(part, "");
                if (part.anchored || !part.isAlive()) part.ownershipSet(false);
            }
            return;
        }
        String owner = group.getFirst().networkOwner;
        boolean silent = false;
        boolean held = false;
        Part chosen = null;
        for (Part part : group) {
            if (part.ownerSilence() > (part.simulatedRemotely() ? QUIET_TICKS : FIRST_REPORT_TICKS)) silent = true;
            if (part.heldForServer()) held = true;
            if (chosen == null && part.ownershipSet()) chosen = part;
        }
        if (chosen != null) {
            String wanted = chosen.networkOwner;
            if (!wanted.isEmpty() && !connected.contains(wanted)) {
                for (Part part : group) part.ownershipSet(false);
                wanted = "";
            }
            for (Part part : group) {
                part.ownershipSet(chosen.ownershipSet());
                if (silent) part.ownerChanged();
                write(part, wanted);
            }
            return;
        }
        if (silent) {
            for (Part part : group) {
                part.holdForServer(TAKEN_BACK_TICKS);
                write(part, "");
            }
            return;
        }
        if (held) {
            for (Part part : group) write(part, "");
            return;
        }
        String driver = driver(group);
        String next;
        if (driver != null && connected.contains(driver)) next = driver;
        else if (!owner.isEmpty() && connected.contains(owner) && !players.containsKey(owner)) next = owner;
        else next = nearest(group, owner, players);
        for (Part part : group) write(part, next);
    }

    private static @Nullable String driver(List<Part> group) {
        for (Part part : group) {
            if (part instanceof Seat seat && seat.occupant instanceof Humanoid living
                    && living.parent() instanceof Character body && body.hasPlayer()) {
                return body.owner;
            }
        }
        return null;
    }

    private static String nearest(List<Part> group, String owner, Map<String, Vector3> players) {
        Vector3 holder = players.get(owner);
        if (holder != null && closest(group, holder) <= KEEP * KEEP) return owner;
        String nearest = "";
        double best = TAKE * TAKE;
        for (Map.Entry<String, Vector3> player : players.entrySet()) {
            double away = closest(group, player.getValue());
            if (away <= best) {
                best = away;
                nearest = player.getKey();
            }
        }
        return nearest;
    }

    private static double closest(List<Part> group, Vector3 at) {
        double best = Double.MAX_VALUE;
        for (Part part : group) best = Math.min(best, Transforms.world(part).position().sub(at).lengthSq());
        return best;
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
