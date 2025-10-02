package com.moud.server.proxy;

import com.moud.network.MoudPackets;
import com.moud.server.network.ServerNetworkManager;
import net.minestom.server.entity.Player;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class CameraLockProxy {
    private static final Logger LOGGER = LoggerFactory.getLogger(CameraLockProxy.class);
    private final Player player;
    private boolean isLocked = false;

    public CameraLockProxy(Player player) {
        this.player = player;
    }

    private Map<String, Object> valueToMap(Value options) {
        if (options == null || !options.hasMembers()) {
            return new HashMap<>();
        }
        Map<String, Object> map = new HashMap<>();
        for (String key : options.getMemberKeys()) {
            Value member = options.getMember(key);
            if (member.isHostObject() && member.asHostObject() instanceof com.moud.api.math.Vector3 vec) {
                map.put(key, Map.of("x", vec.x, "y", vec.y, "z", vec.z));
            } else if (member.isNumber()) {
                map.put(key, member.asDouble());
            } else if (member.isBoolean()) {
                map.put(key, member.asBoolean());
            } else if (member.isString()) {
                map.put(key, member.asString());
            }
        }
        return map;
    }

    @HostAccess.Export
    public void enableCustomCamera() {
        if (isLocked) return;
        isLocked = true;
        ServerNetworkManager.getInstance().send(player, new MoudPackets.CameraControlPacket(
                MoudPackets.CameraControlPacket.Action.ENABLE, null
        ));
    }

    @HostAccess.Export
    public void disableCustomCamera() {
        if (!isLocked) return;
        isLocked = false;
        ServerNetworkManager.getInstance().send(player, new MoudPackets.CameraControlPacket(
                MoudPackets.CameraControlPacket.Action.DISABLE, null
        ));
    }

    @HostAccess.Export
    public void transitionTo(Value options) {
        if (!isLocked) {
            LOGGER.warn("Attempted to call transitionTo on a camera that is not enabled. Call enableCustomCamera() first.");
            return;
        }
        ServerNetworkManager.getInstance().send(player, new MoudPackets.CameraControlPacket(
                MoudPackets.CameraControlPacket.Action.TRANSITION_TO, valueToMap(options)
        ));
    }

    @HostAccess.Export
    public void snapTo(Value options) {
        if (!isLocked) {
            LOGGER.warn("Attempted to call snapTo on a camera that is not enabled. Call enableCustomCamera() first.");
            return;
        }
        ServerNetworkManager.getInstance().send(player, new MoudPackets.CameraControlPacket(
                MoudPackets.CameraControlPacket.Action.SNAP_TO, valueToMap(options)
        ));
    }

    @HostAccess.Export
    public boolean isCustomCameraActive() {
        return isLocked;
    }
}