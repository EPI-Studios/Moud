package com.moud.server.blocks.placement;

import net.minestom.server.coordinate.Point;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockFace;
import net.minestom.server.instance.block.rule.BlockPlacementRule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class TrapdoorPlacementRule extends BlockPlacementRule {
    private static final String PROP_HALF = "half";
    private static final String PROP_OPEN = "open";
    private static final String PROP_POWERED = "powered";

    TrapdoorPlacementRule(@NotNull Block block) {
        super(block);
    }

    @Override
    public @Nullable Block blockPlace(@NotNull PlacementState state) {
        BlockFace clickedFace = state.blockFace();
        Point pos = state.placePosition();
        Point supportPos = pos.relative(PlacementRuleUtils.opposite(clickedFace));
        Block support = state.instance().getBlock(supportPos);
        if (!PlacementRuleUtils.isFaceFull(support, clickedFace)) {
            return null;
        }

        BlockFace facing = clickedFace == BlockFace.TOP || clickedFace == BlockFace.BOTTOM
                ? PlacementRuleUtils.horizontalFacingFromYaw(state.playerPosition().yaw())
                : clickedFace;

        String half = placedHalf(state);

        Block trapdoor = state.block().defaultState()
                .withProperty(PlacementRuleUtils.PROP_FACING, facing.name().toLowerCase())
                .withProperty(PROP_HALF, half)
                .withProperty(PROP_OPEN, "false")
                .withProperty(PROP_POWERED, "false");
        trapdoor = PlacementRuleUtils.setWaterlogged(trapdoor, PlacementRuleUtils.shouldWaterlog(state));
        return trapdoor;
    }

    @Override
    public @NotNull Block blockUpdate(@NotNull UpdateState state) {
        Block current = state.currentBlock();
        if (!isTrapdoorBlock(current)) {
            return current;
        }

        Point pos = state.blockPosition();
        if (!hasAnySupport(state, current, pos)) {
            return Block.AIR;
        }

        return current;
    }

    private static boolean hasAnySupport(@NotNull UpdateState state, @NotNull Block current, @NotNull Point pos) {
        Block below = state.instance().getBlock(pos.relative(BlockFace.BOTTOM));
        if (PlacementRuleUtils.isFaceFull(below, BlockFace.TOP)) {
            return true;
        }

        Block above = state.instance().getBlock(pos.relative(BlockFace.TOP));
        if (PlacementRuleUtils.isFaceFull(above, BlockFace.BOTTOM)) {
            return true;
        }

        String facingValue = current.getProperty(PlacementRuleUtils.PROP_FACING);
        if (facingValue == null) {
            return true;
        }
        BlockFace facing = PlacementRuleUtils.faceFromProperty(facingValue);
        Block behind = state.instance().getBlock(pos.relative(PlacementRuleUtils.opposite(facing)));
        return PlacementRuleUtils.isFaceFull(behind, facing);
    }

    private static @NotNull String placedHalf(@NotNull PlacementState state) {
        BlockFace clickedFace = state.blockFace();
        if (clickedFace == BlockFace.BOTTOM) {
            return "top";
        }
        if (clickedFace == BlockFace.TOP) {
            return "bottom";
        }
        boolean upperHalf = PlacementRuleUtils.localY(state.cursorPosition(), state.placePosition()) > 0.5;
        return upperHalf ? "top" : "bottom";
    }

    private static boolean isTrapdoorBlock(@Nullable Block block) {
        return block != null && block.namespace().path().endsWith("_trapdoor");
    }
}
