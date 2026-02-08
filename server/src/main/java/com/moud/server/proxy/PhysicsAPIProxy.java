package com.moud.server.proxy;

import com.github.stephengold.joltjni.Body;
import com.github.stephengold.joltjni.Vec3;
import com.moud.api.math.Vector3;
import com.moud.api.physics.player.PlayerPhysicsConfig;
import com.moud.api.physics.player.PlayerPhysicsControllers;
import com.moud.server.movement.PlayerMovementSimService;
import com.moud.server.api.exception.APIException;
import com.moud.server.physics.PhysicsService;
import com.moud.server.ts.TsExpose;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import java.util.UUID;

@TsExpose
public final class PhysicsAPIProxy {
    private final PhysicsService physicsService = PhysicsService.getInstance();

    @HostAccess.Export
    public void setPredictionMode(PlayerProxy player, boolean enabled) {
        if (player == null) {
            throw new APIException("INVALID_ARGUMENT", "setPredictionMode requires a player");
        }
        UUID uuid;
        try {
            uuid = UUID.fromString(player.getUuid());
        } catch (IllegalArgumentException e) {
            throw new APIException("INVALID_ARGUMENT", "Invalid player UUID: " + player.getUuid());
        }

        Player minestomPlayer = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(uuid);
        if (minestomPlayer == null || !minestomPlayer.isOnline()) {
            throw new APIException("PLAYER_OFFLINE", "Player is not online");
        }

        PlayerMovementSimService.getInstance().setPredictionMode(minestomPlayer, enabled);
    }

    @HostAccess.Export
    public void setPredictionMode(PlayerProxy player, boolean enabled, Value options) {
        if (player == null) {
            throw new APIException("INVALID_ARGUMENT", "setPredictionMode requires a player");
        }
        UUID uuid;
        try {
            uuid = UUID.fromString(player.getUuid());
        } catch (IllegalArgumentException e) {
            throw new APIException("INVALID_ARGUMENT", "Invalid player UUID: " + player.getUuid());
        }

        Player minestomPlayer = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(uuid);
        if (minestomPlayer == null || !minestomPlayer.isOnline()) {
            throw new APIException("PLAYER_OFFLINE", "Player is not online");
        }

        String controllerId = null;
        PlayerPhysicsConfig configOverride = null;
        if (options != null && !options.isNull() && options.hasMembers()) {
            PlayerPhysicsConfig baselineConfig = enabled ? PlayerPhysicsConfig.predictionDefaults() : PlayerPhysicsConfig.defaults();
            if (options.hasMember("controllerId")) {
                controllerId = options.getMember("controllerId").asString();
            } else if (options.hasMember("controller")) {
                controllerId = options.getMember("controller").asString();
            }

            Value configValue = options.hasMember("config") ? options.getMember("config") : null;
            if (configValue != null && !configValue.isNull()) {
                configOverride = readPlayerPhysicsConfig(configValue, baselineConfig);
            } else if (options.hasMember("speed") || options.hasMember("accel") || options.hasMember("gravity")) {
                configOverride = readPlayerPhysicsConfig(options, baselineConfig);
            }
        }

        if (enabled && controllerId != null && !controllerId.isBlank() && !PlayerPhysicsControllers.has(controllerId)) {
            throw new APIException("INVALID_ARGUMENT", "Unknown player physics controller: " + controllerId);
        }

        PlayerMovementSimService.getInstance().setPredictionMode(minestomPlayer, enabled, controllerId, configOverride);
    }

    @HostAccess.Export
    public long createDistanceConstraint(Value options) {
        if (options == null || !options.hasMembers()) {
            throw new APIException("INVALID_ARGUMENT", "createDistanceConstraint requires an options object");
        }

        Body bodyA = resolveBody(options, "a", "bodyA", true);
        Body bodyB = resolveBody(options, "b", "bodyB", false);

        Vector3 pointA = options.hasMember("pointA") ? readVector3(options.getMember("pointA"), null) : null;
        Vector3 pointB = options.hasMember("pointB") ? readVector3(options.getMember("pointB"), null) : null;

        double minDistance = options.hasMember("minDistance") ? options.getMember("minDistance").asDouble() : 0.0;
        double maxDistance = options.hasMember("maxDistance") ? options.getMember("maxDistance").asDouble() : 0.0;
        if (!options.hasMember("maxDistance")) {
            throw new APIException("INVALID_ARGUMENT", "createDistanceConstraint requires maxDistance");
        }

        if (pointA == null) {
            pointA = readBodyPosition(bodyA);
        }
        if (pointB == null) {
            pointB = bodyB != null ? readBodyPosition(bodyB) : pointA;
        }

        return physicsService.createDistanceConstraint(bodyA, bodyB, pointA, pointB, minDistance, maxDistance);
    }

