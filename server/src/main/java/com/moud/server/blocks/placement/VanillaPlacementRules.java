package com.moud.server.blocks.placement;

import java.util.HashSet;
import java.util.Set;

import net.minestom.server.MinecraftServer;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockManager;

public final class VanillaPlacementRules {
    private VanillaPlacementRules() {
    }

    public static void registerAll() {
        registerAll(MinecraftServer.getBlockManager());
    }

    public static void registerAll(BlockManager blockManager) {
        if (blockManager == null) {
            return;
        }
        registerWalls(blockManager);
        registerBeds(blockManager);
        registerBanners(blockManager);
    }

    private static void registerWalls(BlockManager blockManager) {
        Set<String> registered = new HashSet<>();
        for (Block block : Block.values()) {
            if (block == null) {
                continue;
            }
            String path = block.namespace().path();
            if (!path.endsWith("_wall")) {
                continue;
            }
            Block base = block.defaultState();
            if (!registered.add(base.namespace().asString())) {
                continue;
            }
            for (Block state : base.possibleStates()) {
                blockManager.registerBlockPlacementRule(new WallPlacementRule(state));
            }
        }
    }

    private static void registerBeds(BlockManager blockManager) {
        Set<String> registered = new HashSet<>();
        for (Block block : Block.values()) {
            if (block == null) {
                continue;
            }
            String path = block.namespace().path();
            if (!path.endsWith("_bed")) {
                continue;
            }
            Block base = block.defaultState();
            if (!registered.add(base.namespace().asString())) {
                continue;
            }
            for (Block state : base.possibleStates()) {
                blockManager.registerBlockPlacementRule(new BedPlacementRule(state));
            }
        }
    }

    private static void registerBanners(BlockManager blockManager) {
        Set<String> registered = new HashSet<>();
        for (Block block : Block.values()) {
            if (block == null) {
                continue;
            }
            String path = block.namespace().path();
            if (!path.endsWith("_banner")) {
                continue;
            }
            Block base = block.defaultState();
            if (!registered.add(base.namespace().asString())) {
                continue;
            }
            for (Block state : base.possibleStates()) {
                blockManager.registerBlockPlacementRule(new BannerPlacementRule(state));
            }
        }
    }
}
