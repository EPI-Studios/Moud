package com.moud.server.proxy;

import com.moud.server.entity.ScriptedEntity;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.InstanceManager;
import net.minestom.server.instance.block.Block;
import org.graalvm.polyglot.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WorldProxy {
    private static final Logger LOGGER = LoggerFactory.getLogger(WorldProxy.class);
    private static InstanceContainer defaultInstance;
    private static boolean initialized = false;

    public WorldProxy createInstance() {
        if (!initialized) {
            InstanceManager instanceManager = MinecraftServer.getInstanceManager();
            defaultInstance = instanceManager.createInstanceContainer();
            GlobalEventHandler eventHandler = MinecraftServer.getGlobalEventHandler();
            eventHandler.addListener(AsyncPlayerConfigurationEvent.class, event -> {
                event.setSpawningInstance(defaultInstance);
                event.getPlayer().setRespawnPoint(new Pos(0, 100, 0));
            });
            initialized = true;
        }
        return this;
    }

    public WorldProxy setFlatGenerator() {
        if (defaultInstance != null) {
            defaultInstance.setGenerator(unit -> {
                unit.modifier().fillHeight(0, 1, Block.BEDROCK);
                unit.modifier().fillHeight(1, 64, Block.GRASS_BLOCK);
            });
        }
        return this;
    }

    public WorldProxy setVoidGenerator() {
        if (defaultInstance != null) {
            defaultInstance.setGenerator(unit -> {});
        }
        return this;
    }

    public WorldProxy setSpawn(double x, double y, double z) {
        return this;
    }


    /**
     * Gets the namespace ID of the block at a given world position.
     * @param x The block's X coordinate.
     * @param y The block's Y coordinate.
     * @param z The block's Z coordinate.
     * @return The block's namespace ID (e.g., "minecraft:stone") or "minecraft:air" if unavailable.
     */
    public String getBlock(int x, int y, int z) {
        if (defaultInstance != null) {
            Block block = defaultInstance.getBlock(x, y, z);
            return block.name();
        }
        return "minecraft:air";
    }

    /**
     * Places a block at a given world position.
     * @param x The block's X coordinate.
     * @param y The block's Y coordinate.
     * @param z The block's Z coordinate.
     * @param blockId The namespace ID of the block to place.
     */
    public void setBlock(int x, int y, int z, String blockId) {
        if (defaultInstance != null) {
            Block block = Block.fromNamespaceId(blockId);
            if (block != null) {
                defaultInstance.setBlock(x, y, z, block);
            } else {
                LOGGER.warn("Attempted to set an unknown block: {}", blockId);
            }
        }
    }

    /**
     * Spawns a new scripted entity into the world.
     * @param entityType The base vanilla entity type to use (e.g., "minecraft:zombie").
     * @param x The spawn X coordinate.
     * @param y The spawn Y coordinate.
     * @param z The spawn Z coordinate.
     * @param jsInstance The JavaScript object instance that contains the entity's logic (e.g., an `onTick` function).
     */
    public void spawnScriptedEntity(String entityType, double x, double y, double z, Value jsInstance) {
        if (defaultInstance == null) {
            LOGGER.error("Cannot spawn scripted entity, no instance is available.");
            return;
        }

        EntityType type = EntityType.fromNamespaceId(entityType);
        if (type == null) {
            LOGGER.error("Cannot spawn scripted entity with unknown base type: {}", entityType);
            return;
        }

        ScriptedEntity entity = new ScriptedEntity(type, jsInstance);
        entity.setInstance(defaultInstance, new Pos(x, y, z));
    }
}