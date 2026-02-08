package com.moud.server.blocks.placement;

import net.minestom.server.coordinate.Point;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockFace;
import net.minestom.server.instance.block.rule.BlockPlacementRule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class StairsPlacementRule extends BlockPlacementRule {
    private static final String PROP_HALF = "half";
    private static final String PROP_SHAPE = "shape";

    private static final String HALF_TOP = "top";
    private static final String HALF_BOTTOM = "bottom";

    private static final String SHAPE_STRAIGHT = "straight";
    private static final String SHAPE_INNER_LEFT = "inner_left";
    private static final String SHAPE_INNER_RIGHT = "inner_right";
    private static final String SHAPE_OUTER_LEFT = "outer_left";
    private static final String SHAPE_OUTER_RIGHT = "outer_right";

    StairsPlacementRule(@NotNull Block block) {
        super(block);
    }

    @Override
    public @Nullable Block blockPlace(@NotNull PlacementState state) {
        BlockFace facing = PlacementRuleUtils.horizontalFacingFromYaw(state.playerPosition().yaw());
        String half = placedHalf(state);

        Block stairs = state.block().defaultState();
        stairs = stairs.withProperty(PlacementRuleUtils.PROP_FACING, facing.name().toLowerCase());
        stairs = stairs.withProperty(PROP_HALF, half);
        stairs = PlacementRuleUtils.setWaterlogged(stairs, PlacementRuleUtils.shouldWaterlog(state));
        stairs = stairs.withProperty(PROP_SHAPE, shapeFor(state.instance(), state.placePosition(), stairs));
        return stairs;
    }

    @Override
    public @NotNull Block blockUpdate(@NotNull UpdateState state) {
        Block current = state.currentBlock();
        if (!isStairsBlock(current)) {
            return current;
        }
        String shape = shapeFor(state.instance(), state.blockPosition(), current);
        return current.withProperty(PROP_SHAPE, shape);
    }

    private static @NotNull String placedHalf(@NotNull PlacementState state) {
        BlockFace face = state.blockFace();
        if (face == BlockFace.BOTTOM) {
            return HALF_TOP;
        }
        if (face == BlockFace.TOP) {
            return HALF_BOTTOM;
        }
        boolean upperHalf = PlacementRuleUtils.localY(state.cursorPosition(), state.placePosition()) > 0.5;
        return upperHalf ? HALF_TOP : HALF_BOTTOM;
    }

    private static @NotNull String shapeFor(@NotNull Block.Getter instance, @NotNull Point pos, @NotNull Block self) {
        BlockFace facing = PlacementRuleUtils.faceFromProperty(self.getProperty(PlacementRuleUtils.PROP_FACING));
        String half = self.getProperty(PROP_HALF);
        if (half == null) {
            return SHAPE_STRAIGHT;
        }

        Block front = instance.getBlock(pos.relative(facing));
        if (isStairsBlock(front) && half.equals(front.getProperty(PROP_HALF))) {
            BlockFace frontFacing = PlacementRuleUtils.faceFromProperty(front.getProperty(PlacementRuleUtils.PROP_FACING));
            if (frontFacing != facing && frontFacing != PlacementRuleUtils.opposite(facing)) {
                if (frontFacing == PlacementRuleUtils.rotateLeft(facing) && !isSameHalfStairs(instance.getBlock(pos.relative(PlacementRuleUtils.rotateRight(facing))), half, frontFacing)) {
                    return SHAPE_OUTER_LEFT;
                }
                if (frontFacing == PlacementRuleUtils.rotateRight(facing) && !isSameHalfStairs(instance.getBlock(pos.relative(PlacementRuleUtils.rotateLeft(facing))), half, frontFacing)) {
                    return SHAPE_OUTER_RIGHT;
                }
            }
        }

        Block back = instance.getBlock(pos.relative(PlacementRuleUtils.opposite(facing)));
        if (isStairsBlock(back) && half.equals(back.getProperty(PROP_HALF))) {
            BlockFace backFacing = PlacementRuleUtils.faceFromProperty(back.getProperty(PlacementRuleUtils.PROP_FACING));
            if (backFacing != facing && backFacing != PlacementRuleUtils.opposite(facing)) {
                if (backFacing == PlacementRuleUtils.rotateLeft(facing) && !isSameHalfStairs(instance.getBlock(pos.relative(PlacementRuleUtils.rotateLeft(facing))), half, backFacing)) {
                    return SHAPE_INNER_LEFT;
                }
                if (backFacing == PlacementRuleUtils.rotateRight(facing) && !isSameHalfStairs(instance.getBlock(pos.relative(PlacementRuleUtils.rotateRight(facing))), half, backFacing)) {
                    return SHAPE_INNER_RIGHT;
                }
            }
        }

        return SHAPE_STRAIGHT;
    }

    private static boolean isSameHalfStairs(@Nullable Block block, @NotNull String half, @NotNull BlockFace facing) {
        if (!isStairsBlock(block) || !half.equals(block.getProperty(PROP_HALF))) {
            return false;
        }
        BlockFace neighborFacing = PlacementRuleUtils.faceFromProperty(block.getProperty(PlacementRuleUtils.PROP_FACING));
        return neighborFacing == facing;
    }

    private static boolean isStairsBlock(@Nullable Block block) {
        return block != null && block.namespace().path().endsWith("_stairs");
    }
}

