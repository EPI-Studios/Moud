package com.meekdev.moud.mod.client;

import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.mod.transport.Packets;
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

    private static final Queue<Packets.PilotDown> INCOMING = new ConcurrentLinkedQueue<>();
    private static final List<Vector3> WAYPOINTS = new ArrayList<>();
    private static int next;
    private static boolean jump;

    private Autopilot() {}

    public static void listen() {
        ClientPlayNetworking.registerGlobalReceiver(Packets.PilotDown.TYPE, (payload, context) -> INCOMING.add(payload));
    }

    public static void apply(ClientInput input) {
        for (Packets.PilotDown down; (down = INCOMING.poll()) != null; ) {
            switch (down.kind()) {
                case Packets.PilotDown.WALK -> {
                    WAYPOINTS.clear();
                    double[] n = down.waypoints();
                    for (int i = 0; i + 2 < n.length; i += 3) WAYPOINTS.add(new Vector3(n[i], n[i + 1], n[i + 2]));
                    next = 0;
                }
                case Packets.PilotDown.JUMP -> jump = true;
                case Packets.PilotDown.STOP -> WAYPOINTS.clear();
                default -> {}
            }
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        Input pressed = input.keyPresses;
        boolean manual = pressed.forward() || pressed.backward() || pressed.left() || pressed.right();
        if (manual && !WAYPOINTS.isEmpty()) {
            WAYPOINTS.clear();
            ClientPlayNetworking.send(new Packets.PilotUp(Packets.PilotUp.CANCELLED));
        }
        boolean jumpNow = jump;
        jump = false;
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
                WAYPOINTS.clear();
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
        boolean climb = waypoint.y() > at.y() + 0.5 || player.horizontalCollision && player.onGround();
        input.keyPresses = new Input(forward > 0.3f, forward < -0.3f, left > 0.3f, left < -0.3f, jumpNow || climb, false, false);
        MoveVector.set(input, new Vec2(left, forward).normalized());
    }

    public static void clear() {
        WAYPOINTS.clear();
        INCOMING.clear();
        jump = false;
    }
}
