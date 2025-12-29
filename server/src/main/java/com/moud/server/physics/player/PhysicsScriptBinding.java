package com.moud.server.physics.player;

import com.moud.api.collision.AABB;
import com.moud.api.physics.player.CollisionWorld;
import com.moud.api.physics.player.PlayerController;
import com.moud.api.physics.player.PlayerInput;
import com.moud.api.physics.player.PlayerPhysicsConfig;
import com.moud.api.physics.player.PlayerState;
import com.moud.server.scripting.PolyglotValueUtil;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PhysicsScriptBinding {

    @HostAccess.Export
    public Value defaultStep(
            Value stateValue,
            Value inputValue,
            Value configValue,
            Value collisionValue,
            double dt
    ) {
        PlayerState state = parseState(stateValue);
        PlayerInput input = parseInput(inputValue);
        PlayerPhysicsConfig config = parseConfig(configValue);
        CollisionWorld world = parseCollisionWorld(collisionValue);

        PlayerState result = PlayerController.step(state, input, config, world, (float) dt);
        if (stateValue == null || stateValue.isNull() || !stateValue.hasMembers()) {
            return null;
        }
        writeState(stateValue, result);

        return stateValue;
    }

    @HostAccess.Export
    public PlayerState defaultStepNative(
            PlayerState state,
            PlayerInput input,
            PlayerPhysicsConfig config,
            CollisionWorld world,
            float dt
    ) {
        return PlayerController.step(state, input, config, world, dt);
    }

    @HostAccess.Export
    public ProxyObject createState(
            double x,
            double y,
            double z,
            double velX,
            double velY,
            double velZ,
            boolean onGround,
            boolean collidingHorizontally
    ) {
        return createStateObject(x, y, z, velX, velY, velZ, onGround, collidingHorizontally);
    }

    @HostAccess.Export
    public ProxyObject defaultConfig() {
        PlayerPhysicsConfig defaults = PlayerPhysicsConfig.defaults();
        Map<String, Object> map = new HashMap<>();
        map.put("speed", defaults.speed());
        map.put("accel", defaults.accel());
        map.put("friction", defaults.friction());
        map.put("airResistance", defaults.airResistance());
        map.put("gravity", defaults.gravity());
        map.put("jumpForce", defaults.jumpForce());
        map.put("stepHeight", defaults.stepHeight());
        map.put("width", defaults.width());
        map.put("height", defaults.height());
        map.put("sprintMultiplier", defaults.sprintMultiplier());
        map.put("sneakMultiplier", defaults.sneakMultiplier());
        return ProxyObject.fromMap(map);
    }

    @HostAccess.Export
    public ProxyObject createAABB(
            double minX,
            double minY,
            double minZ,
            double maxX,
            double maxY,
            double maxZ
    ) {
        Map<String, Object> map = new HashMap<>();
        map.put("minX", minX);
        map.put("minY", minY);
        map.put("minZ", minZ);
        map.put("maxX", maxX);
        map.put("maxY", maxY);
        map.put("maxZ", maxZ);
        return ProxyObject.fromMap(map);
    }

    @HostAccess.Export
    public double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    @HostAccess.Export
    public double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    @HostAccess.Export
    public double moveTowards(double current, double target, double maxDelta) {
        if (maxDelta <= 0.0) {
            return current;
        }
        double delta = target - current;
        if (Math.abs(delta) <= maxDelta) {
            return target;
        }
        return current + Math.copySign(maxDelta, delta);
    }

    private void writeState(Value stateValue, PlayerState state) {
        stateValue.putMember("x", state.x());
        stateValue.putMember("y", state.y());
        stateValue.putMember("z", state.z());
        stateValue.putMember("velX", state.velX());
        stateValue.putMember("velY", state.velY());
        stateValue.putMember("velZ", state.velZ());
        stateValue.putMember("onGround", state.onGround());
        stateValue.putMember("collidingHorizontally", state.collidingHorizontally());
    }

    private PlayerState parseState(Value v) {
        return PlayerStateScriptCodec.read(v, PlayerState.at(0, 0, 0));
    }

    private PlayerInput parseInput(Value v) {
        if (v == null || v.isNull() || !v.hasMembers()) {
            return new PlayerInput(0, false, false, false, false, false, false, false, 0, 0);
        }

        long sequenceId = PolyglotValueUtil.readLong(v, "sequenceId", 0L);
        boolean forward = PolyglotValueUtil.readBoolean(v, "forward", false);
        boolean backward = PolyglotValueUtil.readBoolean(v, "backward", false);
        boolean left = PolyglotValueUtil.readBoolean(v, "left", false);
        boolean right = PolyglotValueUtil.readBoolean(v, "right", false);
        boolean jump = PolyglotValueUtil.readBoolean(v, "jump", false);
        boolean sprint = PolyglotValueUtil.readBoolean(v, "sprint", false);
        boolean sneak = PolyglotValueUtil.readBoolean(v, "sneak", false);
        float yaw = PolyglotValueUtil.readFloat(v, "yaw", 0.0f);
        float pitch = PolyglotValueUtil.readFloat(v, "pitch", 0.0f);

        return new PlayerInput(
                sequenceId,
                forward,
                backward,
                left,
                right,
                jump,
                sprint,
                sneak,
                yaw,
                pitch
        );
    }

    private PlayerPhysicsConfig parseConfig(Value v) {
        if (v == null || v.isNull()) {
            return PlayerPhysicsConfig.defaults();
        }
        PlayerPhysicsConfig defaults = PlayerPhysicsConfig.defaults();
        return new PlayerPhysicsConfig(
                PolyglotValueUtil.readFloat(v, "speed", defaults.speed()),
                PolyglotValueUtil.readFloat(v, "accel", defaults.accel()),
                PolyglotValueUtil.readFloat(v, "friction", defaults.friction()),
                PolyglotValueUtil.readFloat(v, "airResistance", defaults.airResistance()),
                PolyglotValueUtil.readFloat(v, "gravity", defaults.gravity()),
                PolyglotValueUtil.readFloat(v, "jumpForce", defaults.jumpForce()),
                PolyglotValueUtil.readFloat(v, "stepHeight", defaults.stepHeight()),
                PolyglotValueUtil.readFloat(v, "width", defaults.width()),
                PolyglotValueUtil.readFloat(v, "height", defaults.height()),
                PolyglotValueUtil.readFloat(v, "sprintMultiplier", defaults.sprintMultiplier()),
                PolyglotValueUtil.readFloat(v, "sneakMultiplier", defaults.sneakMultiplier())
        );
    }

    private CollisionWorld parseCollisionWorld(Value v) {
        if (v == null || v.isNull() || !v.hasMember("getCollisions")) {
            return query -> List.of();
        }

        Value getCollisions = v.getMember("getCollisions");
        if (getCollisions == null || getCollisions.isNull() || !getCollisions.canExecute()) {
            return query -> List.of();
        }

        return query -> {
            if (query == null) {
                return List.of();
            }

            Value result;
            try {
                result = getCollisions.execute(ProxyObject.fromMap(Map.of(
                        "minX", query.minX(),
                        "minY", query.minY(),
                        "minZ", query.minZ(),
                        "maxX", query.maxX(),
                        "maxY", query.maxY(),
                        "maxZ", query.maxZ()
                )));
            } catch (Exception e) {
                return List.of();
            }

            if (result == null || result.isNull() || !result.hasArrayElements()) {
                return List.of();
            }

            long size = result.getArraySize();
            if (size <= 0) {
                return List.of();
            }

            List<AABB> out = new ArrayList<>((int) Math.min(size, 64));
            for (long i = 0; i < size; i++) {
                Value elem;
                try {
                    elem = result.getArrayElement(i);
                } catch (Exception e) {
                    continue;
                }
                if (elem == null || elem.isNull() || !elem.hasMembers()) {
                    continue;
                }

                double minX = PolyglotValueUtil.readDouble(elem, "minX", Double.NaN);
                double minY = PolyglotValueUtil.readDouble(elem, "minY", Double.NaN);
                double minZ = PolyglotValueUtil.readDouble(elem, "minZ", Double.NaN);
                double maxX = PolyglotValueUtil.readDouble(elem, "maxX", Double.NaN);
                double maxY = PolyglotValueUtil.readDouble(elem, "maxY", Double.NaN);
                double maxZ = PolyglotValueUtil.readDouble(elem, "maxZ", Double.NaN);

                if (!Double.isFinite(minX) || !Double.isFinite(minY) || !Double.isFinite(minZ)
                        || !Double.isFinite(maxX) || !Double.isFinite(maxY) || !Double.isFinite(maxZ)) {
                    continue;
                }

                out.add(new AABB(minX, minY, minZ, maxX, maxY, maxZ));
            }

            return out.isEmpty() ? List.of() : out;
        };
    }

    private static ProxyObject createStateObject(
            double x,
            double y,
            double z,
            double velX,
            double velY,
            double velZ,
            boolean onGround,
            boolean collidingHorizontally
    ) {
        Map<String, Object> map = new HashMap<>();
        map.put("x", x);
        map.put("y", y);
        map.put("z", z);
        map.put("velX", velX);
        map.put("velY", velY);
        map.put("velZ", velZ);
        map.put("onGround", onGround);
        map.put("collidingHorizontally", collidingHorizontally);
        return ProxyObject.fromMap(map);
    }
}
