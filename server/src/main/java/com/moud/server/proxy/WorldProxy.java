package com.moud.server.proxy;

import com.moud.api.math.Quaternion;
import com.moud.api.math.Vector3;
import com.moud.server.api.exception.APIException;
import com.moud.server.api.validation.APIValidator;
import com.moud.server.entity.ScriptedEntity;
import com.moud.server.instance.InstanceManager;
import com.moud.server.raycast.RaycastResult;
import com.moud.server.raycast.RaycastUtil;
import com.moud.server.physics.PhysicsService;
import com.moud.server.ts.TsExpose;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.Player;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.block.Block;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

@TsExpose
public class WorldProxy {
    private Instance instance;
    private final APIValidator validator;

    public WorldProxy() {
        this.validator = new APIValidator();
        this.instance = null;
    }

    public WorldProxy(Instance instance) {
        this.validator = new APIValidator();
        this.instance = instance;
    }

    public WorldProxy createInstance() {
        if (instance == null) {
            instance = InstanceManager.getInstance().getDefaultInstance();
        }
        return this;
    }

    private Instance requireInstance() {
        if (instance == null) {
            throw new APIException("INVALID_INSTANCE", "World is not initialized yet. Call createInstance() first.");
        }
        return instance;
    }

    @HostAccess.Export
    public WorldProxy setFlatGenerator() {
        if (!(instance instanceof InstanceContainer)) {
            throw new APIException("INVALID_INSTANCE_TYPE", "Cannot set generator on non-container instance");
        }
        ((InstanceContainer) instance).setGenerator(unit -> {
            unit.modifier().fillHeight(0, 1, Block.BEDROCK);
            unit.modifier().fillHeight(1, 64, Block.GRASS_BLOCK);
        });
        return this;
    }

    @HostAccess.Export
    public WorldProxy setVoidGenerator() {
        if (!(instance instanceof InstanceContainer)) {
            throw new APIException("INVALID_INSTANCE_TYPE", "Cannot set generator on non-container instance");
        }
        ((InstanceContainer) instance).setGenerator(unit -> {});
        return this;
    }

    @HostAccess.Export
    public WorldProxy setSpawn(double x, double y, double z) {
        validator.validateCoordinates(x, y, z);
        Pos spawnPos = new Pos(x, y, z);

        if (requireInstance() instanceof InstanceContainer container) {
            container.setTag(InstanceManager.SPAWN_TAG, spawnPos);
        } else {
            throw new APIException("INVALID_INSTANCE_TYPE", "Cannot set spawn on non-container instance");
        }

        return this;
    }

    @HostAccess.Export
    public String getBlock(int x, int y, int z) {
        return requireInstance().getBlock(x, y, z).name();
    }

    @HostAccess.Export
    public void setBlock(int x, int y, int z, String blockId) {
        validator.validateBlockId(blockId);
        Block block = Block.fromNamespaceId(blockId);
        if (block == null) throw new APIException("INVALID_BLOCK_ID", "Unknown block ID: " + blockId);
        requireInstance().setBlock(x, y, z, block);
    }

    @HostAccess.Export
    public long getTime() {
        return requireInstance().getTime();
    }

    @HostAccess.Export
    public void setTime(long time) {
        requireInstance().setTime(time);
    }

    @HostAccess.Export
    public int getTimeRate() {
        return requireInstance().getTimeRate();
    }

    @HostAccess.Export
    public void setTimeRate(int timeRate) {
        if (timeRate < 0) {
            throw new APIException("INVALID_ARGUMENT", "Time rate must be zero or positive");
        }
        requireInstance().setTimeRate(timeRate);
    }

    @HostAccess.Export
    public int getTimeSynchronizationTicks() {
        return requireInstance().getTimeSynchronizationTicks();
    }

