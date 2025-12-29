package com.moud.server.blocks.placement;

import net.minestom.server.coordinate.Point;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockFace;
import net.minestom.server.instance.block.rule.BlockPlacementRule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class LadderPlacementRule extends BlockPlacementRule {
    LadderPlacementRule(@NotNull Block block) {
        super(block);
    }

    @Override
    public @Nullable Block blockPlace(@NotNull PlacementState state) {
        BlockFace face = state.blockFace();
        if (face == BlockFace.TOP || face == BlockFace.BOTTOM) {
            return null;
        }

        Point pos = state.placePosition();
        Block support = state.instance().getBlock(pos.relative(PlacementRuleUtils.opposite(face)));
        if (!PlacementRuleUtils.isFaceFull(support, face)) {
            return null;
        }

        return state.block().defaultState()
                .withProperty(PlacementRuleUtils.PROP_FACING, face.name().toLowerCase());
    }

    @Override
    public @NotNull Block blockUpdate(@NotNull UpdateState state) {
        Block current = state.currentBlock();
        if (!isLadderBlock(current)) {
            return current;
        }

        String facingValue = current.getProperty(PlacementRuleUtils.PROP_FACING);
        if (facingValue == null) {
            return current;
        }
        BlockFace facing = PlacementRuleUtils.faceFromProperty(facingValue);

        Point pos = state.blockPosition();
        Block support = state.instance().getBlock(pos.relative(PlacementRuleUtils.opposite(facing)));
        if (!PlacementRuleUtils.isFaceFull(support, facing)) {
            return Block.AIR;
        }
        return current;
    }

    private static boolean isLadderBlock(@Nullable Block block) {
        return block != null && "ladder".equals(block.namespace().path());
    }
}

