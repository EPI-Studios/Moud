package com.moud.server.proxy;

import com.moud.api.math.Vector3;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.event.player.PlayerMoveEvent;
import org.graalvm.polyglot.HostAccess;

public class PlayerMoveEventProxy {
    private final Player player;
    private final Pos newPosition;
    private final PlayerMoveEvent event;

    public PlayerMoveEventProxy(Player player, Pos newPosition, PlayerMoveEvent event) {
        this.player = player;
        this.newPosition = newPosition;
        this.event = event;
    }

    @HostAccess.Export
    public PlayerProxy getPlayer() {
        return new PlayerProxy(player);
    }

    @HostAccess.Export
    public Vector3 getNewPosition() {
        return new Vector3((float)newPosition.x(), (float)newPosition.y(), (float)newPosition.z());
    }

    @HostAccess.Export
    public Vector3 getOldPosition() {
        Pos oldPos = event.getNewPosition();
        return new Vector3((float)oldPos.x(), (float)oldPos.y(), (float)oldPos.z());
    }

    @HostAccess.Export
    public void cancel() {
        event.setCancelled(true);
    }

    @HostAccess.Export
    public boolean isCancelled() {
        return event.isCancelled();
    }

    @HostAccess.Export
    public double getDistance() {
        Pos oldPos = event.getNewPosition();
        return oldPos.distance(newPosition);
    }

    @HostAccess.Export
    public boolean hasChangedBlock() {
        Pos oldPos = event.getNewPosition();
        return (int)oldPos.x() != (int)newPosition.x() ||
                (int)oldPos.y() != (int)newPosition.y() ||
                (int)oldPos.z() != (int)newPosition.z();
    }
}