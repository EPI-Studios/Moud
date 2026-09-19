package com.meekdev.moud.mod.server;

import com.meekdev.moud.core.character.Character;
import com.meekdev.moud.core.character.Humanoid;
import com.meekdev.moud.core.character.Rig;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.instance.Transforms;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.part.Part;
import com.meekdev.moud.core.part.Seat;
import com.meekdev.moud.core.part.Seats;
import com.meekdev.moud.core.part.VehicleSeat;
import com.meekdev.moud.core.query.Queries;
import com.meekdev.moud.mod.adapter.physics.Physics;
import com.meekdev.moud.mod.transport.payload.SeatPayload;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class Seating {

    private static final PropertyDef THROTTLE = Classes.VEHICLE_SEAT.property("throttle");
    private static final PropertyDef STEER = Classes.VEHICLE_SEAT.property("steer");

    private static final double GRIP = 0.3;

    private static final Map<UUID, SeatPayload> INPUT = new ConcurrentHashMap<>();

    private Seating() {}

    public static void listen() {
        ServerPlayNetworking.registerGlobalReceiver(SeatPayload.TYPE, (payload, context) ->
                INPUT.put(context.player().getUUID(), payload));
    }

    static void tick(MinecraftServer server, double dt) {
        InstanceTree tree = ServerScene.tree();
        if (tree == null) return;
        Seats.tick(tree, dt);
        for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
            Character body = Physics.bodies().of(player, tree);
            Humanoid living = body == null ? null : Rig.humanoid(body);
            if (living == null) continue;
            Seat seat = Seats.of(living);
            if (seat == null) {
                seat = free(tree, body, living);
                if (seat != null) take(player, seat, living);
            } else {
                hold(player, seat);
            }
        }
        drive(tree);
    }

    static void left(ServerPlayer player) {
        INPUT.remove(player.getUUID());
    }

    private static Seat free(InstanceTree tree, Character body, Humanoid living) {
        if (Seats.waiting(living) || tree.root() == null) return null;
        CFrame world = Transforms.world(body);
        CFrame around = world.withPosition(world.pointToWorld(new Vector3(0, body.height * 0.5, 0)));
        Vector3 size = new Vector3(body.radius * 2, body.height, body.radius * 2);
        for (Part part : Queries.inBox(tree.root(), around, size, Queries.Filter.ALL)) {
            if (part instanceof Seat seat && Seats.canSit(seat, living)) return seat;
        }
        return null;
    }

    private static void take(ServerPlayer player, Seat seat, Humanoid living) {
        if (!Seats.sit(seat, living)) return;
        CFrame frame = Seats.frame(seat);
        Vector3 at = frame.position();
        Vector3 look = frame.lookVector();
        float yaw = (float) Math.toDegrees(Math.atan2(-look.x(), look.z()));
        player.teleportTo(player.level(), at.x(), at.y(), at.z(), Set.of(), yaw, player.getXRot(), true);
    }

    private static void hold(ServerPlayer player, Seat seat) {
        if (player.getLastClientInput().jump()) {
            Seats.stand(seat);
            return;
        }
        if (seat.simulatedRemotely() && seat.networkOwner.equals(player.getUUID().toString())) return;
        Vector3 at = Seats.frame(seat).position();
        double dx = player.getX() - at.x();
        double dy = player.getY() - at.y();
        double dz = player.getZ() - at.z();
        if (dx * dx + dy * dy + dz * dz > GRIP * GRIP) player.teleportTo(at.x(), at.y(), at.z());
    }

    private static void drive(InstanceTree tree) {
        for (VehicleSeat seat : tree.ofClass(Classes.VEHICLE_SEAT)) {
            Character body = Seats.body(seat);
            SeatPayload input = body == null || body.owner.isEmpty() ? null : INPUT.get(UUID.fromString(body.owner));
            double throttle = input == null ? 0 : input.throttle();
            double steer = input == null ? 0 : input.steer();
            if (seat.throttle != throttle) Instances.setNum(seat, THROTTLE, throttle);
            if (seat.steer != steer) Instances.setNum(seat, STEER, steer);
        }
    }
}
