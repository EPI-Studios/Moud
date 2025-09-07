package com.moud.server.proxy;

import com.moud.api.math.Vector3;
import net.minestom.server.coordinate.Point;
import net.minestom.server.entity.Player;
import net.minestom.server.event.trait.CancellableEvent;
import net.minestom.server.instance.block.Block;

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

    public PlayerProxy getPlayer() {
        return new PlayerProxy(player);
    }

    public Vector3 getBlockPosition() {
        return new Vector3((float)blockPosition.x(), (float)blockPosition.y(), (float)blockPosition.z());
    }

    public String getBlockType() {
        return block.name();
    }

    public String getEventType() {
        return eventType;
    }

    public void cancel() {
        event.setCancelled(true);
    }

    public boolean isCancelled() {
        return event.isCancelled();
    }

    public boolean isBreakEvent() {
        return "break".equals(eventType);
    }

    public boolean isPlaceEvent() {
        return "place".equals(eventType);
    }

    public int getBlockStateId() {
        return block.stateId();
    }
}