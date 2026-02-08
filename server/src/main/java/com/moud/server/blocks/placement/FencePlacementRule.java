package com.moud.server.blocks.placement;

import java.util.Map;

import net.minestom.server.coordinate.Point;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockFace;
import net.minestom.server.instance.block.rule.BlockPlacementRule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class FencePlacementRule extends BlockPlacementRule {
    private static final Map<BlockFace, String> SIDE_PROPERTIES = Map.of(
            BlockFace.NORTH, "north",
            BlockFace.EAST, "east",
            BlockFace.SOUTH, "south",
            BlockFace.WEST, "west"
    );

    FencePlacementRule(@NotNull Block block) {
        super(block);
    }

    @Override
    public @Nullable Block blockPlace(@NotNull PlacementState state) {
        Block fence = state.block().defaultState();
        return applyConnections(state.instance(), state.placePosition(), fence);
    }

    @Override
    public @NotNull Block blockUpdate(@NotNull UpdateState state) {
        Block current = state.currentBlock();
        if (!isFenceBlock(current)) {
            return current;
        }
        return applyConnections(state.instance(), state.blockPosition(), current);
    }

    private static @NotNull Block applyConnections(@NotNull Block.Getter instance, @NotNull Point pos, @NotNull Block fence) {
        return fence.withProperties(Map.of(
                sideProperty(BlockFace.NORTH), connected(instance, pos, BlockFace.NORTH) ? "true" : "false",
                sideProperty(BlockFace.EAST), connected(instance, pos, BlockFace.EAST) ? "true" : "false",
                sideProperty(BlockFace.SOUTH), connected(instance, pos, BlockFace.SOUTH) ? "true" : "false",
                sideProperty(BlockFace.WEST), connected(instance, pos, BlockFace.WEST) ? "true" : "false"
        ));
    }

    private static boolean connected(@NotNull Block.Getter instance, @NotNull Point pos, @NotNull BlockFace face) {
        Point neighborPos = pos.relative(face);
        Block neighbor = instance.getBlock(neighborPos);
        if (neighbor == null || neighbor.isAir()) {
            return false;
        }
        if (isFenceBlock(neighbor)) {
            return true;
        }
        if (isFenceGateBlock(neighbor)) {
            return true;
        }
        return neighbor.registry().collisionShape().isFaceFull(PlacementRuleUtils.opposite(face));
    }

    private static String sideProperty(@NotNull BlockFace face) {
        return SIDE_PROPERTIES.get(face);
    }

    private static boolean isFenceBlock(@Nullable Block block) {
        if (block == null) {
            return false;
        }
        String path = block.namespace().path();
        return path.endsWith("_fence") && !path.endsWith("_fence_gate");
    }

    private static boolean isFenceGateBlock(@Nullable Block block) {
        return block != null && block.namespace().path().endsWith("_fence_gate");
    }
}

