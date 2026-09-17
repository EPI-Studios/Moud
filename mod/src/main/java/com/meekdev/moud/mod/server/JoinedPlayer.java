package com.meekdev.moud.mod.server;

import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.mod.adapter.physics.Physics;
import com.meekdev.moud.script.api.ControlsRef;
import com.meekdev.moud.script.api.PlayerRef;
import java.time.Instant;
import java.util.Date;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.UserBanListEntry;
import net.minecraft.server.level.ServerPlayer;

final class JoinedPlayer implements PlayerRef {

    private final ServerPlayer player;

    JoinedPlayer(ServerPlayer player) {
        this.player = player;
    }

    @Override
    public String name() {
        return player.getGameProfile().name();
    }

    @Override
    public String id() {
        return player.getUUID().toString();
    }

    @Override
    public Instance character() {
        return Physics.bodies().of(player, ServerScene.tree());
    }

    @Override
    public void spawn(Vector3 position) {
        Spawning.spawn(player, position);
    }

    @Override
    public ControlsRef controls() {
        return Spawning.controls(player);
    }

    @Override
    public Instance team() {
        return Teams.of(player);
    }

    @Override
    public void team(Instance team) {
        Teams.set(player, team);
    }

    @Override
    public void ban(String reason, double seconds) {
        MinecraftServer server = player.level().getServer();
        if (server != null) {
            Date until = seconds > 0 ? Date.from(Instant.now().plusSeconds((long) seconds)) : null;
            server.getPlayerList().getBans().add(new UserBanListEntry(new NameAndId(player.getGameProfile()), null, "moud", until, reason));
        }
        kick(reason);
    }

    @Override
    public void kick(String message) {
        player.connection.disconnect(Component.literal(message.isBlank() ? "You were removed from the game" : message));
    }

    @Override
    public double ping() {
        return player.connection.latency() / 1000.0;
    }

    @Override
    public double viewTime() {
        return ServerHistory.INSTANCE.viewTime(player.getUUID().toString());
    }
}
