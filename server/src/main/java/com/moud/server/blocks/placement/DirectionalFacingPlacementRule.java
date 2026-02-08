package com.moud.server.blocks.placement;

import java.util.Set;

import net.minestom.server.coordinate.Vec;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockFace;
import net.minestom.server.instance.block.rule.BlockPlacementRule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class DirectionalFacingPlacementRule extends BlockPlacementRule {
    private static final Set<String> HORIZONTAL_VALUES = Set.of("north", "east", "south", "west");

    DirectionalFacingPlacementRule(@NotNull Block block) {
        super(block);
    }

    @Override
    public @Nullable Block blockPlace(@NotNull PlacementState state) {
        Block base = state.block().defaultState();
        if (!PlacementRuleUtils.hasProperty(base, PlacementRuleUtils.PROP_FACING)) {
            return base;
        }

        BlockFace clickedFace = state.blockFace();
        BlockFace facing;
        if (clickedFace == BlockFace.TOP || clickedFace == BlockFace.BOTTOM) {
            facing = facingFromLook(state);
        } else {
            facing = PlacementRuleUtils.opposite(clickedFace);
        }

        base = base.withProperty(PlacementRuleUtils.PROP_FACING, PlacementRuleUtils.facingPropertyValue(facing));
        base = PlacementRuleUtils.setWaterlogged(base, PlacementRuleUtils.shouldWaterlog(state));
        return base;
    }

    private static @NotNull BlockFace facingFromLook(@NotNull PlacementState state) {
        Vec look = state.playerPosition().direction();
        double ax = Math.abs(look.x());
        double ay = Math.abs(look.y());
        double az = Math.abs(look.z());

        if (ay > ax && ay > az) {
            return look.y() > 0.0 ? BlockFace.TOP : BlockFace.BOTTOM;
        }

        BlockFace playerFacing = PlacementRuleUtils.horizontalFacingFromYaw(state.playerPosition().yaw());
        return PlacementRuleUtils.opposite(playerFacing);
    }

    static boolean looksDirectional(@NotNull Block base) {
        if (!PlacementRuleUtils.hasProperty(base, PlacementRuleUtils.PROP_FACING)) {
            return false;
        }
        for (Block state : base.possibleStates()) {
            String value = state.getProperty(PlacementRuleUtils.PROP_FACING);
            if (value == null) {
                continue;
            }
            if ("up".equals(value) || "down".equals(value)) {
                return true;
            }
        }
        return false;
    }

    static boolean looksHorizontalOnly(@NotNull Block base) {
        if (!PlacementRuleUtils.hasProperty(base, PlacementRuleUtils.PROP_FACING)) {
            return false;
        }
        for (Block state : base.possibleStates()) {
            String value = state.getProperty(PlacementRuleUtils.PROP_FACING);
            if (value == null) {
                continue;
            }
            if (!HORIZONTAL_VALUES.contains(value)) {
                return false;
            }
        }
        return true;
    }
}