    @HostAccess.Export
    public long createHingeConstraint(Value options) {
        if (options == null || !options.hasMembers()) {
            throw new APIException("INVALID_ARGUMENT", "createHingeConstraint requires an options object");
        }

        Body bodyA = resolveBody(options, "a", "bodyA", true);
        Body bodyB = resolveBody(options, "b", "bodyB", false);

        if (!options.hasMember("pivot")) {
            throw new APIException("INVALID_ARGUMENT", "createHingeConstraint requires pivot");
        }
        if (!options.hasMember("axis")) {
            throw new APIException("INVALID_ARGUMENT", "createHingeConstraint requires axis");
        }

        Vector3 pivot = readVector3(options.getMember("pivot"), Vector3.zero());
        Vector3 axis = readVector3(options.getMember("axis"), new Vector3(0, 1, 0));
        Vector3 normal = options.hasMember("normal") ? readVector3(options.getMember("normal"), null) : null;

        Double limitsMin = options.hasMember("limitsMin") ? options.getMember("limitsMin").asDouble() : null;
        Double limitsMax = options.hasMember("limitsMax") ? options.getMember("limitsMax").asDouble() : null;
        Double maxFrictionTorque = options.hasMember("maxFrictionTorque") ? options.getMember("maxFrictionTorque").asDouble() : null;

        return physicsService.createHingeConstraint(bodyA, bodyB, pivot, axis, normal, limitsMin, limitsMax, maxFrictionTorque);
    }

    @HostAccess.Export
    public long createFixedConstraint(Value options) {
        if (options == null || !options.hasMembers()) {
            throw new APIException("INVALID_ARGUMENT", "createFixedConstraint requires an options object");
        }

        Body bodyA = resolveBody(options, "a", "bodyA", true);
        Body bodyB = resolveBody(options, "b", "bodyB", false);

        Vector3 pivot = options.hasMember("pivot") ? readVector3(options.getMember("pivot"), Vector3.zero()) : readBodyPosition(bodyA);
        Vector3 axisX = options.hasMember("axisX") ? readVector3(options.getMember("axisX"), new Vector3(1, 0, 0)) : new Vector3(1, 0, 0);
        Vector3 axisY = options.hasMember("axisY") ? readVector3(options.getMember("axisY"), new Vector3(0, 1, 0)) : new Vector3(0, 1, 0);
        boolean autoDetectPoint = options.hasMember("autoDetectPoint") && options.getMember("autoDetectPoint").asBoolean();

        return physicsService.createFixedConstraint(bodyA, bodyB, pivot, axisX, axisY, autoDetectPoint);
    }

    @HostAccess.Export
    public boolean removeConstraint(long constraintId) {
        return physicsService.removeConstraint(constraintId);
    }

    @HostAccess.Export
    public void applyImpulse(Value target, Value impulse) {
        Body body = resolveBody(target, true);
        Vector3 vec = readVector3(impulse, null);
        if (vec == null) {
            throw new APIException("INVALID_ARGUMENT", "applyImpulse requires an impulse Vector3");
        }
        physicsService.applyImpulse(body, vec);
    }

    @HostAccess.Export
    public void setLinearVelocity(Value target, Value velocity) {
        Body body = resolveBody(target, true);
        Vector3 vec = readVector3(velocity, null);
        if (vec == null) {
            throw new APIException("INVALID_ARGUMENT", "setLinearVelocity requires a velocity Vector3");
        }
        physicsService.setLinearVelocity(body, vec);
    }

    @HostAccess.Export
    public Vector3 getLinearVelocity(Value target) {
        if (target == null || target.isNull() || !target.isHostObject()) {
            throw new APIException("INVALID_ARGUMENT", "getLinearVelocity requires a Model object");
        }
        Body body = resolveBodyNoEnable(target, true);
        Vec3 vel = body.getLinearVelocity();
        return new Vector3(vel.getX(), vel.getY(), vel.getZ());
    }

    private Body resolveBody(Value options, String primaryKey, String secondaryKey, boolean required) {
        Value raw = null;
        if (options.hasMember(primaryKey)) {
            raw = options.getMember(primaryKey);
        } else if (options.hasMember(secondaryKey)) {
            raw = options.getMember(secondaryKey);
        }

        if (raw == null || raw.isNull()) {
            if (required) {
                throw new APIException("INVALID_ARGUMENT", "Missing required body reference: " + primaryKey);
            }
            return null;
        }

        if (!raw.isHostObject()) {
            throw new APIException("INVALID_ARGUMENT", "Body references must be Model objects");
        }

        Object host = raw.asHostObject();
        if (host instanceof ModelProxy modelProxy) {
            Body body = physicsService.getModelPhysicsBody(modelProxy.getId());
            if (body == null) {
                throw new APIException("PHYSICS_NOT_AVAILABLE", "Model has no physics body (use createPhysicsModel or attachDynamic)");
            }
            return body;
        }

        throw new APIException("INVALID_ARGUMENT", "Unsupported body reference type: " + host.getClass().getName());
    }

