package com.meekdev.moud.mod.server;

import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.mod.adapter.chat.ChatView;
import com.meekdev.moud.mod.transport.Packets;
import com.meekdev.moud.script.api.DebugRef;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class ServerDebug implements DebugRef {

    public static final ServerDebug INSTANCE = new ServerDebug();

    private ServerDebug() {}

    @Override
    public void line(Vector3 from, Vector3 to, Color color, double seconds) {
        send(0, new double[] {from.x(), from.y(), from.z(), to.x(), to.y(), to.z()}, "", color, seconds);
    }

    @Override
    public void box(CFrame frame, Vector3 size, Color color, double seconds) {
        Vector3 p = frame.position();
        Quat q = frame.rotation();
        send(1, new double[] {p.x(), p.y(), p.z(), q.x(), q.y(), q.z(), q.w(), size.x(), size.y(), size.z()}, "", color, seconds);
    }

    @Override
    public void sphere(Vector3 centre, double radius, Color color, double seconds) {
        send(2, new double[] {centre.x(), centre.y(), centre.z(), radius}, "", color, seconds);
    }

    @Override
    public void label(Vector3 at, String text, Color color, double seconds) {
        send(3, new double[] {at.x(), at.y(), at.z()}, text, color, seconds);
    }

    @Override
    public void watch(String name, String value) {
        send(4, new double[0], name + " (server): " + value, Color.WHITE, 0);
    }

    @Override
    public void clear() {
        send(5, new double[0], "", Color.WHITE, 0);
    }

    private static void send(int kind, double[] numbers, String text, Color color, double seconds) {
        MinecraftServer server = ServerScene.server();
        if (server == null) return;
        Packets.DebugDown payload = new Packets.DebugDown(kind, numbers, text, ChatView.argbOf(color, 1), seconds);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (ServerPlayNetworking.canSend(player, Packets.DebugDown.TYPE)) ServerPlayNetworking.send(player, payload);
        }
    }
}
