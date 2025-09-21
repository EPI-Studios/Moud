package com.moud.server.proxy;

import net.minestom.server.entity.Entity;
import net.minestom.server.entity.Player;
import org.graalvm.polyglot.HostAccess;

public class EntityInteractionProxy {
    private final Entity entity;
    private final Player player;
    private final String interactionType;

    public EntityInteractionProxy(Entity entity, Player player, String interactionType) {
        this.entity = entity;
        this.player = player;
        this.interactionType = interactionType;
    }

    @HostAccess.Export
    public PlayerProxy getPlayer() {
        return new PlayerProxy(player);
    }

    @HostAccess.Export
    public String getEntityType() {
        return entity.getEntityType().name();
    }

    @HostAccess.Export
    public String getEntityUuid() {
        return entity.getUuid().toString();
    }

    @HostAccess.Export
    public String getInteractionType() {
        return interactionType;
    }

    @HostAccess.Export
    public double getEntityX() {
        return entity.getPosition().x();
    }

    @HostAccess.Export
    public double getEntityY() {
        return entity.getPosition().y();
    }

    @HostAccess.Export
    public double getEntityZ() {
        return entity.getPosition().z();
    }

    @HostAccess.Export
    public boolean isHoverEnter() {
        return "hover_enter".equals(interactionType);
    }

    @HostAccess.Export
    public boolean isHoverExit() {
        return "hover_exit".equals(interactionType);
    }

    @HostAccess.Export
    public boolean isClick() {
        return "click".equals(interactionType);
    }
}