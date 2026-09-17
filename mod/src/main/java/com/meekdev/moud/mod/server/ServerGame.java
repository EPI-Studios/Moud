package com.meekdev.moud.mod.server;

import com.meekdev.moud.mod.adapter.physics.Physics;
import com.meekdev.moud.mod.place.Game;
import com.meekdev.moud.script.api.GameRef;
import com.meekdev.moud.script.host.HostError;

public final class ServerGame implements GameRef {

    public static final ServerGame INSTANCE = new ServerGame();

    private ServerGame() {}

    @Override
    public boolean paused() {
        return false;
    }

    @Override
    public void paused(boolean on) {
        throw new HostError("game.paused is set in a LocalScript");
    }

    @Override
    public void quit() {
        throw new HostError("game:quit runs in a LocalScript");
    }

    @Override
    public void openSettings() {
        throw new HostError("game:openSettings runs in a LocalScript");
    }

    @Override
    public boolean exported() {
        return Game.standalone();
    }

    @Override
    public double gravity() {
        return Physics.gravity();
    }

    @Override
    public void gravity(double metresPerSecondSquared) {
        Physics.gravity(metresPerSecondSquared);
    }
}