    @HostAccess.Export
    public void setTimeSynchronizationTicks(int ticks) {
        if (ticks < 0) {
            throw new APIException("INVALID_ARGUMENT", "Synchronization ticks must be zero or positive");
        }
        requireInstance().setTimeSynchronizationTicks(ticks);
    }

    @HostAccess.Export
    public ModelProxy createModel(Value options) {
        if (options == null || !options.hasMembers()) {
            throw new APIException("INVALID_ARGUMENT", "createModel requires an options object.");
        }

        String modelPath = options.hasMember("model") ? options.getMember("model").asString() : null;
        if (modelPath == null) {
            throw new APIException("INVALID_ARGUMENT", "createModel requires a 'model' property with the asset path.");
        }

        Vector3 position = options.hasMember("position") ? options.getMember("position").as(Vector3.class) : Vector3.zero();
        Quaternion rotation = options.hasMember("rotation") ? options.getMember("rotation").as(Quaternion.class) : Quaternion.identity();
        Vector3 scale = options.hasMember("scale") ? options.getMember("scale").as(Vector3.class) : Vector3.one();
        String texturePath = options.hasMember("texture") ? options.getMember("texture").asString() : null;

        ModelProxy model = new ModelProxy(instance, modelPath, position, rotation, scale, texturePath);

        double autoWidth = clampCollisionSize(scale.x);
        double autoHeight = clampCollisionSize(scale.y);
        double autoDepth = clampCollisionSize(scale.z);
        boolean collisionConfigured = false;

        if (options.hasMember("collision")) {
            Value collisionVal = options.getMember("collision");
            if (collisionVal.isBoolean()) {
                collisionConfigured = true;
                if (collisionVal.asBoolean()) {
                    model.setCollisionBox(autoWidth, autoHeight, autoDepth);
                }
            } else if (collisionVal.hasMembers()) {
                double w = collisionVal.hasMember("width") ? collisionVal.getMember("width").asDouble() : autoWidth;
                double h = collisionVal.hasMember("height") ? collisionVal.getMember("height").asDouble() : autoHeight;
                double d = collisionVal.hasMember("depth") ? collisionVal.getMember("depth").asDouble() : autoDepth;
                model.setCollisionBox(
                        clampCollisionSize(w),
                        clampCollisionSize(h),
                        clampCollisionSize(d)
                );
                collisionConfigured = true;
            }
        }

        if (!collisionConfigured) {
            model.setCollisionBox(autoWidth, autoHeight, autoDepth);
        }

        return model;
    }

    @HostAccess.Export
    public ModelProxy createPhysicsModel(Value options) {
        if (options == null || !options.hasMembers()) {
            throw new APIException("INVALID_ARGUMENT", "createPhysicsModel requires an options object.");
        }
        Value physicsOptions = options.hasMember("physics") ? options.getMember("physics") : null;
        if (physicsOptions == null || !physicsOptions.hasMembers()) {
            throw new APIException("INVALID_ARGUMENT", "createPhysicsModel requires a 'physics' object describing the body.");
        }

        ModelProxy model = createModel(options);

        double mass = physicsOptions.hasMember("mass") ? physicsOptions.getMember("mass").asDouble() : 5.0;
        Vector3 initialVelocity = physicsOptions.hasMember("linearVelocity")
                ? readVector3(physicsOptions.getMember("linearVelocity"), null)
                : null;

        double width = model.getCollisionWidth();
        double height = model.getCollisionHeight();
        double depth = model.getCollisionDepth();
        if (width <= 0) width = 1;
        if (height <= 0) height = 1;
        if (depth <= 0) depth = 1;

        Vector3 halfExtents = new Vector3(width / 2.0, height / 2.0, depth / 2.0);
        boolean playerPush = physicsOptions.hasMember("playerPush") && physicsOptions.getMember("playerPush").asBoolean();
        PhysicsService.getInstance().attachDynamicModel(model, halfExtents, (float) mass, initialVelocity, playerPush);
        return model;
    }

