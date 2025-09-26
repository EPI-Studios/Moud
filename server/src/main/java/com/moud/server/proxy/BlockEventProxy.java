package com.moud.server.proxy;

import com.moud.api.math.Vector3;
import net.minestom.server.coordinate.Point;
import net.minestom.server.entity.Player;
import net.minestom.server.event.trait.CancellableEvent;
import net.minestom.server.instance.block.Block;
import org.graalvm.polyglot.HostAccess;

public class BlockEventProxy {
    private final Player player;
    private final Point blockPosition;
    private final Block block;
    private final CancellableEvent event;
    private final String eventType;

    public BlockEventProxy(Player player, Point blockPosition, Block block, CancellableEvent event, String eventType) {
        this.player = player;
        this.blockPosition = blockPosition;
        this.block = block;
        this.event = event;
        this.eventType = eventType;
    }

    @HostAccess.Export
    public PlayerProxy getPlayer() {
        return new PlayerProxy(player);
    }

    @HostAccess.Export
    public Vector3 getBlockPosition() {
        return new Vector3((float)blockPosition.x(), (float)blockPosition.y(), (float)blockPosition.z());
    }

    @HostAccess.Export
    public String getBlockType() {
        return block.name();
    }

    @HostAccess.Export
    public String getEventType() {
        return eventType;
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
    public boolean isBreakEvent() {
        return "break".equals(eventType);
    }

    @HostAccess.Export
    public boolean isPlaceEvent() {
        return "place".equals(eventType);
    }

    @HostAccess.Export
    public int getBlockStateId() {
        return block.stateId();
    }
}