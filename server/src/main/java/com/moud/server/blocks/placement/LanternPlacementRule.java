package com.moud.server.blocks.placement;

import net.minestom.server.coordinate.Point;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockFace;
import net.minestom.server.instance.block.rule.BlockPlacementRule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class LanternPlacementRule extends BlockPlacementRule {
    private static final String PROP_HANGING = "hanging";
    private static final String PROP_WATERLOGGED = "waterlogged";

    LanternPlacementRule(@NotNull Block block) {
        super(block);
    }

    @Override
    public @Nullable Block blockPlace(@NotNull PlacementState state) {
        Block base = state.block().defaultState();
        if (!PlacementRuleUtils.hasProperty(base, PROP_HANGING)) {
            return base;
        }

        BlockFace face = state.blockFace();
        boolean hanging = face == BlockFace.BOTTOM;
        Point pos = state.placePosition();

        if (hanging) {
            Block support = state.instance().getBlock(pos.relative(BlockFace.TOP));
            if (!PlacementRuleUtils.isFaceFull(support, BlockFace.BOTTOM)) {
                return null;
            }
        } else {
            Block support = state.instance().getBlock(pos.relative(BlockFace.BOTTOM));
            if (!PlacementRuleUtils.isFaceFull(support, BlockFace.TOP)) {
                return null;
            }
        }

        base = base.withProperty(PROP_HANGING, hanging ? "true" : "false");
        if (PlacementRuleUtils.hasProperty(base, PROP_WATERLOGGED)) {
            base = base.withProperty(PROP_WATERLOGGED, PlacementRuleUtils.shouldWaterlog(state) ? "true" : "false");
        }
        return base;
    }

    @Override
    public @NotNull Block blockUpdate(@NotNull UpdateState state) {
        Block current = state.currentBlock();
        if (current == null || current.isAir() || !PlacementRuleUtils.hasProperty(current, PROP_HANGING)) {
            return current;
        }

        String hangingValue = current.getProperty(PROP_HANGING);
        if (hangingValue == null) {
            return current;
        }

        Point pos = state.blockPosition();
        if ("true".equals(hangingValue)) {
            Block support = state.instance().getBlock(pos.relative(BlockFace.TOP));
            if (!PlacementRuleUtils.isFaceFull(support, BlockFace.BOTTOM)) {
                return Block.AIR;
            }
        } else {
            Block support = state.instance().getBlock(pos.relative(BlockFace.BOTTOM));
            if (!PlacementRuleUtils.isFaceFull(support, BlockFace.TOP)) {
                return Block.AIR;
            }
        }

        return current;
    }
}

