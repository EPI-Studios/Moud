package com.meekdev.moud.mod.server;

import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.mod.adapter.physics.Physics;
import com.meekdev.moud.script.api.ControlsRef;
import com.meekdev.moud.script.api.PlayerRef;
import net.minecraft.network.chat.Component;
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
