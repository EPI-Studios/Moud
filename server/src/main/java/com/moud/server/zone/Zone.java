package com.moud.server.zone;

import com.moud.api.math.Vector3;
import net.minestom.server.entity.Player;
import org.graalvm.polyglot.Value;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class Zone {
    private final String id;
    private final Vector3 min;
    private final Vector3 max;
    private final Value onEnter;
    private final Value onLeave;

    private final Set<UUID> playersInZone = new HashSet<>();

    public Zone(String id, Vector3 corner1, Vector3 corner2, Value onEnter, Value onLeave) {
        this.id = id;
        this.min = new Vector3(Math.min(corner1.x, corner2.x), Math.min(corner1.y, corner2.y), Math.min(corner1.z, corner2.z));
        this.max = new Vector3(Math.max(corner1.x, corner2.x), Math.max(corner1.y, corner2.y), Math.max(corner1.z, corner2.z));
        this.onEnter = onEnter;
        this.onLeave = onLeave;
    }

    public boolean contains(Player player) {
        float x = (float) player.getPosition().x();
        float y = (float) player.getPosition().y();
        float z = (float) player.getPosition().z();
        return x >= min.x && x <= max.x &&
                y >= min.y && y <= max.y &&
                z >= min.z && z <= max.z;
    }

    public boolean isPlayerInside(Player player) {
        return playersInZone.contains(player.getUuid());
    }

    public void addPlayer(Player player) {
        playersInZone.add(player.getUuid());
    }

    public void removePlayer(Player player) {
        playersInZone.remove(player.getUuid());
    }

    public String getId() { return id; }
    public Vector3 getMin() { return min; }
    public Vector3 getMax() { return max; }
    public Value getOnEnterCallback() { return onEnter; }
    public Value getOnLeaveCallback() { return onLeave; }
}