package com.moud.server.proxy;

import com.moud.server.ts.TsExpose;
import com.moud.network.MoudPackets;
import com.moud.server.network.ServerNetworkManager;
import net.minestom.server.entity.Player;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@TsExpose
public class CameraLockProxy {
    private static final Logger LOGGER = LoggerFactory.getLogger(CameraLockProxy.class);
    private final Player player;
    private boolean isLocked = false;

    public CameraLockProxy(Player player) {
        this.player = player;
    }

    private Object convertValue(Value value) {
        if (value == null) {
            return null;
        }
        if (value.isHostObject()) {
            Object hostObj = value.asHostObject();
            if (hostObj instanceof com.moud.api.math.Vector3 vec) {
                return Map.of("x", vec.x, "y", vec.y, "z", vec.z);
            }
            if (hostObj instanceof Map<?, ?> map) {
                Map<String, Object> converted = new HashMap<>();
                map.forEach((k, v) -> {
                    if (k != null && v != null) {
                        converted.put(k.toString(), v instanceof Value val ? convertValue(val) : v);
                    }
                });
                return converted;
            }
            if (hostObj instanceof List<?> list) {
                List<Object> converted = new ArrayList<>();
                for (Object element : list) {
                    if (element instanceof Value val) {
                        Object convertedElement = convertValue(val);
                        if (convertedElement != null) {
                            converted.add(convertedElement);
                        }
                    } else if (element != null) {
                        converted.add(element);
                    }
                }
                return converted;
            }
        }
        if (value.hasArrayElements()) {
            List<Object> list = new ArrayList<>();
            long size = value.getArraySize();
            for (int i = 0; i < size; i++) {
                Object converted = convertValue(value.getArrayElement(i));
                if (converted != null) {
                    list.add(converted);
                }
            }
            return list;
        }
        if (value.hasMembers()) {
            Map<String, Object> map = new HashMap<>();
            for (String key : value.getMemberKeys()) {
                Object member = convertValue(value.getMember(key));
                if (member != null) {
                    map.put(key, member);
                }
            }
            return map;
        }
        if (value.isNumber()) {
            if (value.fitsInInt()) return value.asInt();
            if (value.fitsInLong()) return value.asLong();
            return value.asDouble();
        }
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        if (value.isString()) {
            return value.asString();
        }
        return null;
    }

    private Map<String, Object> valueToMap(Value options) {
        Object converted = convertValue(options);
        if (converted instanceof Map<?, ?> convertedMap) {
            Map<String, Object> result = new HashMap<>();
            convertedMap.forEach((k, v) -> {
                if (k != null && v != null) {
                    result.put(k.toString(), v);
                }
            });
            return result;
        }
        return new HashMap<>();
    }

