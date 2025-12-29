package com.moud.server.blocks.placement;

import net.minestom.server.coordinate.Point;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockFace;
import net.minestom.server.instance.block.rule.BlockPlacementRule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class RedstoneDiodePlacementRule extends BlockPlacementRule {
    private static final String PROP_FACING = "facing";

    RedstoneDiodePlacementRule(@NotNull Block block) {
        super(block);
    }

    @Override
    public @Nullable Block blockPlace(@NotNull PlacementState state) {
        Point pos = state.placePosition();
        Block support = state.instance().getBlock(pos.relative(BlockFace.BOTTOM));
        if (!PlacementRuleUtils.isFaceFull(support, BlockFace.TOP)) {
            return null;
        }

        BlockFace playerFacing = PlacementRuleUtils.horizontalFacingFromYaw(state.playerPosition().yaw());
        BlockFace facing = PlacementRuleUtils.opposite(playerFacing);
        Block diode = state.block().defaultState();
        if (PlacementRuleUtils.hasProperty(diode, PROP_FACING)) {
            diode = diode.withProperty(PROP_FACING, facing.name().toLowerCase());
        }
        return diode;
    }

    @Override
    public @NotNull Block blockUpdate(@NotNull UpdateState state) {
        Block current = state.currentBlock();
        if (!isDiode(current)) {
            return current;
        }
        Block support = state.instance().getBlock(state.blockPosition().relative(BlockFace.BOTTOM));
        if (!PlacementRuleUtils.isFaceFull(support, BlockFace.TOP)) {
            return Block.AIR;
        }
        return current;
    }

    private static boolean isDiode(@Nullable Block block) {
        if (block == null) {
            return false;
        }
        String path = block.namespace().path();
        return "repeater".equals(path) || "comparator".equals(path);
    }
}

