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
        registerPillars(blockManager);
        registerWalls(blockManager);
        registerBeds(blockManager);
        registerBanners(blockManager);
        registerSlabs(blockManager);
        registerStairs(blockManager);
        registerDoors(blockManager);
        registerTrapdoors(blockManager);
        registerSigns(blockManager);
        registerTorches(blockManager);
        registerLadders(blockManager);
        registerButtons(blockManager);
        registerLevers(blockManager);
        registerPanes(blockManager);
        registerFences(blockManager);
        registerFenceGates(blockManager);
        registerLanterns(blockManager);
        registerPressurePlates(blockManager);
        registerSkulls(blockManager);
        registerChests(blockManager);
        registerCandles(blockManager);
        registerSnowLayers(blockManager);
        registerRedstoneDiodes(blockManager);
        registerRails(blockManager);
        registerDirectionalFacingBlocks(blockManager);
        registerHorizontalFacingBlocks(blockManager);
        registerRotationBlocks(blockManager);
    }

    private static void registerPillars(BlockManager blockManager) {
        Set<String> registered = new HashSet<>();
        for (Block block : Block.values()) {
            if (block == null) {
                continue;
            }
            Block base = block.defaultState();
            if (!base.properties().containsKey("axis")) {
                continue;
            }
            if (!registered.add(base.namespace().asString())) {
                continue;
            }
            for (Block state : base.possibleStates()) {
                blockManager.registerBlockPlacementRule(new PillarPlacementRule(state));
            }
        }
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

    private static void registerSlabs(BlockManager blockManager) {
        Set<String> registered = new HashSet<>();
        for (Block block : Block.values()) {
            if (block == null) {
                continue;
            }
            String path = block.namespace().path();
            if (!path.endsWith("_slab")) {
                continue;
            }
            Block base = block.defaultState();
            if (!registered.add(base.namespace().asString())) {
                continue;
            }
            for (Block state : base.possibleStates()) {
                blockManager.registerBlockPlacementRule(new SlabPlacementRule(state));
            }
        }
    }

    private static void registerStairs(BlockManager blockManager) {
        Set<String> registered = new HashSet<>();
        for (Block block : Block.values()) {
            if (block == null) {
                continue;
            }
            String path = block.namespace().path();
            if (!path.endsWith("_stairs")) {
                continue;
            }
            Block base = block.defaultState();
            if (!registered.add(base.namespace().asString())) {
                continue;
            }
            for (Block state : base.possibleStates()) {
                blockManager.registerBlockPlacementRule(new StairsPlacementRule(state));
            }
        }
    }

    private static void registerDoors(BlockManager blockManager) {
        Set<String> registered = new HashSet<>();
        for (Block block : Block.values()) {
            if (block == null) {
                continue;
            }
            String path = block.namespace().path();
            if (!path.endsWith("_door") || path.endsWith("_trapdoor")) {
                continue;
            }
            Block base = block.defaultState();
            if (!registered.add(base.namespace().asString())) {
                continue;
            }
            for (Block state : base.possibleStates()) {
                blockManager.registerBlockPlacementRule(new DoorPlacementRule(state));
            }
        }
    }

    private static void registerTrapdoors(BlockManager blockManager) {
        Set<String> registered = new HashSet<>();
        for (Block block : Block.values()) {
            if (block == null) {
                continue;
            }
            String path = block.namespace().path();
            if (!path.endsWith("_trapdoor")) {
                continue;
            }
            Block base = block.defaultState();
            if (!registered.add(base.namespace().asString())) {
                continue;
            }
            for (Block state : base.possibleStates()) {
                blockManager.registerBlockPlacementRule(new TrapdoorPlacementRule(state));
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

    private static void registerSigns(BlockManager blockManager) {
        Set<String> registered = new HashSet<>();
        for (Block block : Block.values()) {
            if (block == null) {
                continue;
            }
            String path = block.namespace().path();
            if (!path.endsWith("_sign") || path.endsWith("_hanging_sign")) {
                continue;
            }
            Block base = block.defaultState();
            if (!registered.add(base.namespace().asString())) {
                continue;
            }
            for (Block state : base.possibleStates()) {
                blockManager.registerBlockPlacementRule(new SignPlacementRule(state));
            }
        }
    }

    private static void registerTorches(BlockManager blockManager) {
        Set<String> registered = new HashSet<>();
        for (Block block : Block.values()) {
            if (block == null) {
                continue;
            }
            String path = block.namespace().path();
            if (!path.endsWith("_torch")) {
                continue;
            }
            Block base = block.defaultState();
            if (!registered.add(base.namespace().asString())) {
                continue;
            }
            for (Block state : base.possibleStates()) {
                blockManager.registerBlockPlacementRule(new TorchPlacementRule(state));
            }
        }
    }

    private static void registerLadders(BlockManager blockManager) {
        Set<String> registered = new HashSet<>();
        for (Block block : Block.values()) {
            if (block == null) {
                continue;
            }
            if (!"ladder".equals(block.namespace().path())) {
                continue;
            }
            Block base = block.defaultState();
            if (!registered.add(base.namespace().asString())) {
                continue;
            }
            for (Block state : base.possibleStates()) {
                blockManager.registerBlockPlacementRule(new LadderPlacementRule(state));
            }
        }
    }

    private static void registerButtons(BlockManager blockManager) {
        Set<String> registered = new HashSet<>();
        for (Block block : Block.values()) {
            if (block == null) {
                continue;
            }
            String path = block.namespace().path();
            if (!path.endsWith("_button")) {
                continue;
            }
            Block base = block.defaultState();
            if (!registered.add(base.namespace().asString())) {
                continue;
            }
            for (Block state : base.possibleStates()) {
                blockManager.registerBlockPlacementRule(new ButtonPlacementRule(state));
            }
        }
    }

    private static void registerLevers(BlockManager blockManager) {
        Set<String> registered = new HashSet<>();
        for (Block block : Block.values()) {
            if (block == null) {
                continue;
            }
            if (!"lever".equals(block.namespace().path())) {
                continue;
            }
            Block base = block.defaultState();
            if (!registered.add(base.namespace().asString())) {
                continue;
            }
            for (Block state : base.possibleStates()) {
                blockManager.registerBlockPlacementRule(new LeverPlacementRule(state));
            }
        }
    }

    private static void registerPanes(BlockManager blockManager) {
        Set<String> registered = new HashSet<>();
        for (Block block : Block.values()) {
            if (block == null) {
                continue;
            }
            String path = block.namespace().path();
            if (!path.endsWith("_pane") && !"iron_bars".equals(path)) {
                continue;
            }
            Block base = block.defaultState();
            if (!registered.add(base.namespace().asString())) {
                continue;
            }
            for (Block state : base.possibleStates()) {
                blockManager.registerBlockPlacementRule(new PanePlacementRule(state));
            }
        }
    }

    private static void registerFences(BlockManager blockManager) {
        Set<String> registered = new HashSet<>();
        for (Block block : Block.values()) {
            if (block == null) {
                continue;
            }
            String path = block.namespace().path();
            if (!path.endsWith("_fence") || path.endsWith("_fence_gate")) {
                continue;
            }
            Block base = block.defaultState();
            if (!registered.add(base.namespace().asString())) {
                continue;
            }
            for (Block state : base.possibleStates()) {
                blockManager.registerBlockPlacementRule(new FencePlacementRule(state));
            }
        }
    }

    private static void registerFenceGates(BlockManager blockManager) {
        Set<String> registered = new HashSet<>();
        for (Block block : Block.values()) {
            if (block == null) {
                continue;
            }
            String path = block.namespace().path();
            if (!path.endsWith("_fence_gate")) {
                continue;
            }
            Block base = block.defaultState();
            if (!registered.add(base.namespace().asString())) {
                continue;
            }
            for (Block state : base.possibleStates()) {
                blockManager.registerBlockPlacementRule(new FenceGatePlacementRule(state));
            }
        }
    }

    private static void registerLanterns(BlockManager blockManager) {
        Set<String> registered = new HashSet<>();
        for (Block block : Block.values()) {
            if (block == null) {
                continue;
            }
            String path = block.namespace().path();
            if (!path.endsWith("lantern")) {
                continue;
            }
            Block base = block.defaultState();
            if (!base.properties().containsKey("hanging")) {
                continue;
            }
            if (!registered.add(base.namespace().asString())) {
                continue;
            }
            for (Block state : base.possibleStates()) {
                blockManager.registerBlockPlacementRule(new LanternPlacementRule(state));
            }
        }
    }

    private static void registerPressurePlates(BlockManager blockManager) {
        Set<String> registered = new HashSet<>();
        for (Block block : Block.values()) {
            if (block == null) {
                continue;
            }
            String path = block.namespace().path();
            if (!path.endsWith("_pressure_plate")
                    && !"heavy_weighted_pressure_plate".equals(path)
                    && !"light_weighted_pressure_plate".equals(path)) {
                continue;
            }
            Block base = block.defaultState();
            if (!registered.add(base.namespace().asString())) {
                continue;
            }
            for (Block state : base.possibleStates()) {
                blockManager.registerBlockPlacementRule(new PressurePlatePlacementRule(state));
            }
        }
    }

    private static void registerSkulls(BlockManager blockManager) {
        Set<String> registered = new HashSet<>();
        for (Block block : Block.values()) {
            if (block == null) {
                continue;
            }
            String path = block.namespace().path();
            boolean isSkull = path.endsWith("_skull") || path.endsWith("_wall_skull");
            boolean isHead = path.endsWith("_head") || path.endsWith("_wall_head");
            if (!isSkull && !isHead) {
                continue;
            }
            if ("piston_head".equals(path)) {
                continue;
            }
            Block base = block.defaultState();
            if (!registered.add(base.namespace().asString())) {
                continue;
            }
            for (Block state : base.possibleStates()) {
                blockManager.registerBlockPlacementRule(new SkullPlacementRule(state));
            }
        }
    }

    private static void registerChests(BlockManager blockManager) {
        Set<String> registered = new HashSet<>();
        for (Block block : Block.values()) {
            if (block == null) {
                continue;
            }
            String path = block.namespace().path();
            if (!"chest".equals(path) && !"trapped_chest".equals(path)) {
                continue;
            }
            Block base = block.defaultState();
            if (!registered.add(base.namespace().asString())) {
                continue;
            }
            for (Block state : base.possibleStates()) {
                blockManager.registerBlockPlacementRule(new ChestPlacementRule(state));
            }
        }
    }

    private static void registerCandles(BlockManager blockManager) {
        Set<String> registered = new HashSet<>();
        for (Block block : Block.values()) {
            if (block == null) {
                continue;
            }
            String path = block.namespace().path();
            if (!path.endsWith("_candle") && !"candle".equals(path)) {
                continue;
            }
            Block base = block.defaultState();
            if (!base.properties().containsKey("candles")) {
                continue;
            }
            if (!registered.add(base.namespace().asString())) {
                continue;
            }
            for (Block state : base.possibleStates()) {
                blockManager.registerBlockPlacementRule(new CandlePlacementRule(state));
            }
        }
    }

    private static void registerSnowLayers(BlockManager blockManager) {
        Set<String> registered = new HashSet<>();
        for (Block block : Block.values()) {
            if (block == null) {
                continue;
            }
            if (!"snow".equals(block.namespace().path())) {
                continue;
            }
            Block base = block.defaultState();
            if (!registered.add(base.namespace().asString())) {
                continue;
            }
            for (Block state : base.possibleStates()) {
                blockManager.registerBlockPlacementRule(new SnowLayerPlacementRule(state));
            }
        }
    }

    private static void registerRedstoneDiodes(BlockManager blockManager) {
        Set<String> registered = new HashSet<>();
        for (Block block : Block.values()) {
            if (block == null) {
                continue;
            }
            String path = block.namespace().path();
            if (!"repeater".equals(path) && !"comparator".equals(path)) {
                continue;
            }
            Block base = block.defaultState();
            if (!registered.add(base.namespace().asString())) {
                continue;
            }
            for (Block state : base.possibleStates()) {
                blockManager.registerBlockPlacementRule(new RedstoneDiodePlacementRule(state));
            }
        }
    }

    private static void registerRails(BlockManager blockManager) {
        Set<String> registered = new HashSet<>();
        for (Block block : Block.values()) {
            if (block == null) {
                continue;
            }
            String path = block.namespace().path();
            if (!"rail".equals(path) && !path.endsWith("_rail")) {
                continue;
            }
            Block base = block.defaultState();
            if (!base.properties().containsKey("shape")) {
                continue;
            }
            if (!registered.add(base.namespace().asString())) {
                continue;
            }
            for (Block state : base.possibleStates()) {
                blockManager.registerBlockPlacementRule(new RailPlacementRule(state));
            }
        }
    }

    private static void registerDirectionalFacingBlocks(BlockManager blockManager) {
        Set<String> registered = new HashSet<>();
        for (Block block : Block.values()) {
            if (block == null) {
                continue;
            }
            Block base = block.defaultState();
            if (!DirectionalFacingPlacementRule.looksDirectional(base)) {
                continue;
            }
            if (!registered.add(base.namespace().asString())) {
                continue;
            }
            String path = base.namespace().path();
            if (shouldSkipGenericFacing(path)) {
                continue;
            }
            for (Block state : base.possibleStates()) {
                blockManager.registerBlockPlacementRule(new DirectionalFacingPlacementRule(state));
            }
        }
    }

    private static void registerHorizontalFacingBlocks(BlockManager blockManager) {
        Set<String> registered = new HashSet<>();
        for (Block block : Block.values()) {
            if (block == null) {
                continue;
            }
            Block base = block.defaultState();
            if (!DirectionalFacingPlacementRule.looksHorizontalOnly(base)) {
                continue;
            }
            if (!registered.add(base.namespace().asString())) {
                continue;
            }
            String path = base.namespace().path();
            if (shouldSkipGenericFacing(path)) {
                continue;
            }
            for (Block state : base.possibleStates()) {
                blockManager.registerBlockPlacementRule(new HorizontalFacingPlacementRule(state));
            }
        }
    }

    private static void registerRotationBlocks(BlockManager blockManager) {
        Set<String> registered = new HashSet<>();
        for (Block block : Block.values()) {
            if (block == null) {
                continue;
            }
            Block base = block.defaultState();
            if (!base.properties().containsKey("rotation")) {
                continue;
            }
            String path = base.namespace().path();
            if (path.endsWith("_banner") || path.endsWith("_wall_banner")) {
                continue;
            }
            if (path.endsWith("_sign") || path.endsWith("_wall_sign") || path.endsWith("_hanging_sign") || path.endsWith("_wall_hanging_sign")) {
                continue;
            }
            if (path.endsWith("_skull") || path.endsWith("_wall_skull") || path.endsWith("_head") || path.endsWith("_wall_head")) {
                continue;
            }
            if (!registered.add(base.namespace().asString())) {
                continue;
            }
            for (Block state : base.possibleStates()) {
                blockManager.registerBlockPlacementRule(new Rotation16PlacementRule(state));
            }
        }
    }

    private static boolean shouldSkipGenericFacing(String path) {
        if (path.endsWith("_stairs")
                || path.endsWith("_slab")
                || path.endsWith("_bed")
                || path.endsWith("_door")
                || path.endsWith("_trapdoor")
                || path.endsWith("_fence_gate")
                || path.endsWith("_banner")
                || path.endsWith("_wall_banner")
                || path.endsWith("_sign")
                || path.endsWith("_wall_sign")
                || path.endsWith("_hanging_sign")
                || path.endsWith("_wall_hanging_sign")
                || path.endsWith("_torch")
                || path.endsWith("_wall_torch")
                || path.endsWith("_wall")
                || path.endsWith("_fence")
                || path.endsWith("_pane")
                || "iron_bars".equals(path)
                || "ladder".equals(path)) {
            return true;
        }
        if ("chest".equals(path) || "trapped_chest".equals(path) || "repeater".equals(path) || "comparator".equals(path)) {
            return true;
        }
        return path.endsWith("_skull") || path.endsWith("_wall_skull") || path.endsWith("_head") || path.endsWith("_wall_head");
    }
}
