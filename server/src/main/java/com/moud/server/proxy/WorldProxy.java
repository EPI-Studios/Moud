package com.moud.server.proxy;

import com.moud.api.math.Vector3;
import com.moud.server.api.exception.APIException;
import com.moud.server.api.validation.APIValidator;
import com.moud.server.entity.ScriptedEntity;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.block.Block;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

public class WorldProxy {
    private static InstanceContainer defaultInstance;
    private final APIValidator validator;

    public WorldProxy() {
        this.validator = new APIValidator();
    }

    public WorldProxy createInstance() {
        if (defaultInstance == null) {
            defaultInstance = MinecraftServer.getInstanceManager().createInstanceContainer();
            MinecraftServer.getGlobalEventHandler().addListener(AsyncPlayerConfigurationEvent.class, event -> {
                event.setSpawningInstance(defaultInstance);
            });
        }
        return this;
    }

    @HostAccess.Export
    public WorldProxy setFlatGenerator() {
        defaultInstance.setGenerator(unit -> {
            unit.modifier().fillHeight(0, 1, Block.BEDROCK);
            unit.modifier().fillHeight(1, 64, Block.GRASS_BLOCK);
        });
        return this;
    }

    @HostAccess.Export
    public WorldProxy setVoidGenerator() {
        defaultInstance.setGenerator(unit -> {});
        return this;
    }

    @HostAccess.Export
    public WorldProxy setSpawn(double x, double y, double z) {
        validator.validateCoordinates(x, y, z);
        defaultInstance.setTag(net.minestom.server.tag.Tag.Transient("spawn"), new Pos(x, y, z));
        MinecraftServer.getGlobalEventHandler().addListener(AsyncPlayerConfigurationEvent.class, event -> {
            event.getPlayer().setRespawnPoint(new Pos(x, y, z));
        });
        return this;
    }

    @HostAccess.Export
    public String getBlock(int x, int y, int z) {
        return defaultInstance.getBlock(x, y, z).name();
    }

    @HostAccess.Export
    public void setBlock(int x, int y, int z, String blockId) {
        validator.validateBlockId(blockId);
        Block block = Block.fromNamespaceId(blockId);
        if (block == null) throw new APIException("INVALID_BLOCK_ID", "Unknown block ID: " + blockId);
        defaultInstance.setBlock(x, y, z, block);
    }

    @HostAccess.Export
    public void spawnScriptedEntity(String entityType, double x, double y, double z, Value jsInstance) {
        EntityType type = EntityType.fromNamespaceId(entityType);
        if (type == null) throw new APIException("UNKNOWN_ENTITY_TYPE", "Unknown entity type: " + entityType);
        ScriptedEntity entity = new ScriptedEntity(type, jsInstance);
        entity.setInstance(defaultInstance, new Pos(x, y, z));
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
        textEntity.setInstance(defaultInstance, new Pos(position.x, position.y, position.z));

        return textProxy;
    }

    public Instance getInstance() {
        return defaultInstance;
    }
}