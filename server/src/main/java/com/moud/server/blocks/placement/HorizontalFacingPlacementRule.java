package com.moud.server.blocks.placement;

import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockFace;
import net.minestom.server.instance.block.rule.BlockPlacementRule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class HorizontalFacingPlacementRule extends BlockPlacementRule {
    HorizontalFacingPlacementRule(@NotNull Block block) {
        super(block);
    }

    @Override
    public @Nullable Block blockPlace(@NotNull PlacementState state) {
        Block base = state.block().defaultState();
        if (!PlacementRuleUtils.hasProperty(base, PlacementRuleUtils.PROP_FACING)) {
            return base;
        }

        BlockFace playerFacing = PlacementRuleUtils.horizontalFacingFromYaw(state.playerPosition().yaw());
        BlockFace facing = PlacementRuleUtils.opposite(playerFacing);
        base = base.withProperty(PlacementRuleUtils.PROP_FACING, facing.name().toLowerCase());
        base = PlacementRuleUtils.setWaterlogged(base, PlacementRuleUtils.shouldWaterlog(state));
        return base;
    }
}