    private List<Object> valueToList(Value options) {
        Object converted = convertValue(options);
        if (converted instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        if (converted != null) {
            LOGGER.warn("valueToList expected list but got {} ({})", converted.getClass().getSimpleName(), converted);
        }
        return new ArrayList<>();
    }

    @HostAccess.Export
    public void enableCustomCamera() {
        if (isLocked) return;
        isLocked = true;
        ServerNetworkManager.getInstance().send(player, new MoudPackets.CameraControlPacket(
                MoudPackets.CameraControlPacket.Action.ENABLE, null, null
        ));
    }

    @HostAccess.Export
    public void disableCustomCamera() {
        if (!isLocked) return;
        isLocked = false;
        ServerNetworkManager.getInstance().send(player, new MoudPackets.CameraControlPacket(
                MoudPackets.CameraControlPacket.Action.DISABLE, null, null
        ));
    }

    @HostAccess.Export
    public void transitionTo(Value options) {
        if (!isLocked) {
            LOGGER.warn("Attempted to call transitionTo on a camera that is not enabled. Call enableCustomCamera() first.");
            return;
        }
        ServerNetworkManager.getInstance().send(player, new MoudPackets.CameraControlPacket(
                MoudPackets.CameraControlPacket.Action.TRANSITION_TO, valueToMap(options), null
        ));
    }

    @HostAccess.Export
    public void snapTo(Value options) {
        if (!isLocked) {
            LOGGER.warn("Attempted to call snapTo on a camera that is not enabled. Call enableCustomCamera() first.");
            return;
        }
        ServerNetworkManager.getInstance().send(player, new MoudPackets.CameraControlPacket(
                MoudPackets.CameraControlPacket.Action.SNAP_TO, valueToMap(options), null
        ));
    }

    @HostAccess.Export
    public void followTo(Value options) {
        if (!isLocked) {
            LOGGER.warn("Attempted to call followTo on a camera that is not enabled. Call enableCustomCamera() first.");
            return;
        }
        ServerNetworkManager.getInstance().send(player, new MoudPackets.CameraControlPacket(
                MoudPackets.CameraControlPacket.Action.FOLLOW_TO, valueToMap(options), null
        ));
    }

    @HostAccess.Export
    public void followPath(Value points, long duration, boolean loop) {
        if (!isLocked) {
            LOGGER.warn("Attempted to call followPath on a camera that is not enabled. Call enableCustomCamera() first.");
            return;
        }
        List<Object> pointList = valueToList(points);
        if (pointList.isEmpty()) {
            LOGGER.warn("followPath requires an array of points");
            return;
        }
        Map<String, Object> options = new HashMap<>();
        options.put("points", pointList);
        options.put("duration", duration);
        options.put("loop", loop);
        ServerNetworkManager.getInstance().send(player, new MoudPackets.CameraControlPacket(
                MoudPackets.CameraControlPacket.Action.FOLLOW_PATH, options, null
        ));
    }

    @HostAccess.Export
    public void stopPath() {
        if (!isLocked) {
            return;
        }
        ServerNetworkManager.getInstance().send(player, new MoudPackets.CameraControlPacket(
                MoudPackets.CameraControlPacket.Action.STOP_PATH, null, null
        ));
    }

    @HostAccess.Export
    public void createCinematic(Value keyframes) {
        if (!isLocked) {
            LOGGER.warn("Attempted to call createCinematic on a camera that is not enabled. Call enableCustomCamera() first.");
            return;
        }
        List<Object> frameList = valueToList(keyframes);
        if (frameList.isEmpty()) {
            LOGGER.warn("createCinematic requires an array of keyframes");
            return;
        }
        Map<String, Object> options = new HashMap<>();
        options.put("keyframes", frameList);
        ServerNetworkManager.getInstance().send(player, new MoudPackets.CameraControlPacket(
                MoudPackets.CameraControlPacket.Action.CREATE_CINEMATIC, options, null
        ));
    }

    @HostAccess.Export
    public void stopCinematic() {
        if (!isLocked) {
            return;
        }
        ServerNetworkManager.getInstance().send(player, new MoudPackets.CameraControlPacket(
                MoudPackets.CameraControlPacket.Action.STOP_CINEMATIC, null, null
        ));
    }

    @HostAccess.Export
    public void lookAt(Value target) {
        if (!isLocked) {
            LOGGER.warn("Attempted to call lookAt on a camera that is not enabled. Call enableCustomCamera() first.");
            return;
        }
        Map<String, Object> targetMap = valueToMap(target);
        if (targetMap.isEmpty()) {
            LOGGER.warn("lookAt requires a target with x, y, z values");
            return;
        }
        ServerNetworkManager.getInstance().send(player, new MoudPackets.CameraControlPacket(
                MoudPackets.CameraControlPacket.Action.LOOK_AT, targetMap, null
        ));
    }

    @HostAccess.Export
    public void clearLookAt() {
        if (!isLocked) {
            return;
        }
        ServerNetworkManager.getInstance().send(player, new MoudPackets.CameraControlPacket(
                MoudPackets.CameraControlPacket.Action.CLEAR_LOOK_AT, null, null
        ));
    }

    @HostAccess.Export
    public void dollyZoom(double targetFov, long duration) {
        Map<String, Object> options = new HashMap<>();
        options.put("targetFov", targetFov);
        options.put("duration", duration);
        dollyZoom(Value.asValue(options));
    }

    @HostAccess.Export
    public void dollyZoom(Value options) {
        if (!isLocked) {
            LOGGER.warn("Attempted to call dollyZoom on a camera that is not enabled. Call enableCustomCamera() first.");
            return;
        }
        Map<String, Object> converted = valueToMap(options);
        if (!converted.containsKey("targetFov") && options != null && options.hasMember("targetFov")) {
            converted.put("targetFov", options.getMember("targetFov").asDouble());
        }
        if (!converted.containsKey("duration") && options != null && options.hasMember("duration")) {
            converted.put("duration", options.getMember("duration").asLong());
        }
        ServerNetworkManager.getInstance().send(player, new MoudPackets.CameraControlPacket(
                MoudPackets.CameraControlPacket.Action.DOLLY_ZOOM, converted, null
        ));
    }

    @HostAccess.Export
    public boolean isCustomCameraActive() {
        return isLocked;
    }
}