    private static double clampCollisionSize(double value) {
        double size = Math.abs(value);
        return size < 0.01 ? 0.01 : size;
    }

    @HostAccess.Export
    public MediaDisplayProxy createDisplay(Value options) {
        if (options == null || !options.hasMembers()) {
            throw new APIException("INVALID_ARGUMENT", "createDisplay requires an options object.");
        }

        Vector3 position = options.hasMember("position") ? readVector3(options.getMember("position"), Vector3.zero()) : Vector3.zero();
        Quaternion rotation = options.hasMember("rotation") ? readQuaternion(options.getMember("rotation"), Quaternion.identity()) : Quaternion.identity();
        Vector3 scale = options.hasMember("scale") ? readVector3(options.getMember("scale"), Vector3.one()) : Vector3.one();

        MediaDisplayProxy display = new MediaDisplayProxy(instance, position, rotation, scale);

        if (options.hasMember("anchor")) {
            applyAnchor(display, options.getMember("anchor"));
        }

        if (options.hasMember("content")) {
            applyContent(display, options.getMember("content"));
        }

        if (options.hasMember("playback")) {
            applyPlayback(display, options.getMember("playback"));
        }

        return display;
    }

    @HostAccess.Export
    public void spawnScriptedEntity(String entityType, double x, double y, double z, Value jsInstance) {
        EntityType type = EntityType.fromNamespaceId(entityType);
        if (type == null) throw new APIException("UNKNOWN_ENTITY_TYPE", "Unknown entity type: " + entityType);
        ScriptedEntity entity = new ScriptedEntity(type, jsInstance);
        entity.setInstance(instance, new Pos(x, y, z));
    }

    @HostAccess.Export
    public PlayerModelProxy createPlayerModel(Value options) {
        if (options == null || !options.hasMembers()) {
            throw new APIException("INVALID_ARGUMENT", "createPlayerModel requires an options object.");
        }

        Vector3 position = Vector3.zero();
        String skinUrl = "";

        try {
            position = readVector(options, "position", Vector3.zero());

            if (options.hasMember("skinUrl")) {
                skinUrl = options.getMember("skinUrl").asString();
            }

            PlayerModelProxy proxy = new PlayerModelProxy(position, skinUrl);
            if (options.hasMember("rotation")) {
                Value rot = options.getMember("rotation");
                float yaw = rot.hasMember("y") ? rot.getMember("y").asFloat() : 0f;
                float pitch = rot.hasMember("x") ? rot.getMember("x").asFloat() : 0f;
                proxy.setRotation(yaw, pitch);
            }
            return proxy;

        } catch (Exception e) {
            throw new APIException("MODEL_CREATION_FAILED", "Failed to create player model", e);
        }
    }

    @HostAccess.Export
    public TextProxy createText(Value options) {
        if (options == null || !options.hasMembers()) {
            throw new APIException("INVALID_ARGUMENT", "createText requires an options object.");
        }
        Vector3 position = readVector(options, "position", Vector3.zero());
        String content = options.hasMember("content") ? options.getMember("content").asString() : "";
        String billboard = options.hasMember("billboard") ? options.getMember("billboard").asString() : "fixed";

        TextProxy textProxy = new TextProxy(position, content, billboard);
        Entity textEntity = textProxy.getEntity();
        Pos initialPosition = new Pos(position.x, position.y, position.z, 0, 0);
        textEntity.setInstance(instance, initialPosition);

        if (options.hasMember("hitbox") && options.getMember("hitbox").hasMembers()) {
            Value hitboxValue = options.getMember("hitbox");
            double width = hitboxValue.hasMember("width") ? hitboxValue.getMember("width").asDouble() : 1.0;
            double height = hitboxValue.hasMember("height") ? hitboxValue.getMember("height").asDouble() : 1.0;
            textProxy.enableHitbox(width, height);
        }

        return textProxy;
    }

