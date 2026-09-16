package com.meekdev.moud.mod.server;

import com.meekdev.moud.core.character.Character;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.mod.transport.payload.PushPayload;
import com.meekdev.moud.script.api.PushRef;
import com.meekdev.moud.script.host.HostError;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class ServerPush implements PushRef {

    public static final ServerPush INSTANCE = new ServerPush();

    private ServerPush() {}

    @Override
    public void push(Character body, Vector3 perSecond, boolean replace) {
        if (body.owner.isEmpty()) throw new HostError("only a player's body can be pushed for now, %s has no player", body.name());
        MinecraftServer server = ServerScene.server();
        ServerPlayer player = null;
        try {
            if (server != null) player = server.getPlayerList().getPlayer(UUID.fromString(body.owner));
        } catch (IllegalArgumentException ignored) {
        }
        if (player == null || !ServerPlayNetworking.canSend(player, PushPayload.TYPE)) return;
        ServerPlayNetworking.send(player, new PushPayload(perSecond.x(), perSecond.y(), perSecond.z(), replace));
    }
}
