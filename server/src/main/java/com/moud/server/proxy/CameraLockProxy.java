package com.moud.server.proxy;

import com.moud.api.math.Vector3;
import com.moud.server.network.ServerNetworkManager;
import com.moud.server.network.ServerNetworkPackets;
import net.minestom.server.entity.Player;
import org.graalvm.polyglot.HostAccess;

public class CameraLockProxy {
    private final Player player;
    private boolean isLocked = false;
    private Vector3 lockedPosition;
    private float lockedYaw;
    private float lockedPitch;

    public CameraLockProxy(Player player) {
        this.player = player;
    }

    @HostAccess.Export
    public void lock(Vector3 position, Object rotation) {
        double yaw = 0;
        double pitch = 0;

        if (rotation != null) {
            try {
                org.graalvm.polyglot.Value rotValue = (org.graalvm.polyglot.Value) rotation;
                if (rotValue.hasMember("yaw")) {
                    yaw = rotValue.getMember("yaw").asDouble();
                }
                if (rotValue.hasMember("pitch")) {
                    pitch = rotValue.getMember("pitch").asDouble();
                }
            } catch (Exception e) {
                // Fallback to default values
            }
        }

        this.isLocked = true;
        this.lockedPosition = position;
        this.lockedYaw = (float) yaw;
        this.lockedPitch = (float) pitch;

        player.sendPacket(ServerNetworkPackets.createClientboundCameraLockPacket(
                position, this.lockedYaw, this.lockedPitch, true
        ));
        this.isLocked = true;
        this.lockedPosition = position;
        this.lockedYaw = (float) yaw;
        this.lockedPitch = (float) pitch;

        player.sendPacket(ServerNetworkPackets.createClientboundCameraLockPacket(
                position, this.lockedYaw, this.lockedPitch, true
        ));
    }

    @HostAccess.Export
    public void release() {
        this.isLocked = false;

        player.sendPacket(ServerNetworkPackets.createClientboundCameraLockPacket(
                Vector3.zero(), 0f, 0f, false
        ));
    }

    @HostAccess.Export
    public boolean isLocked() {
        return isLocked;
    }

    public Vector3 getLockedPosition() {
        return lockedPosition;
    }

    public float getLockedYaw() {
        return lockedYaw;
    }

    public float getLockedPitch() {
        return lockedPitch;
    }
}