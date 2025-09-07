package com.moud.server.proxy;

import com.moud.server.api.exception.APIException;
import com.moud.server.api.validation.APIValidator;
import com.moud.server.entity.ScriptedEntity;
import com.moud.server.logging.MoudLogger;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.EntityType;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.InstanceManager;
import net.minestom.server.instance.block.Block;
import org.graalvm.polyglot.Value;

public class WorldProxy {
    private static final MoudLogger LOGGER = MoudLogger.getLogger(WorldProxy.class);
    private static InstanceContainer defaultInstance;
    private static boolean initialized = false;

    private final APIValidator validator;

    public WorldProxy() {
        this.validator = new APIValidator();
    }

    public WorldProxy createInstance() {
        if (!initialized) {
            try {
                InstanceManager instanceManager = MinecraftServer.getInstanceManager();
                defaultInstance = instanceManager.createInstanceContainer();

                GlobalEventHandler eventHandler = MinecraftServer.getGlobalEventHandler();
                eventHandler.addListener(AsyncPlayerConfigurationEvent.class, event -> {
                    event.setSpawningInstance(defaultInstance);
                    event.getPlayer().setRespawnPoint(new Pos(0, 100, 0));
                });

                initialized = true;
                LOGGER.success("World instance created and configured successfully");

            } catch (Exception e) {
                LOGGER.error("Failed to create world instance", e);
                throw new APIException("WORLD_CREATION_FAILED", "Failed to create world instance", e);
            }
        }
        return this;
    }

    public WorldProxy setFlatGenerator() {
        if (defaultInstance == null) {
            throw new APIException("WORLD_NOT_INITIALIZED", "World instance not initialized");
        }

        try {
            defaultInstance.setGenerator(unit -> {
                unit.modifier().fillHeight(0, 1, Block.BEDROCK);
                unit.modifier().fillHeight(1, 64, Block.GRASS_BLOCK);
            });

            LOGGER.api("Flat world generator applied successfully");

        } catch (Exception e) {
            LOGGER.error("Failed to set flat generator", e);
            throw new APIException("GENERATOR_FAILED", "Failed to set flat generator", e);
        }

        return this;
    }

    public WorldProxy setVoidGenerator() {
        if (defaultInstance == null) {
            throw new APIException("WORLD_NOT_INITIALIZED", "World instance not initialized");
        }

        try {
            defaultInstance.setGenerator(unit -> {});
            LOGGER.api("Void world generator applied successfully");

        } catch (Exception e) {
            LOGGER.error("Failed to set void generator", e);
            throw new APIException("GENERATOR_FAILED", "Failed to set void generator", e);
        }

        return this;
    }

    public WorldProxy setSpawn(double x, double y, double z) {
        try {
            validator.validateCoordinates(x, y, z);
            LOGGER.api("World spawn set to: {}, {}, {}", x, y, z);

        } catch (APIException e) {
            LOGGER.error("Invalid spawn coordinates: {}, {}, {}", x, y, z);
            throw e;
        } catch (Exception e) {
            LOGGER.error("Failed to set spawn coordinates", e);
            throw new APIException("SPAWN_SET_FAILED", "Failed to set spawn coordinates", e);
        }

        return this;
    }

    public String getBlock(int x, int y, int z) {
        try {
            validator.validateCoordinates(x, y, z);

            if (defaultInstance == null) {
                throw new APIException("WORLD_NOT_INITIALIZED", "World instance not initialized");
            }

            Block block = defaultInstance.getBlock(x, y, z);
            String blockName = block.name();

            LOGGER.debug("Retrieved block at {}, {}, {}: {}", x, y, z, blockName);
            return blockName;

        } catch (APIException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("Failed to get block at {}, {}, {}", x, y, z, e);
            return "minecraft:air";
        }
    }

    public void setBlock(int x, int y, int z, String blockId) {
        try {
            validator.validateCoordinates(x, y, z);
            validator.validateBlockId(blockId);

            if (defaultInstance == null) {
                throw new APIException("WORLD_NOT_INITIALIZED", "World instance not initialized");
            }

            Block block = Block.fromNamespaceId(blockId);
            if (block == null) {
                throw new APIException("INVALID_BLOCK_ID", "Unknown block ID: " + blockId);
            }

            defaultInstance.setBlock(x, y, z, block);
            LOGGER.debug("Set block at {}, {}, {} to: {}", x, y, z, blockId);

        } catch (APIException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("Failed to set block at {}, {}, {} to {}", x, y, z, blockId, e);
            throw new APIException("BLOCK_SET_FAILED", "Failed to set block", e);
        }
    }

    public void spawnScriptedEntity(String entityType, double x, double y, double z, Value jsInstance) {
        try {
            validator.validateCoordinates(x, y, z);

            if (entityType == null || entityType.trim().isEmpty()) {
                throw new APIException("INVALID_ENTITY_TYPE", "Entity type cannot be null or empty");
            }

            if (jsInstance == null) {
                throw new APIException("INVALID_JS_INSTANCE", "JavaScript instance cannot be null");
            }

            if (defaultInstance == null) {
                throw new APIException("WORLD_NOT_INITIALIZED", "World instance not initialized");
            }

            EntityType type = EntityType.fromNamespaceId(entityType);
            if (type == null) {
                throw new APIException("UNKNOWN_ENTITY_TYPE", "Unknown entity type: " + entityType);
            }

            ScriptedEntity entity = new ScriptedEntity(type, jsInstance);
            entity.setInstance(defaultInstance, new Pos(x, y, z));

            LOGGER.success("Scripted entity '{}' spawned at {}, {}, {}", entityType, x, y, z);

        } catch (APIException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("Failed to spawn scripted entity '{}' at {}, {}, {}", entityType, x, y, z, e);
            throw new APIException("ENTITY_SPAWN_FAILED", "Failed to spawn scripted entity", e);
        }
    }

    public boolean isInitialized() {
        return initialized && defaultInstance != null;
    }

    public Instance getInstance() {
        if (defaultInstance == null) {
            throw new APIException("WORLD_NOT_INITIALIZED", "World instance not initialized");
        }
        return defaultInstance;
    }
}