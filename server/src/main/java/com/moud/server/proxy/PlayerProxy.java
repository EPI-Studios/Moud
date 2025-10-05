package com.moud.server.proxy;

import com.moud.api.math.Vector3;
import com.moud.network.MoudPackets;
import com.moud.server.api.exception.APIException;
import com.moud.server.api.validation.APIValidator;
import com.moud.server.movement.ServerMovementHandler;
import com.moud.server.network.ServerNetworkManager;
import com.moud.server.shared.api.SharedValueApiProxy;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Player;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class PlayerProxy {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlayerProxy.class);
    private final Player player;
    private final ClientProxy client;
    private final APIValidator validator;
    private final SharedValueApiProxy sharedValues;
    private final PlayerUIProxy ui;
    private final CursorProxy cursor;
    @HostAccess.Export public final PlayerWindowProxy window;

    @HostAccess.Export
    public final CameraLockProxy camera;

    public PlayerProxy(Player player) {
        this.player = player;
        this.client = new ClientProxy(player);
        this.validator = new APIValidator();
        this.sharedValues = new SharedValueApiProxy(player);
        this.camera = new CameraLockProxy(player);
        this.ui = new PlayerUIProxy(player);
        this.cursor = new CursorProxy(player);
        this.window = new PlayerWindowProxy(player);

    }

    @HostAccess.Export
    public String getName() {
        return player.getUsername();
    }

    @HostAccess.Export
    public String getUuid() {
        return player.getUuid().toString();
    }

    @HostAccess.Export
    public void sendMessage(String message) {
        validator.validateString(message, "message");
        player.sendMessage(message);
    }

    @HostAccess.Export
    public void kick(String reason) {
        validator.validateString(reason, "reason");
        player.kick(reason);
    }

    @HostAccess.Export
    public boolean isOnline() {
        return player.isOnline();
    }

    @HostAccess.Export
    public ClientProxy getClient() {
        return client;
    }

    @HostAccess.Export
    public Vector3 getPosition() {
        Pos pos = player.getPosition();
        return new Vector3(pos.x(), pos.y(), pos.z());
    }

    @HostAccess.Export
    public Vector3 getDirection() {
        Vec dir = player.getPosition().direction();
        return new Vector3(dir.x(), dir.y(), dir.z());
    }

    @HostAccess.Export
    public Vector3 getCameraDirection() {
        return com.moud.server.player.PlayerCameraManager.getInstance().getCameraDirection(player);
    }

    @HostAccess.Export
    public Vector3 getHeadRotation() {
        Pos pos = player.getPosition();
        return new Vector3(pos.yaw(), pos.pitch(), 0.0f);
    }

    @HostAccess.Export
    public float getYaw() {
        return player.getPosition().yaw();
    }

    @HostAccess.Export
    public float getPitch() {
        return player.getPosition().pitch();
    }

    @HostAccess.Export
    public void teleport(double x, double y, double z) {
        validator.validateCoordinates(x, y, z);
        MinecraftServer.getSchedulerManager().scheduleNextTick(() -> {
            if (player.isOnline() && player.getInstance() != null) {
                player.teleport(new Pos(x, y, z));
            }
        });
    }

    @HostAccess.Export
    public SharedValueApiProxy getShared() {
        return sharedValues;
    }

    @HostAccess.Export
    public PlayerUIProxy getUi() {
        return ui;
    }

    @HostAccess.Export
    public CursorProxy getCursor() {
        return cursor;
    }

    @HostAccess.Export
    public boolean isMoving() {
        ServerMovementHandler.PlayerMovementState state = ServerMovementHandler.getInstance().getPlayerState(player);

        if (state == null) {
            return false;
        }

        return state.isMoving();
    }

    @HostAccess.Export
    public void setVanished(boolean vanished) {
        player.setInvisible(vanished);
    }

    @HostAccess.Export
    public void setPartConfig(String partName, Value options) {
        if (!player.isOnline()) return;

        validator.validateString(partName, "partName");
        if (options == null || !options.hasMembers()) {
            throw new APIException("INVALID_ARGUMENT", "Options object cannot be null or empty for setPartConfig.");
        }

        try {
            Map<String, Object> properties = new HashMap<>();
            if (options.hasMember("position")) properties.put("position", convertVector3(options.getMember("position")));
            if (options.hasMember("rotation")) properties.put("rotation", convertVector3(options.getMember("rotation")));
            if (options.hasMember("scale")) properties.put("scale", convertVector3(options.getMember("scale")));
            if (options.hasMember("visible")) properties.put("visible", options.getMember("visible").asBoolean());
            if (options.hasMember("overrideAnimation")) properties.put("overrideAnimation", options.getMember("overrideAnimation").asBoolean());

            if (options.hasMember("interpolation")) {
                Value interpolationValue = options.getMember("interpolation");
                Map<String, Object> interpolationSettings = new HashMap<>();
                if (interpolationValue.hasMember("enabled")) interpolationSettings.put("enabled", interpolationValue.getMember("enabled").asBoolean());
                if (interpolationValue.hasMember("duration")) interpolationSettings.put("duration", interpolationValue.getMember("duration").asLong());
                if (interpolationValue.hasMember("easing")) interpolationSettings.put("easing", interpolationValue.getMember("easing").asString());
                if (interpolationValue.hasMember("speed")) interpolationSettings.put("speed", interpolationValue.getMember("speed").asFloat());
                properties.put("interpolation", interpolationSettings);
            }

            MoudPackets.S2C_SetPlayerPartConfigPacket packet = new MoudPackets.S2C_SetPlayerPartConfigPacket(
                    player.getUuid(), partName, properties);
            ServerNetworkManager.getInstance().send(player, packet);
        } catch (Exception e) {
            LOGGER.error("Failed to set part configuration for player {}", player.getUsername(), e);
            throw new APIException("PART_CONFIG_FAILED", "Could not apply part configuration.", e);
        }
    }

    @HostAccess.Export
    public void setInterpolationSettings(Value settings) {
        if (!player.isOnline()) return;
        try {
            Map<String, Object> interpolationData = new HashMap<>();
            if (settings.hasMember("enabled")) interpolationData.put("enabled", settings.getMember("enabled").asBoolean());
            if (settings.hasMember("duration")) interpolationData.put("duration", settings.getMember("duration").asLong());
            if (settings.hasMember("easing")) interpolationData.put("easing", settings.getMember("easing").asString());
            if (settings.hasMember("speed")) interpolationData.put("speed", settings.getMember("speed").asFloat());

            MoudPackets.InterpolationSettingsPacket packet = new MoudPackets.InterpolationSettingsPacket(player.getUuid(), interpolationData);
            ServerNetworkManager.getInstance().send(player, packet);
        } catch (Exception e) {
            LOGGER.error("Failed to set interpolation settings for player {}", player.getUsername(), e);
            throw new APIException("INTERPOLATION_SETTINGS_FAILED", "Could not apply interpolation settings.", e);
        }
    }

    @HostAccess.Export
    public void playAnimation(String animationId) {
        if (player.isOnline()) {
            ServerNetworkManager.getInstance().send(player, new MoudPackets.S2C_PlayPlayerAnimationPacket(animationId));
        }
    }

    @HostAccess.Export
    public void pointToPosition(Vector3 targetPosition, Value options) {
        Vector3 playerPos = new Vector3(player.getPosition().x(), player.getPosition().y(), player.getPosition().z());
        float playerYaw = player.getPosition().yaw();

        double dirX = targetPosition.x - playerPos.x;
        double dirZ = targetPosition.z - playerPos.z;
        double dirY = targetPosition.y - (playerPos.y + 1.62);

        double horizontalDistance = Math.sqrt(dirX * dirX + dirZ * dirZ);
        double worldYaw = Math.toDegrees(Math.atan2(-dirX, dirZ));

        double armYaw = worldYaw - playerYaw;
        while(armYaw <= -180) armYaw += 360;
        while(armYaw > 180) armYaw -= 360;

        double armPitch = -Math.toDegrees(Math.atan2(dirY, horizontalDistance));

        Map<String, Object> properties = new HashMap<>();
        properties.put("rotation", new Vector3(armPitch, armYaw, 0));
        properties.put("overrideAnimation", true);

        if (options != null && options.hasMembers() && options.hasMember("interpolation")) {
            Value interpolationValue = options.getMember("interpolation");
            Map<String, Object> interpolationSettings = new HashMap<>();
            if (interpolationValue.hasMember("enabled")) interpolationSettings.put("enabled", interpolationValue.getMember("enabled").asBoolean());
            if (interpolationValue.hasMember("duration")) interpolationSettings.put("duration", interpolationValue.getMember("duration").asLong());
            if (interpolationValue.hasMember("easing")) interpolationSettings.put("easing", interpolationValue.getMember("easing").asString());
            properties.put("interpolation", interpolationSettings);
        }

        callSetPartConfig("right_arm", properties);
        callSetPartConfig("left_arm", properties);
    }

    @HostAccess.Export
    public void setFirstPersonConfig(Value config) {
        if (!player.isOnline()) return;
        try {
            Map<String, Object> fpConfig = new HashMap<>();
            if (config.hasMember("showRightArm")) fpConfig.put("showRightArm", config.getMember("showRightArm").asBoolean());
            if (config.hasMember("showLeftArm")) fpConfig.put("showLeftArm", config.getMember("showLeftArm").asBoolean());
            if (config.hasMember("showRightItem")) fpConfig.put("showRightItem", config.getMember("showRightItem").asBoolean());
            if (config.hasMember("showLeftItem")) fpConfig.put("showLeftItem", config.getMember("showLeftItem").asBoolean());
            if (config.hasMember("showArmor")) fpConfig.put("showArmor", config.getMember("showArmor").asBoolean());

            MoudPackets.FirstPersonConfigPacket packet = new MoudPackets.FirstPersonConfigPacket(player.getUuid(), fpConfig);
            ServerNetworkManager.getInstance().send(player, packet);
        } catch (Exception e) {
            LOGGER.error("Failed to set first person configuration for player {}", player.getUsername(), e);
            throw new APIException("FIRST_PERSON_CONFIG_FAILED", "Could not apply first person configuration.", e);
        }
    }

    @HostAccess.Export
    public void resetAllParts() {
        Vector3 zero = new Vector3(0, 0, 0);
        Vector3 defaultScale = new Vector3(1, 1, 1);
        String[] parts = {"head", "body", "right_arm", "left_arm", "right_leg", "left_leg"};
        for (String part : parts) {
            Map<String, Object> properties = new HashMap<>();
            properties.put("rotation", zero);
            properties.put("position", zero);
            properties.put("scale", defaultScale);
            properties.put("visible", true);
            properties.put("overrideAnimation", false);
            callSetPartConfig(part, properties);
        }
    }

    @HostAccess.Export
    public boolean isWalking() {
        ServerMovementHandler.PlayerMovementState state = ServerMovementHandler.getInstance().getPlayerState(player);
        return state != null && state.isMoving() && !state.sprinting() && !state.sneaking();
    }

    @HostAccess.Export
    public boolean isRunning() {
        ServerMovementHandler.PlayerMovementState state = ServerMovementHandler.getInstance().getPlayerState(player);
        return state != null && state.sprinting();
    }

    @HostAccess.Export
    public boolean isSneaking() {
        ServerMovementHandler.PlayerMovementState state = ServerMovementHandler.getInstance().getPlayerState(player);
        return state != null && state.sneaking();
    }

    @HostAccess.Export
    public boolean isJumping() {
        ServerMovementHandler.PlayerMovementState state = ServerMovementHandler.getInstance().getPlayerState(player);
        return state != null && state.jumping();
    }

    @HostAccess.Export
    public boolean isOnGround() {
        ServerMovementHandler.PlayerMovementState state = ServerMovementHandler.getInstance().getPlayerState(player);
        return state != null && state.onGround();
    }

    @HostAccess.Export
    public String getMovementType() {
        ServerMovementHandler.PlayerMovementState state = ServerMovementHandler.getInstance().getPlayerState(player);
        return state != null ? state.getMovementType() : "unknown";
    }

    @HostAccess.Export
    public String getMovementDirection() {
        ServerMovementHandler.PlayerMovementState state = ServerMovementHandler.getInstance().getPlayerState(player);
        return state != null ? state.getMovementDirection() : "none";
    }

    @HostAccess.Export
    public float getMovementSpeed() {
        ServerMovementHandler.PlayerMovementState state = ServerMovementHandler.getInstance().getPlayerState(player);
        return state != null ? state.speed() : 0.0f;
    }

    private void callSetPartConfig(String partName, Map<String, Object> properties) {
        try {
            MoudPackets.S2C_SetPlayerPartConfigPacket packet = new MoudPackets.S2C_SetPlayerPartConfigPacket(player.getUuid(), partName, properties);
            ServerNetworkManager.getInstance().send(player, packet);
        } catch (Exception e) {
            LOGGER.error("Failed to set part configuration for player {}", player.getUsername(), e);
        }
    }

    private Vector3 convertVector3(Value vectorValue) {
        if (vectorValue.isHostObject() && vectorValue.asHostObject() instanceof Vector3) {
            return (Vector3) vectorValue.asHostObject();
        }
        if (vectorValue.hasMembers()) {
            double x = vectorValue.hasMember("x") ? vectorValue.getMember("x").asDouble() : 0.0;
            double y = vectorValue.hasMember("y") ? vectorValue.getMember("y").asDouble() : 0.0;
            double z = vectorValue.hasMember("z") ? vectorValue.getMember("z").asDouble() : 0.0;
            return new Vector3(x, y, z);
        }
        return Vector3.zero();
    }
}