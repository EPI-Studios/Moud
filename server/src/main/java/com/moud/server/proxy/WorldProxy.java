package com.moud.server.proxy;

import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.InstanceManager;
import net.minestom.server.instance.block.Block;

public class WorldProxy {
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
}