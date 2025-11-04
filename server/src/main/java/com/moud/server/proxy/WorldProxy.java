package com.moud.server.proxy;

import com.moud.api.math.Quaternion;
import com.moud.api.math.Vector3;
import com.moud.server.api.exception.APIException;
import com.moud.server.api.validation.APIValidator;
import com.moud.server.entity.ScriptedEntity;
import com.moud.server.instance.InstanceManager;
import com.moud.server.raycast.RaycastResult;
import com.moud.server.raycast.RaycastUtil;
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

import java.util.Map;
import java.util.HashMap;
import java.util.function.Predicate;

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

        if (instance instanceof InstanceContainer container) {
            container.setTag(InstanceManager.SPAWN_TAG, spawnPos);
        } else {
            throw new APIException("INVALID_INSTANCE_TYPE", "Cannot set spawn on non-container instance");
        }

        return this;
    }

    @HostAccess.Export
    public String getBlock(int x, int y, int z) {
        return instance.getBlock(x, y, z).name();
    }

    @HostAccess.Export
    public void setBlock(int x, int y, int z, String blockId) {
        validator.validateBlockId(blockId);
        Block block = Block.fromNamespaceId(blockId);
        if (block == null) throw new APIException("INVALID_BLOCK_ID", "Unknown block ID: " + blockId);
        instance.setBlock(x, y, z, block);
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

        if (options.hasMember("collision")) {
            Value collisionVal = options.getMember("collision");
            if (collisionVal.isBoolean() && !collisionVal.asBoolean()) {
                // No collision
            } else if (collisionVal.hasMembers()) {
                double w = collisionVal.hasMember("width") ? collisionVal.getMember("width").asDouble() : 1.0;
                double h = collisionVal.hasMember("height") ? collisionVal.getMember("height").asDouble() : 1.0;
                double d = collisionVal.hasMember("depth") ? collisionVal.getMember("depth").asDouble() : 1.0;
                model.setCollisionBox(w, h, d);
            } else {
                // Default collision box
                model.setCollisionBox(1.0, 1.0, 1.0);
            }
        }

        return model;
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
            if (options.hasMember("position")) {
                Value posValue = options.getMember("position");
                if (posValue.isHostObject() && posValue.asHostObject() instanceof Vector3) {
                    position = posValue.as(Vector3.class);
                }
            }

            if (options.hasMember("skinUrl")) {
                skinUrl = options.getMember("skinUrl").asString();
            }

            return new PlayerModelProxy(position, skinUrl);

        } catch (Exception e) {
            throw new APIException("MODEL_CREATION_FAILED", "Failed to create player model", e);
        }
    }

    @HostAccess.Export
    public TextProxy createText(Value options) {
        if (options == null || !options.hasMembers()) {
            throw new APIException("INVALID_ARGUMENT", "createText requires an options object.");
        }
        Vector3 position = options.hasMember("position") ? options.getMember("position").as(Vector3.class) : Vector3.zero();
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

    public Instance getInstance() {
        return instance;
    }
}
