package com.moud.client.physics;

import com.moud.api.collision.AABB;
import com.moud.api.physics.player.CollisionWorld;
import com.moud.api.physics.player.PlayerController;
import com.moud.api.physics.player.PlayerInput;
import com.moud.api.physics.player.PlayerPhysicsConfig;
import com.moud.api.physics.player.PlayerPhysicsControllers;
import com.moud.api.physics.player.PlayerState;
import com.moud.client.movement.ClientMovementTracker;
import com.moud.client.util.PolyglotValueUtil;
import net.minecraft.client.MinecraftClient;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ClientPhysicsScriptLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientPhysicsScriptLoader.class);

    private final Context jsContext;
    private final ClientPhysicsBinding physicsBinding;

    public ClientPhysicsScriptLoader(Context jsContext) {
        this.jsContext = jsContext;
        this.physicsBinding = new ClientPhysicsBinding();
    }

    public boolean loadSharedPhysics(String scriptSource) {
        if (scriptSource == null || scriptSource.isBlank()) {
            return false;
        }

        if (jsContext == null) {
            return false;
        }

        synchronized (jsContext) {
            boolean entered = false;
            try {
                jsContext.enter();
                entered = true;

                jsContext.getBindings("js").putMember("Physics", physicsBinding);

                String wrappedSource = """
                    (function() {
                        const exports = {};
                        const module = { exports: exports };
                        %s
                        return module.exports.controller || module.exports.default || module.exports
                            || exports.controller || exports.default;
                    })()
                    """.formatted(scriptSource);

                Source source = Source.newBuilder("js", wrappedSource, "shared-physics.js").build();
                Value result = jsContext.eval(source);

                if (result == null || result.isNull()) {
                    LOGGER.debug("Client: Shared physics script did not export a controller");
                    return false;
                }

                return registerController(result);
            } catch (Throwable t) {
                LOGGER.error("Client: Failed to load shared physics: {}", t.getMessage(), t);
                return false;
            } finally {
                if (entered) {
                    try {
                        jsContext.leave();
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
    }

    private boolean registerController(Value controllerValue) {
        if (!controllerValue.hasMembers()) {
            return false;
        }

        Value idValue = controllerValue.getMember("id");
        Value stepValue = controllerValue.getMember("step");

        if (idValue == null || idValue.isNull() || !idValue.isString()) {
            LOGGER.warn("Client: Physics controller missing 'id'");
            return false;
        }

        if (stepValue == null || stepValue.isNull() || !stepValue.canExecute()) {
            LOGGER.warn("Client: Physics controller missing 'step' function");
            return false;
        }

        String id = idValue.asString();
        ScriptedClientPhysicsController controller = new ScriptedClientPhysicsController(id, stepValue, jsContext);

        PlayerPhysicsControllers.register(id, controller);
        LOGGER.info("Client: Registered shared physics controller: {}", id);
        MinecraftClient.getInstance().execute(() -> ClientMovementTracker.getInstance().tryEnablePendingPrediction());

        return true;
    }


    public static final class ClientPhysicsBinding {

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
            if (maxDelta <= 0.0) return current;
            double delta = target - current;
            if (Math.abs(delta) <= maxDelta) return target;
            return current + Math.copySign(maxDelta, delta);
        }

        private static void writeState(Value stateValue, PlayerState state) {
            stateValue.putMember("x", state.x());
            stateValue.putMember("y", state.y());
            stateValue.putMember("z", state.z());
            stateValue.putMember("velX", state.velX());
            stateValue.putMember("velY", state.velY());
            stateValue.putMember("velZ", state.velZ());
            stateValue.putMember("onGround", state.onGround());
            stateValue.putMember("collidingHorizontally", state.collidingHorizontally());
        }

        private static PlayerState parseState(Value v) {
            return PlayerStateScriptCodec.read(v, PlayerState.at(0, 0, 0));
        }

        private static PlayerInput parseInput(Value v) {
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

        private static PlayerPhysicsConfig parseConfig(Value v) {
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

        private static CollisionWorld parseCollisionWorld(Value v) {
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
    }
}