    private Body resolveBody(Value raw, boolean required) {
        if (raw == null || raw.isNull()) {
            if (required) {
                throw new APIException("INVALID_ARGUMENT", "Missing required body reference");
            }
            return null;
        }

        if (!raw.isHostObject()) {
            throw new APIException("INVALID_ARGUMENT", "Body references must be Model objects");
        }

        Object host = raw.asHostObject();
        if (host instanceof ModelProxy modelProxy) {
            Body body = physicsService.getModelPhysicsBody(modelProxy.getId());
            if (body == null) {
                throw new APIException("PHYSICS_NOT_AVAILABLE", "Model has no physics body (use createPhysicsModel or attachDynamic)");
            }
            return body;
        }

        throw new APIException("INVALID_ARGUMENT", "Unsupported body reference type: " + host.getClass().getName());
    }

    private Body resolveBodyNoEnable(Value raw, boolean required) {
        if (raw == null || raw.isNull()) {
            if (required) {
                throw new APIException("INVALID_ARGUMENT", "Missing required body reference");
            }
            return null;
        }

        if (!raw.isHostObject()) {
            throw new APIException("INVALID_ARGUMENT", "Body references must be Model objects");
        }

        Object host = raw.asHostObject();
        if (host instanceof ModelProxy modelProxy) {
            Body body = physicsService.getModelPhysicsBody(modelProxy.getId());
            if (body == null) {
                throw new APIException("PHYSICS_NOT_AVAILABLE", "Model has no physics body (use createPhysicsModel or attachDynamic)");
            }
            return body;
        }

        throw new APIException("INVALID_ARGUMENT", "Unsupported body reference type: " + host.getClass().getName());
    }

    private static Vector3 readVector3(Value value, Vector3 fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            if (value.isHostObject() && value.asHostObject() instanceof Vector3 vec) {
                return vec;
            }
            if (value.hasMembers()) {
                double x = value.hasMember("x") ? value.getMember("x").asDouble() : (fallback != null ? fallback.x : 0.0);
                double y = value.hasMember("y") ? value.getMember("y").asDouble() : (fallback != null ? fallback.y : 0.0);
                double z = value.hasMember("z") ? value.getMember("z").asDouble() : (fallback != null ? fallback.z : 0.0);
                return new Vector3(x, y, z);
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }

    private static Vector3 readBodyPosition(Body body) {
        if (body == null) {
            return Vector3.zero();
        }
        var pos = body.getPosition();
        return new Vector3(
                ((Number) pos.getX()).doubleValue(),
                ((Number) pos.getY()).doubleValue(),
                ((Number) pos.getZ()).doubleValue()
        );
    }

    private static PlayerPhysicsConfig readPlayerPhysicsConfig(Value value, PlayerPhysicsConfig fallback) {
        if (value == null || value.isNull()) {
            return fallback;
        }
        if (value.isHostObject() && value.asHostObject() instanceof PlayerPhysicsConfig config) {
            return config;
        }
        if (!value.hasMembers()) {
            return fallback;
        }

        PlayerPhysicsConfig base = fallback != null ? fallback : PlayerPhysicsConfig.defaults();
        float speed = readFloat(value, "speed", base.speed());
        float accel = readFloat(value, "accel", base.accel());
        float friction = readFloat(value, "friction", base.friction());
        float airResistance = readFloat(value, "airResistance", base.airResistance());
        float gravity = readFloat(value, "gravity", base.gravity());
        float jumpForce = readFloat(value, "jumpForce", base.jumpForce());
        float stepHeight = readFloat(value, "stepHeight", base.stepHeight());
        float width = readFloat(value, "width", base.width());
        float height = readFloat(value, "height", base.height());
        float sprintMultiplier = readFloat(value, "sprintMultiplier", base.sprintMultiplier());
        float sneakMultiplier = readFloat(value, "sneakMultiplier", base.sneakMultiplier());

        return new PlayerPhysicsConfig(
                speed,
                accel,
                friction,
                airResistance,
                gravity,
                jumpForce,
                stepHeight,
                width,
                height,
                sprintMultiplier,
                sneakMultiplier
        );
    }

    private static float readFloat(Value object, String key, float fallback) {
        if (object != null && object.hasMember(key)) {
            try {
                Value raw = object.getMember(key);
                if (raw != null && raw.fitsInFloat()) {
                    return raw.asFloat();
                }
                if (raw != null && raw.isNumber()) {
                    return (float) raw.asDouble();
                }
            } catch (Exception ignored) {
            }
        }
        return fallback;
    }
}
