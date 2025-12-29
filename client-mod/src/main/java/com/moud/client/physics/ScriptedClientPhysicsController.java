package com.moud.client.physics;

import com.moud.api.collision.AABB;
import com.moud.api.physics.player.CollisionWorld;
import com.moud.api.physics.player.PlayerController;
import com.moud.api.physics.player.PlayerInput;
import com.moud.api.physics.player.PlayerPhysicsConfig;
import com.moud.api.physics.player.PlayerPhysicsController;
import com.moud.api.physics.player.PlayerState;
import com.moud.client.util.PolyglotValueUtil;
import com.moud.client.zone.ClientZoneManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ScriptedClientPhysicsController implements PlayerPhysicsController {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScriptedClientPhysicsController.class);

    private final String id;
    private final Value stepFunction;
    private final Context jsContext;
    private volatile boolean warned;

    public ScriptedClientPhysicsController(String id, Value stepFunction, Context jsContext) {
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
        if (stepFunction == null || jsContext == null || !canExecuteSafely(stepFunction)) {
            return defaultStep(current, input, config, world, dt);
        }

        boolean entered = false;
        try {
            if (isContextClosedSafely(jsContext)) {
                return defaultStep(current, input, config, world, dt);
            }
            jsContext.enter();
            entered = true;

            Value jsState = createJsState(current);
            Value jsInput = createJsInput(input);
            Value jsConfig = createJsConfig(config);
            Value jsCollision = createJsCollisionWorld(world);
            Value jsCtx = createJsContext(dt);

            Value result = stepFunction.execute(jsState, jsInput, jsConfig, jsCollision, jsCtx);

            return parseJsState(result, current);
        } catch (Throwable t) {
            logFallback(t);
            return defaultStep(current, input, config, world, dt);
        } finally {
            if (entered) {
                try {
                    jsContext.leave();
                } catch (Throwable t) {
                    logFallback(t);
                }
            }
        }
    }

    private PlayerState defaultStep(PlayerState current, PlayerInput input, PlayerPhysicsConfig config,
                                    CollisionWorld world, float dt) {
        return PlayerController.step(current, input, config, world, dt);
    }

    private boolean canExecuteSafely(Value value) {
        try {
            return value.canExecute();
        } catch (Throwable t) {
            logFallback(t);
            return false;
        }
    }

    private boolean isContextClosedSafely(Context context) {
        try {
            java.lang.reflect.Method isClosed = context.getClass().getMethod("isClosed");
            Object result = isClosed.invoke(context);
            return result instanceof Boolean b && b;
        } catch (NoSuchMethodException ignored) {
            return false;
        } catch (Throwable t) {
            return false;
        }
    }

    private void logFallback(Throwable t) {
        if (!warned) {
            warned = true;
            LOGGER.warn("Client scripted physics '{}' unavailable; using default physics", id, t);
        } else {
            LOGGER.debug("Client scripted physics '{}' unavailable; using default physics", id, t);
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

    private Value createJsContext(float dt) {
        Value obj = jsContext.eval("js", "({})");
        obj.putMember("dt", dt);
        obj.putMember("tick", System.currentTimeMillis() / 50);

        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;

        // Player context
        Value playerObj = jsContext.eval("js", "({})");
        playerObj.putMember("uuid", player != null ? player.getUuidAsString() : "");

        playerObj.putMember("hasItem", (ProxyExecutable) arguments -> {
            if (player == null || arguments == null || arguments.length < 1 || arguments[0] == null) {
                return false;
            }
            String itemId = PolyglotValueUtil.asString(arguments[0], null);
            if (itemId == null || itemId.isBlank()) {
                return false;
            }
            Identifier id = Identifier.tryParse(itemId);
            if (id == null) {
                return false;
            }
            for (ItemStack stack : player.getInventory().main) {
                if (Registries.ITEM.getId(stack.getItem()).equals(id)) {
                    return true;
                }
            }
            return false;
        });

        playerObj.putMember("getHealth", (ProxyExecutable) arguments ->
                player != null ? player.getHealth() : 20f);

        playerObj.putMember("hasEffect", (ProxyExecutable) arguments -> {
            if (player == null || arguments == null || arguments.length < 1 || arguments[0] == null) {
                return false;
            }
            String effectId = PolyglotValueUtil.asString(arguments[0], null);
            if (effectId == null || effectId.isBlank()) {
                return false;
            }
            Identifier id = Identifier.tryParse(effectId);
            if (id == null) {
                return false;
            }
            for (RegistryEntry<StatusEffect> entry : player.getActiveStatusEffects().keySet()) {
                Identifier activeId = Registries.STATUS_EFFECT.getId(entry.value());
                if (id.equals(activeId)) {
                    return true;
                }
            }
            return false;
        });

        playerObj.putMember("getData", (ProxyExecutable) arguments -> {
            // TODO: Hook into SharedValueManager
            return null;
        });

        obj.putMember("player", playerObj);

        // World context
        Value worldObj = jsContext.eval("js", "({})");
        worldObj.putMember("getBlock", (ProxyExecutable) arguments -> {
            if (client.world == null || arguments == null || arguments.length < 3) {
                return "minecraft:air";
            }
            double x = PolyglotValueUtil.asDouble(arguments[0], Double.NaN);
            double y = PolyglotValueUtil.asDouble(arguments[1], Double.NaN);
            double z = PolyglotValueUtil.asDouble(arguments[2], Double.NaN);
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                return "minecraft:air";
            }
            BlockPos pos = new BlockPos((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
            return Registries.BLOCK.getId(client.world.getBlockState(pos).getBlock()).toString();
        });

        worldObj.putMember("isInZone", (ProxyExecutable) arguments -> {
            if (arguments == null || arguments.length < 4) {
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

            return ClientZoneManager.isInZone(x, y, z, zoneId);
        });

        obj.putMember("world", worldObj);

        return obj;
    }

    private PlayerState parseJsState(Value result, PlayerState fallback) {
        return PlayerStateScriptCodec.read(result, fallback);
    }
}
