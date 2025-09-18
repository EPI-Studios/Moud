package com.moud.server.proxy;

import com.moud.api.math.Vector3;
import com.moud.server.network.ServerNetworkPackets;
import net.minestom.server.entity.Player;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

public class CameraLockProxy {
    private final Player player;
    private boolean isLocked = false;

    public CameraLockProxy(Player player) {
        this.player = player;
    }

    @HostAccess.Export
    public void lock(Vector3 position, Value rotationValue) {
        float yaw = 0;
        float pitch = 0;

        if (rotationValue != null && rotationValue.hasMembers()) {
            if (rotationValue.hasMember("yaw")) {
                yaw = rotationValue.getMember("yaw").asFloat();
            }
            if (rotationValue.hasMember("pitch")) {
                pitch = rotationValue.getMember("pitch").asFloat();
            }
        }

        this.isLocked = true;

        player.setInvisible(true);

        player.sendPacket(ServerNetworkPackets.createClientboundCameraLockPacket(
                position, yaw, pitch, true
        ));
    }

    @HostAccess.Export
    public void release() {
        if (!isLocked) return;
        this.isLocked = false;

        player.setInvisible(false);

        player.sendPacket(ServerNetworkPackets.createClientboundCameraLockPacket(
                Vector3.zero(), 0f, 0f, false
        ));
    }

    @HostAccess.Export
    public boolean isLocked() {
        return isLocked;
    }



}