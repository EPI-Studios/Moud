package com.moud.server.blocks.placement;

import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockFace;
import net.minestom.server.instance.block.rule.BlockPlacementRule;
import org.jetbrains.annotations.NotNull;

final class PillarPlacementRule extends BlockPlacementRule {
    private static final String PROP_AXIS = "axis";

    PillarPlacementRule(@NotNull Block block) {
        super(block);
    }

    @Override
    public @NotNull Block blockPlace(@NotNull PlacementState state) {
        Block base = state.block().defaultState();
        if (!PlacementRuleUtils.hasProperty(base, PROP_AXIS)) {
            return base;
        }
        assert state.blockFace() != null;
        String axis = axisForFace(state.blockFace());
        return base.withProperty(PROP_AXIS, axis);
    }

    private static @NotNull String axisForFace(@NotNull BlockFace face) {
        return switch (face) {
            case EAST, WEST -> "x";
            case NORTH, SOUTH -> "z";
            case TOP, BOTTOM -> "y";
            default -> "y";
        };
    }
}

