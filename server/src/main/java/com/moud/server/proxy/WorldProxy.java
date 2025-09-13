package com.moud.server.proxy;

import com.moud.server.api.exception.APIException;
import com.moud.server.api.validation.APIValidator;
import com.moud.server.entity.ScriptedEntity;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
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

    public Instance getInstance() {
        return defaultInstance;
    }
}