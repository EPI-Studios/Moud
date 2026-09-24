package com.meekdev.moud.mod.client.input;

import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.nav.Waypoint;
import com.meekdev.moud.mod.transport.payload.PilotDownPayload;
import com.meekdev.moud.mod.transport.payload.PilotUpPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;

public final class Autopilot {

    private static final Queue<PilotDownPayload> INCOMING = new ConcurrentLinkedQueue<>();
    private static final List<Vector3> WAYPOINTS = new ArrayList<>();
    private static final List<Waypoint.Action> ACTIONS = new ArrayList<>();
    private static final double LEAP_NEAREST = 1.0;
    private static final double LEAP_FARTHEST = 2.4;
    private static int next;
    private static boolean jump;
    private static Vector3 steer = Vector3.ZERO;
    private static boolean steerRelative;

    private Autopilot() {}

    public static void listen() {
        ClientPlayNetworking.registerGlobalReceiver(PilotDownPayload.TYPE, (payload, context) -> INCOMING.add(payload));
    }

    public static void apply(ClientInput input) {
        for (PilotDownPayload down; (down = INCOMING.poll()) != null; ) receive(down);
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        Input pressed = input.keyPresses;
        boolean manual = pressed.forward() || pressed.backward() || pressed.left() || pressed.right();
        if (manual && !WAYPOINTS.isEmpty()) {
            dropWaypoints();
            ClientPlayNetworking.send(new PilotUpPayload(PilotUpPayload.CANCELLED));
        }
        boolean jumpNow = jump;
        jump = false;
        if (WAYPOINTS.isEmpty() && !manual && steer.lengthSq() > 1.0e-6) {
            double yaw = Math.toRadians(player.getYRot());
            double forwardX = -Math.sin(yaw);
            double forwardZ = Math.cos(yaw);
            double wx = steer.x();
            double wz = steer.z();
            if (steerRelative) {
                double ahead = -steer.z();
                double side = steer.x();
                wx = forwardX * ahead - forwardZ * side;
                wz = forwardZ * ahead + forwardX * side;
            }
            double length = Math.sqrt(wx * wx + wz * wz);
            float forward = (float) ((wx * forwardX + wz * forwardZ) / length);
            float left = (float) ((wx * forwardZ - wz * forwardX) / length);
            input.keyPresses = new Input(forward > 0.3f, forward < -0.3f, left > 0.3f, left < -0.3f, jumpNow || pressed.jump(), pressed.shift(), pressed.sprint());
            MoveVector.set(input, new Vec2(left, forward).normalized());
            return;
        }
        if (WAYPOINTS.isEmpty()) {
            if (jumpNow) input.keyPresses = new Input(pressed.forward(), pressed.backward(), pressed.left(), pressed.right(), true, pressed.shift(), pressed.sprint());
            return;
        }
        Vector3 at = new Vector3(player.getX(), player.getY(), player.getZ());
        Vector3 waypoint = WAYPOINTS.get(next);
        double dx = waypoint.x() - at.x();
        double dz = waypoint.z() - at.z();
        while (Math.sqrt(dx * dx + dz * dz) < 0.35) {
            next++;
            if (next >= WAYPOINTS.size()) {
                dropWaypoints();
                input.keyPresses = new Input(false, false, false, false, jumpNow, false, false);
                return;
            }
            waypoint = WAYPOINTS.get(next);
            dx = waypoint.x() - at.x();
            dz = waypoint.z() - at.z();
        }
        double yaw = Math.toRadians(player.getYRot());
        double forwardX = -Math.sin(yaw);
        double forwardZ = Math.cos(yaw);
        double length = Math.sqrt(dx * dx + dz * dz);
        double wx = dx / length;
        double wz = dz / length;
        float forward = (float) (wx * forwardX + wz * forwardZ);
        float left = (float) (wx * forwardZ - wz * forwardX);
        boolean leapDistance = length > LEAP_NEAREST && length < LEAP_FARTHEST;
        boolean leap = ACTIONS.get(next) == Waypoint.Action.JUMP && player.onGround() && leapDistance;
        boolean climb = leap || waypoint.y() > at.y() + 0.5 || player.horizontalCollision && player.onGround();
        input.keyPresses = new Input(forward > 0.3f, forward < -0.3f, left > 0.3f, left < -0.3f, jumpNow || climb, false, false);
        MoveVector.set(input, new Vec2(left, forward).normalized());
    }

    static void receive(PilotDownPayload down) {
        switch (down.kind()) {
            case PilotDownPayload.WALK -> {
                readWaypoints(down.waypoints());
                next = 0;
            }
            case PilotDownPayload.JUMP -> jump = true;
            case PilotDownPayload.STOP -> dropWaypoints();
            case PilotDownPayload.MOVE -> {
                double[] n = down.waypoints();
                steer = n.length >= 4 ? new Vector3(n[0], 0, n[2]) : Vector3.ZERO;
                steerRelative = n.length >= 4 && n[3] != 0;
                if (steer.lengthSq() > 1.0e-6) dropWaypoints();
            }
            default -> {}
        }
    }

    private static void readWaypoints(double[] packed) {
        dropWaypoints();
        Waypoint.Action[] actions = Waypoint.Action.values();
        for (int at = 0; at + PilotDownPayload.WAYPOINT_STRIDE <= packed.length; at += PilotDownPayload.WAYPOINT_STRIDE) {
            WAYPOINTS.add(new Vector3(packed[at], packed[at + 1], packed[at + 2]));
            int action = Math.clamp((int) packed[at + 3], 0, actions.length - 1);
            ACTIONS.add(actions[action]);
        }
    }

    private static void dropWaypoints() {
        WAYPOINTS.clear();
        ACTIONS.clear();
    }

    public static void clear() {
        dropWaypoints();
        INCOMING.clear();
        jump = false;
        steer = Vector3.ZERO;
    }
}
