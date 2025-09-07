package com.moud.server.proxy;

import com.moud.api.math.Vector3;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.event.player.PlayerMoveEvent;

public class PlayerMoveEventProxy {
    private final Player player;
    private final Pos newPosition;
    private final PlayerMoveEvent event;

    public PlayerMoveEventProxy(Player player, Pos newPosition, PlayerMoveEvent event) {
        this.player = player;
        this.newPosition = newPosition;
        this.event = event;
    }

    public PlayerProxy getPlayer() {
        return new PlayerProxy(player);
    }

    public Vector3 getNewPosition() {
        return new Vector3((float)newPosition.x(), (float)newPosition.y(), (float)newPosition.z());
    }

    public Vector3 getOldPosition() {
        Pos oldPos = event.getNewPosition();
        return new Vector3((float)oldPos.x(), (float)oldPos.y(), (float)oldPos.z());
    }

    public void cancel() {
        event.setCancelled(true);
    }

    public boolean isCancelled() {
        return event.isCancelled();
    }

    public double getDistance() {
        Pos oldPos = event.getNewPosition();
        return oldPos.distance(newPosition);
    }

    public boolean hasChangedBlock() {
        Pos oldPos = event.getNewPosition();
        return (int)oldPos.x() != (int)newPosition.x() ||
                (int)oldPos.y() != (int)newPosition.y() ||
                (int)oldPos.z() != (int)newPosition.z();
    }
}