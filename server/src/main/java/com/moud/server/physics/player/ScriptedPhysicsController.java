package com.moud.server.physics.player;

import com.moud.api.collision.AABB;
import com.moud.api.physics.player.CollisionWorld;
import com.moud.api.physics.player.PlayerController;
import com.moud.api.physics.player.PlayerInput;
import com.moud.api.physics.player.PlayerPhysicsConfig;
import com.moud.api.physics.player.PlayerPhysicsController;
import com.moud.api.physics.player.PlayerState;
import com.moud.server.logging.MoudLogger;
import com.moud.server.scripting.PolyglotValueUtil;
import com.moud.server.scripting.ScriptPlayerContextProvider;
import com.moud.server.scripting.ScriptThreadContext;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;


public final class ScriptedPhysicsController implements PlayerPhysicsController {
    private static final MoudLogger LOGGER = MoudLogger.getLogger(ScriptedPhysicsController.class);

    private final String id;
    private final Value stepFunction;
    private final Context jsContext;

    public ScriptedPhysicsController(String id, Value stepFunction, Context jsContext) {
        this.id = id;
        this.stepFunction = stepFunction;
        this.jsContext = jsContext;
    }

    public String getId() {
        return id;
    }

    @Override
    public PlayerState step(PlayerState current, PlayerInput input, PlayerPhysicsConfig config,
                            CollisionWorld world, float dt) {
        UUID playerUuid = ScriptThreadContext.getPlayerId();
        return stepWithPlayer(current, input, config, world, dt, playerUuid);
    }

