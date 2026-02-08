package com.moud.server.blocks.placement;

import net.minestom.server.coordinate.Point;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockFace;
import net.minestom.server.instance.block.rule.BlockPlacementRule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class PressurePlatePlacementRule extends BlockPlacementRule {
    PressurePlatePlacementRule(@NotNull Block block) {
        super(block);
    }

    @Override
    public @Nullable Block blockPlace(@NotNull PlacementState state) {
        Point pos = state.placePosition();
        Block support = state.instance().getBlock(pos.relative(BlockFace.BOTTOM));
        if (!PlacementRuleUtils.isFaceFull(support, BlockFace.TOP)) {
            return null;
        }
        return state.block().defaultState();
    }

    @Override
    public @NotNull Block blockUpdate(@NotNull UpdateState state) {
        Block current = state.currentBlock();
        if (!isPressurePlate(current)) {
            return current;
        }
        Block support = state.instance().getBlock(state.blockPosition().relative(BlockFace.BOTTOM));
        if (!PlacementRuleUtils.isFaceFull(support, BlockFace.TOP)) {
            return Block.AIR;
        }
        return current;
    }

    private static boolean isPressurePlate(@Nullable Block block) {
        if (block == null) {
            return false;
        }
        String path = block.namespace().path();
        return path.endsWith("_pressure_plate") || "heavy_weighted_pressure_plate".equals(path) || "light_weighted_pressure_plate".equals(path);
    }
}

