package com.meekdev.moud.mod.server.input;

import com.meekdev.moud.core.character.Character;
import com.meekdev.moud.core.input.ClickDetector;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Spatial;
import com.meekdev.moud.core.instance.Transforms;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.part.Part;
import com.meekdev.moud.mod.adapter.physics.Physics;
import com.meekdev.moud.mod.server.ServerScene;
import com.meekdev.moud.mod.transport.payload.ClickPayload;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class ServerClicks {

    private record Heard(UUID player, int detector, int kind) {}

    private static final Queue<Heard> HEARD = new ConcurrentLinkedQueue<>();

    private static final double SLACK = 2;

    private ServerClicks() {}

    public static void listen() {
        ServerPlayNetworking.registerGlobalReceiver(ClickPayload.TYPE, (payload, context) ->
                HEARD.add(new Heard(context.player().getUUID(), payload.detector(), payload.kind())));
    }

    public static void tick(MinecraftServer server) {
        InstanceTree tree = ServerScene.tree();
        for (Heard heard; (heard = HEARD.poll()) != null; ) {
            if (tree == null || !(tree.byId(heard.detector()) instanceof ClickDetector detector)) continue;
            ServerPlayer player = server.getPlayerList().getPlayer(heard.player());
            if (player == null) continue;
            Character body = Physics.bodies().of(player, tree);
            Vector3 from = body != null ? Transforms.world(body).position()
                    : new Vector3(player.getX(), player.getEyeY(), player.getZ());
            if (distance(detector, from) > detector.maxActivationDistance + SLACK) continue;
            ClickDetector.Clicker clicker = new ClickDetector.Clicker(heard.player().toString());
            switch (heard.kind()) {
                case ClickPayload.CLICK -> detector.mouseClick.fire(clicker);
                case ClickPayload.RIGHT_CLICK -> detector.rightMouseClick.fire(clicker);
                case ClickPayload.HOVER_ENTER -> detector.mouseHoverEnter.fire(clicker);
                case ClickPayload.HOVER_LEAVE -> detector.mouseHoverLeave.fire(clicker);
                default -> {}
            }
        }
    }

    public static double distance(ClickDetector detector, Vector3 from) {
        Instance parent = detector.parent();
        if (!(parent instanceof Spatial)) return Double.POSITIVE_INFINITY;
        double reach = Transforms.world(parent).position().distance(from);
        return parent instanceof Part part ? Math.max(0, reach - part.size.length() / 2) : reach;
    }
}