    public PlayerState stepWithPlayer(PlayerState current, PlayerInput input, PlayerPhysicsConfig config,
                                       CollisionWorld world, float dt, UUID playerUuid) {
        if (stepFunction == null || jsContext == null) {
            return PlayerController.step(current, input, config, world, dt);
        }

        synchronized (jsContext) {
            boolean entered = false;
            try {
                jsContext.enter();
                entered = true;

                Value jsState = createJsState(current);
                Value jsInput = createJsInput(input);
                Value jsConfig = createJsConfig(config);
                Value jsCollision = createJsCollisionWorld(world);
                Value jsPlayerContext = createJsContext(playerUuid, dt, ScriptThreadContext.getPlayerContext());

                Value result = stepFunction.execute(jsState, jsInput, jsConfig, jsCollision, jsPlayerContext);

                return parseJsState(result, current);
            } catch (Throwable t) {
                LOGGER.warn("Scripted physics controller '{}' threw exception, using default physics: {}",
                        id, t.getMessage());
                return PlayerController.step(current, input, config, world, dt);
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

    private Value createJsState(PlayerState state) {
        Value obj = jsContext.eval("js", "({})");
        obj.putMember("x", state.x());
        obj.putMember("y", state.y());
        obj.putMember("z", state.z());
        obj.putMember("velX", state.velX());
        obj.putMember("velY", state.velY());
        obj.putMember("velZ", state.velZ());
        obj.putMember("onGround", state.onGround());
        obj.putMember("collidingHorizontally", state.collidingHorizontally());
        return obj;
    }

    private Value createJsInput(PlayerInput input) {
        Value obj = jsContext.eval("js", "({})");
        obj.putMember("sequenceId", input.sequenceId());
        obj.putMember("forward", input.forward());
        obj.putMember("backward", input.backward());
        obj.putMember("left", input.left());
        obj.putMember("right", input.right());
        obj.putMember("jump", input.jump());
        obj.putMember("sprint", input.sprint());
        obj.putMember("sneak", input.sneak());
        obj.putMember("yaw", input.yaw());
        obj.putMember("pitch", input.pitch());
        return obj;
    }

    private Value createJsConfig(PlayerPhysicsConfig config) {
        Value obj = jsContext.eval("js", "({})");
        obj.putMember("speed", config.speed());
        obj.putMember("accel", config.accel());
        obj.putMember("friction", config.friction());
        obj.putMember("airResistance", config.airResistance());
        obj.putMember("gravity", config.gravity());
        obj.putMember("jumpForce", config.jumpForce());
        obj.putMember("stepHeight", config.stepHeight());
        obj.putMember("width", config.width());
        obj.putMember("height", config.height());
        obj.putMember("sprintMultiplier", config.sprintMultiplier());
        obj.putMember("sneakMultiplier", config.sneakMultiplier());
        return obj;
    }

    private Value createJsCollisionWorld(CollisionWorld world) {
        Value obj = jsContext.eval("js", "({})");
        obj.putMember("getCollisions", (ProxyExecutable) arguments -> {
            if (arguments == null || arguments.length < 1) {
                return List.of();
            }

            Value queryValue = arguments[0];
            if (queryValue == null || queryValue.isNull() || !queryValue.hasMembers()) {
                return List.of();
            }

            double minX = PolyglotValueUtil.readDouble(queryValue, "minX", Double.NaN);
            double minY = PolyglotValueUtil.readDouble(queryValue, "minY", Double.NaN);
            double minZ = PolyglotValueUtil.readDouble(queryValue, "minZ", Double.NaN);
            double maxX = PolyglotValueUtil.readDouble(queryValue, "maxX", Double.NaN);
            double maxY = PolyglotValueUtil.readDouble(queryValue, "maxY", Double.NaN);
            double maxZ = PolyglotValueUtil.readDouble(queryValue, "maxZ", Double.NaN);

            if (!Double.isFinite(minX) || !Double.isFinite(minY) || !Double.isFinite(minZ)
                    || !Double.isFinite(maxX) || !Double.isFinite(maxY) || !Double.isFinite(maxZ)) {
                return List.of();
            }

            AABB query = new AABB(minX, minY, minZ, maxX, maxY, maxZ);

            List<AABB> collisions = world != null ? world.getCollisions(query) : List.of();
            if (collisions == null || collisions.isEmpty()) {
                return List.of();
            }

            List<ProxyObject> arr = new ArrayList<>(collisions.size());
            for (AABB aabb : collisions) {
                if (aabb == null) {
                    continue;
                }
                arr.add(ProxyObject.fromMap(Map.of(
                        "minX", aabb.minX(),
                        "minY", aabb.minY(),
                        "minZ", aabb.minZ(),
                        "maxX", aabb.maxX(),
                        "maxY", aabb.maxY(),
                        "maxZ", aabb.maxZ()
                )));
            }
            return arr;
        });
        return obj;
    }

    private Value createJsContext(UUID playerUuid, float dt, ScriptPlayerContextProvider provider) {
        Value obj = jsContext.eval("js", "({})");
        obj.putMember("dt", dt);
        obj.putMember("tick", System.currentTimeMillis() / 50);

        // Player context
        Value playerObj = jsContext.eval("js", "({})");
        playerObj.putMember("uuid", playerUuid != null ? playerUuid.toString() : "");

        ScriptPlayerContextProvider finalProvider = provider;

        playerObj.putMember("hasItem", (ProxyExecutable) arguments -> {
            if (finalProvider == null || arguments == null || arguments.length < 1) {
                return false;
            }
            String itemId = PolyglotValueUtil.asString(arguments[0], null);
            if (itemId == null || itemId.isBlank()) {
                return false;
            }
            return finalProvider.hasItem(itemId);
        });
        playerObj.putMember("getHealth", (ProxyExecutable) arguments ->
                finalProvider != null ? finalProvider.getHealth() : 20f);
        playerObj.putMember("hasEffect", (ProxyExecutable) arguments -> {
            if (finalProvider == null || arguments == null || arguments.length < 1) {
                return false;
            }
            String effectId = PolyglotValueUtil.asString(arguments[0], null);
            if (effectId == null || effectId.isBlank()) {
                return false;
            }
            return finalProvider.hasEffect(effectId);
        });
        playerObj.putMember("getData", (ProxyExecutable) arguments -> {
            if (finalProvider == null || arguments == null || arguments.length < 1) {
                return null;
            }
            String key = PolyglotValueUtil.asString(arguments[0], null);
            if (key == null || key.isBlank()) {
                return null;
            }
            return finalProvider.getData(key);
        });

        obj.putMember("player", playerObj);

        // World context
        Value worldObj = jsContext.eval("js", "({})");
        worldObj.putMember("getBlock", (ProxyExecutable) arguments -> {
            if (finalProvider == null || arguments == null || arguments.length < 3) {
                return "minecraft:air";
            }
            double x = PolyglotValueUtil.asDouble(arguments[0], Double.NaN);
            double y = PolyglotValueUtil.asDouble(arguments[1], Double.NaN);
            double z = PolyglotValueUtil.asDouble(arguments[2], Double.NaN);
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                return "minecraft:air";
            }
            return finalProvider.getBlock(x, y, z);
        });
        worldObj.putMember("isInZone", (ProxyExecutable) arguments -> {
            if (finalProvider == null || arguments == null || arguments.length < 4) {
                return false;
            }
            double x = PolyglotValueUtil.asDouble(arguments[0], Double.NaN);
            double y = PolyglotValueUtil.asDouble(arguments[1], Double.NaN);
            double z = PolyglotValueUtil.asDouble(arguments[2], Double.NaN);
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                return false;
            }
            String zoneId = PolyglotValueUtil.asString(arguments[3], null);
            if (zoneId == null || zoneId.isBlank()) {
                return false;
            }
            return finalProvider.isInZone(x, y, z, zoneId);
        });

        obj.putMember("world", worldObj);

        return obj;
    }

    private PlayerState parseJsState(Value result, PlayerState fallback) {
        return PlayerStateScriptCodec.read(result, fallback);
    }
}
