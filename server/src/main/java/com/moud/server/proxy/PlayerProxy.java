package com.moud.server.proxy;

import com.moud.server.ts.TsExpose;
import com.moud.api.math.Vector3;
import com.moud.network.MoudPackets;
import com.moud.server.api.exception.APIException;
import com.moud.server.api.validation.APIValidator;
import com.moud.server.movement.ServerMovementHandler;
import com.moud.server.network.ServerNetworkManager;
import com.moud.server.shared.api.SharedValueApiProxy;
import com.moud.server.entity.ModelManager;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Player;
import net.minestom.server.collision.BoundingBox;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

@TsExpose
public class PlayerProxy {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlayerProxy.class);
    private final Player player;
    private final ClientProxy client;
    private final APIValidator validator;
    private final SharedValueApiProxy sharedValues;
    @HostAccess.Export
    public final PlayerUIProxy ui;
    @HostAccess.Export
    public final CursorProxy cursor;
    @HostAccess.Export
    public final PlayerWindowProxy window;

    @HostAccess.Export
    public final CameraLockProxy camera;
    @HostAccess.Export
    public final PlayerAudioProxy audio;

    public PlayerProxy(Player player) {
        this.player = player;
        this.client = new ClientProxy(player);
        this.validator = new APIValidator();
        this.sharedValues = new SharedValueApiProxy(player);
        this.camera = new CameraLockProxy(player);
        this.ui = new PlayerUIProxy(player);
        this.cursor = new CursorProxy(player);
        this.window = new PlayerWindowProxy(player);
        this.audio = new PlayerAudioProxy(player);
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
    public CursorProxy getCursor() {
        return cursor;
    }

    @HostAccess.Export
    public PlayerUIProxy getUi() {
        return ui;
    }

    @HostAccess.Export
    public PlayerWindowProxy getWindow() {
        return window;
    }

    @HostAccess.Export
    public CameraLockProxy getCamera() {
        return camera;
    }

    @HostAccess.Export
    public PlayerAudioProxy getAudio() {
        return audio;
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
        setPartConfigWithVisibility(partName, options, "self");
    }

    @HostAccess.Export
    public void setPartConfigWithVisibility(String partName, Value options, String visibility) {
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

            // Send based on visibility
            ServerNetworkManager networkManager = ServerNetworkManager.getInstance();
            switch (visibility.toLowerCase()) {
                case "all":
                    networkManager.broadcast(packet);
                    break;
                case "others":
                    networkManager.broadcastExcept(packet, player);
                    break;
                case "self":
                default:
                    networkManager.send(player, packet);
                    break;
            }
        } catch (Exception e) {
            LOGGER.error("Failed to set part configuration for player {}", player.getUsername(), e);
            throw new APIException("PART_CONFIG_FAILED", "Could not apply part configuration.", e);
        }
    }

    @HostAccess.Export
    public void setPartConfigForPlayers(String partName, Value options, Value playerList) {
        if (!player.isOnline()) return;

        validator.validateString(partName, "partName");
        if (options == null || !options.hasMembers()) {
            throw new APIException("INVALID_ARGUMENT", "Options object cannot be null or empty.");
        }

        try {
            Map<String, Object> properties = buildPartConfigProperties(options);
            MoudPackets.S2C_SetPlayerPartConfigPacket packet = new MoudPackets.S2C_SetPlayerPartConfigPacket(
                    player.getUuid(), partName, properties);

            // Extract player list
            java.util.List<net.minestom.server.entity.Player> targetPlayers = new java.util.ArrayList<>();
            if (playerList.hasArrayElements()) {
                long size = playerList.getArraySize();
                for (long i = 0; i < size; i++) {
                    Value playerValue = playerList.getArrayElement(i);
                    if (playerValue.isHostObject() && playerValue.asHostObject() instanceof PlayerProxy) {
                        PlayerProxy proxy = (PlayerProxy) playerValue.asHostObject();
                        targetPlayers.add(proxy.player);
                    }
                }
            }

            ServerNetworkManager.getInstance().sendToPlayers(packet, targetPlayers);
        } catch (Exception e) {
            LOGGER.error("Failed to set part configuration for specific players", e);
            throw new APIException("PART_CONFIG_FAILED", "Could not apply part configuration.", e);
        }
    }

    private Map<String, Object> buildPartConfigProperties(Value options) {
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
        return properties;
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
    public void playAnimation(String animationId, Value options) {
        if (!player.isOnline()) {
            return;
        }

        boolean fade = false;
        int durationMs = 300; // Default fade duration

        if (options != null && options.hasMembers()) {
            if (options.hasMember("fade")) {
                fade = options.getMember("fade").asBoolean();
            }
            if (options.hasMember("fadeDuration")) {
                durationMs = options.getMember("fadeDuration").asInt();
            }
        }

        ServerNetworkManager networkManager = ServerNetworkManager.getInstance();
        if (fade) {
            int durationTicks = Math.max(1, durationMs / 50); // Convert ms to ticks (20tps)
            networkManager.send(player, new MoudPackets.S2C_PlayPlayerAnimationWithFadePacket(animationId, durationTicks));
        } else {
            networkManager.send(player, new MoudPackets.S2C_PlayPlayerAnimationPacket(animationId));
        }
    }

    @HostAccess.Export
    public void playAnimation(String animationId) {
        playAnimation(animationId, null);
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

    /**
     * Detects if the player is standing near a ledge (no support ahead).
     * Considers blocks and model colliders.
     * @param forwardDistance how far ahead to probe from the player's feet (blocks). Default 0.6 if <= 0.
     * @param dropThreshold maximum vertical gap to still count as support. Default 0.75 if <= 0.
     * @return true if there is support under the current feet but missing support ahead.
     */
    @HostAccess.Export
    public boolean isAtEdge(double forwardDistance, double dropThreshold) {
        if (!player.isOnline()) return false;
        Instance instance = player.getInstance();
        if (instance == null) return false;

        double f = forwardDistance > 0 ? forwardDistance : 0.6;
        double drop = dropThreshold > 0 ? dropThreshold : 0.75;

        Pos pos = player.getPosition();
        double footY = pos.y() - 0.1;

        if (!hasSupport(instance, pos.x(), footY, pos.z(), drop)) {
            return false;
        }

        Vec dir = pos.direction();
        Vec horizontal = new Vec(dir.x(), 0, dir.z());
        if (horizontal.lengthSquared() < 1e-6) {
            horizontal = new Vec(1, 0, 0);
        } else {
            horizontal = horizontal.normalize();
        }
        Vec side = new Vec(-horizontal.z(), 0, horizontal.x());
        double halfWidth = 0.35;

        double[][] probes = new double[][]{
                {f, 0},
                {f, halfWidth},
                {f, -halfWidth}
        };

        for (double[] probe : probes) {
            double forward = probe[0];
            double sideways = probe[1];
            double px = pos.x() + horizontal.x() * forward + side.x() * sideways;
            double pz = pos.z() + horizontal.z() * forward + side.z() * sideways;
            if (!hasSupport(instance, px, footY, pz, drop)) {
                return true;
            }
        }
        return false;
    }

    @HostAccess.Export
    public boolean isAtEdge() {
        return isAtEdge(0.6, 0.75);
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

    private boolean hasSupport(Instance instance, double x, double y, double z, double dropThreshold) {
        int bx = (int) Math.floor(x);
        int by = (int) Math.floor(y - dropThreshold);
        int bz = (int) Math.floor(z);
        Block block = instance.getBlock(bx, by, bz);
        if (!block.isAir() && !block.isLiquid()) {
            return true;
        }

        for (var model : ModelManager.getInstance().getAllModels()) {
            var entity = model.getEntity();
            if (entity == null || entity.getInstance() != instance) continue;
            BoundingBox bb = entity.getBoundingBox();
            if (bb == null) continue;
            Pos p = entity.getPosition();
            double minX = p.x() + bb.minX();
            double minY = p.y() + bb.minY();
            double minZ = p.z() + bb.minZ();
            double maxX = p.x() + bb.maxX();
            double maxY = p.y() + bb.maxY();
            double maxZ = p.z() + bb.maxZ();
            if (x >= minX && x <= maxX && z >= minZ && z <= maxZ) {
                double gap = y - maxY;
                if (gap >= -0.05 && gap <= dropThreshold + 0.05 && y >= minY - 0.1) {
                    return true;
                }
            }
        }
        return false;
    }
}