    private Vector3 readVector(Value options, String key, Vector3 fallback) {
        try {
            if (options.hasMember(key)) {
                Value v = options.getMember(key);
                if (v.isHostObject() && v.asHostObject() instanceof Vector3 vec) {
                    return vec;
                }
                if (v.hasMembers()) {
                    double x = v.hasMember("x") ? v.getMember("x").asDouble() : fallback.x;
                    double y = v.hasMember("y") ? v.getMember("y").asDouble() : fallback.y;
                    double z = v.hasMember("z") ? v.getMember("z").asDouble() : fallback.z;
                    return new Vector3(x, y, z);
                }
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }

    @HostAccess.Export
    public ProxyObject raycast(Value options) {
        validator.validateNotNull(options, "options");

        Vector3 originVec = options.getMember("origin").as(Vector3.class);
        Vector3 directionVec = options.getMember("direction").as(Vector3.class);
        double maxDistance = options.hasMember("maxDistance") ? options.getMember("maxDistance").asDouble() : 100.0;

        Point origin = new Pos(originVec.x, originVec.y, originVec.z);
        Vec direction = new Vec(directionVec.x, directionVec.y, directionVec.z);

        Predicate<Entity> filter = entity -> !(entity instanceof Player);
        if (options.hasMember("ignorePlayer")) {
            PlayerProxy playerToIgnore = options.getMember("ignorePlayer").as(PlayerProxy.class);
            filter = entity -> !entity.getUuid().toString().equals(playerToIgnore.getUuid());
        }

        RaycastResult result = RaycastUtil.performRaycast(instance, origin, direction, maxDistance, filter);

        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("didHit", result.didHit());
        resultMap.put("position", result.position());
        resultMap.put("normal", result.normal());
        resultMap.put("distance", result.distance());

        if (result.entity() != null) {
            resultMap.put("entity", new PlayerProxy((Player) result.entity()));
        } else {
            resultMap.put("entity", null);
        }

        if (result.block() != null) {
            resultMap.put("blockType", result.block().name());
        } else {
            resultMap.put("blockType", null);
        }

        return ProxyObject.fromMap(resultMap);
    }

    private void applyAnchor(MediaDisplayProxy display, Value anchorValue) {
        if (anchorValue == null || !anchorValue.hasMembers()) {
            return;
        }

        String type = anchorValue.hasMember("type") ? anchorValue.getMember("type").asString().toLowerCase(Locale.ROOT) : "free";
        Vector3 offset = anchorValue.hasMember("offset") ? readVector3(anchorValue.getMember("offset"), Vector3.zero()) : Vector3.zero();

        switch (type) {
            case "block" -> {
                int x = anchorValue.hasMember("x") ? anchorValue.getMember("x").asInt() : 0;
                int y = anchorValue.hasMember("y") ? anchorValue.getMember("y").asInt() : 0;
                int z = anchorValue.hasMember("z") ? anchorValue.getMember("z").asInt() : 0;
                display.setAnchorToBlock(x, y, z, offset);
            }
            case "entity", "player" -> {
                UUID targetUuid = null;
                if (anchorValue.hasMember("uuid")) {
                    targetUuid = UUID.fromString(anchorValue.getMember("uuid").asString());
                } else if (anchorValue.hasMember("player") && anchorValue.getMember("player").isHostObject()) {
                    Object host = anchorValue.getMember("player").asHostObject();
                    if (host instanceof PlayerProxy proxy) {
                        targetUuid = UUID.fromString(proxy.getUuid());
                    }
                } else if (anchorValue.hasMember("model") && anchorValue.getMember("model").isHostObject()) {
                    Object host = anchorValue.getMember("model").asHostObject();
                    if (host instanceof ModelProxy modelProxy) {
                        targetUuid = modelProxy.getEntity().getUuid();
                    }
                }

                if (targetUuid != null) {
                    display.setAnchorToEntity(targetUuid, offset);
                }
            }
            default -> display.clearAnchor();
        }
    }

    private void applyContent(MediaDisplayProxy display, Value contentValue) {
        if (contentValue == null || !contentValue.hasMembers()) {
            return;
        }

        String type = contentValue.hasMember("type") ? contentValue.getMember("type").asString().toLowerCase(Locale.ROOT) : "image";
        boolean loop = contentValue.hasMember("loop") && contentValue.getMember("loop").asBoolean();
        double fps = contentValue.hasMember("fps") ? contentValue.getMember("fps").asDouble() : 30.0;

        switch (type) {
            case "image", "texture" -> {
                if (contentValue.hasMember("source")) {
                    display.setImage(contentValue.getMember("source").asString());
                }
                display.setLoop(loop);
            }
            case "video", "stream", "url" -> {
                if (contentValue.hasMember("source")) {
                    display.setVideo(contentValue.getMember("source").asString(), fps, loop);
                }
            }
            case "frames", "sequence" -> {
                List<String> frames = contentValue.hasMember("frames")
                        ? readStringList(contentValue.getMember("frames"))
                        : List.of();
                if (!frames.isEmpty()) {
                    display.setFrameSequence(frames.toArray(new String[0]), fps, loop);
                }
            }
            default -> {
                if (contentValue.hasMember("source")) {
                    display.setImage(contentValue.getMember("source").asString());
                    display.setLoop(loop);
                }
            }
        }
    }

    private void applyPlayback(MediaDisplayProxy display, Value playbackValue) {
        if (playbackValue == null || !playbackValue.hasMembers()) {
            return;
        }

        if (playbackValue.hasMember("speed")) {
            display.setPlaybackSpeed(playbackValue.getMember("speed").asDouble());
        }

        if (playbackValue.hasMember("offset")) {
            display.seek(playbackValue.getMember("offset").asDouble());
        }

        if (playbackValue.hasMember("playing")) {
            boolean shouldPlay = playbackValue.getMember("playing").asBoolean();
            if (shouldPlay) {
                display.play();
            } else {
                display.pause();
            }
        }
    }

    private Vector3 readVector3(Value value, Vector3 fallback) {
        if (value == null) {
            return fallback;
        }
        if (value.isHostObject() && value.asHostObject() instanceof Vector3 vector) {
            return new Vector3(vector);
        }
        if (value.hasMembers()) {
            double x = value.hasMember("x") ? value.getMember("x").asDouble() : fallback.x;
            double y = value.hasMember("y") ? value.getMember("y").asDouble() : fallback.y;
            double z = value.hasMember("z") ? value.getMember("z").asDouble() : fallback.z;
            return new Vector3(x, y, z);
        }
        return fallback;
    }

    private Quaternion readQuaternion(Value value, Quaternion fallback) {
        if (value == null) {
            return fallback;
        }
        if (value.isHostObject() && value.asHostObject() instanceof Quaternion quaternion) {
            return new Quaternion(quaternion);
        }
        if (value.hasMembers()) {
            double pitch = value.hasMember("pitch") ? value.getMember("pitch").asDouble() : 0.0;
            double yaw = value.hasMember("yaw") ? value.getMember("yaw").asDouble() : 0.0;
            double roll = value.hasMember("roll") ? value.getMember("roll").asDouble() : 0.0;
            return Quaternion.fromEuler((float) pitch, (float) yaw, (float) roll);
        }
        return fallback;
    }

    private List<String> readStringList(Value value) {
        if (value == null || !value.hasArrayElements()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        long size = value.getArraySize();
        for (long i = 0; i < size; i++) {
            Value element = value.getArrayElement(i);
            if (element.isString()) {
                result.add(element.asString());
            } else if (element.isHostObject() && element.asHostObject() instanceof String s) {
                result.add(s);
            }
        }
        return result;
    }

    public Instance getInstance() {
        return instance;
    }
}
