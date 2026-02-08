package com.moud.server.blocks.placement;

import net.minestom.server.coordinate.Point;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockFace;
import net.minestom.server.instance.block.rule.BlockPlacementRule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class DoorPlacementRule extends BlockPlacementRule {
    private static final String PROP_HALF = "half";
    private static final String PROP_HINGE = "hinge";
    private static final String PROP_OPEN = "open";
    private static final String PROP_POWERED = "powered";

    private static final String HALF_LOWER = "lower";
    private static final String HALF_UPPER = "upper";
    private static final String HINGE_LEFT = "left";
    private static final String HINGE_RIGHT = "right";

    DoorPlacementRule(@NotNull Block block) {
        super(block);
    }

    @Override
    public @Nullable Block blockPlace(@NotNull PlacementState state) {
        if (state.blockFace() == BlockFace.BOTTOM) {
            return null;
        }

        Point lowerPos = state.placePosition();
        Point upperPos = lowerPos.relative(BlockFace.TOP);

        Block upperExisting = state.instance().getBlock(upperPos);
        if (!PlacementRuleUtils.canReplace(upperExisting)) {
            return null;
        }

        Block support = state.instance().getBlock(lowerPos.relative(BlockFace.BOTTOM));
        if (!PlacementRuleUtils.isFaceFull(support, BlockFace.TOP)) {
            return null;
        }

        BlockFace facing = PlacementRuleUtils.horizontalFacingFromYaw(state.playerPosition().yaw());
        BlockFace left = PlacementRuleUtils.rotateLeft(facing);
        BlockFace right = PlacementRuleUtils.rotateRight(facing);

        String hinge = chooseHinge(state, lowerPos, upperPos, facing, left, right);

        Block doorLower = state.block().defaultState()
                .withProperty(PlacementRuleUtils.PROP_FACING, facing.name().toLowerCase())
                .withProperty(PROP_HALF, HALF_LOWER)
                .withProperty(PROP_HINGE, hinge)
                .withProperty(PROP_OPEN, "false")
                .withProperty(PROP_POWERED, "false");

        Block doorUpper = doorLower.withProperty(PROP_HALF, HALF_UPPER);
        if (!(state.instance() instanceof Instance instance)) {
            return null;
        }
        instance.setBlock(upperPos, doorUpper);
        return doorLower;
    }

    @Override
    public @NotNull Block blockUpdate(@NotNull UpdateState state) {
        Block current = state.currentBlock();
        if (!isDoorBlock(current)) {
            return current;
        }

        String half = current.getProperty(PROP_HALF);
        if (half == null) {
            return current;
        }

        Point pos = state.blockPosition();
        if (HALF_LOWER.equals(half)) {
            Block support = state.instance().getBlock(pos.relative(BlockFace.BOTTOM));
            if (!PlacementRuleUtils.isFaceFull(support, BlockFace.TOP)) {
                return Block.AIR;
            }
            Block upper = state.instance().getBlock(pos.relative(BlockFace.TOP));
            if (!isDoorBlock(upper) || !upper.namespace().equals(current.namespace())) {
                return Block.AIR;
            }
            return current;
        }

        Block lower = state.instance().getBlock(pos.relative(BlockFace.BOTTOM));
        if (!isDoorBlock(lower) || !lower.namespace().equals(current.namespace())) {
            return Block.AIR;
        }
        return current;
    }

    private static @NotNull String chooseHinge(@NotNull PlacementState state,
                                               @NotNull Point lowerPos,
                                               @NotNull Point upperPos,
                                               @NotNull BlockFace facing,
                                               @NotNull BlockFace left,
                                               @NotNull BlockFace right) {
        Block leftLower = state.instance().getBlock(lowerPos.relative(left));
        Block leftUpper = state.instance().getBlock(upperPos.relative(left));
        Block rightLower = state.instance().getBlock(lowerPos.relative(right));
        Block rightUpper = state.instance().getBlock(upperPos.relative(right));

        boolean leftDoor = isMatchingDoor(leftLower, state.block(), facing) || isMatchingDoor(leftUpper, state.block(), facing);
        boolean rightDoor = isMatchingDoor(rightLower, state.block(), facing) || isMatchingDoor(rightUpper, state.block(), facing);

        if (leftDoor && !rightDoor) {
            return HINGE_RIGHT;
        }
        if (rightDoor && !leftDoor) {
            return HINGE_LEFT;
        }

        int leftScore = (PlacementRuleUtils.isFaceFull(leftLower, right) ? 1 : 0) + (PlacementRuleUtils.isFaceFull(leftUpper, right) ? 1 : 0);
        int rightScore = (PlacementRuleUtils.isFaceFull(rightLower, left) ? 1 : 0) + (PlacementRuleUtils.isFaceFull(rightUpper, left) ? 1 : 0);

        if (leftScore > rightScore) {
            return HINGE_RIGHT;
        }
        if (rightScore > leftScore) {
            return HINGE_LEFT;
        }

        boolean clickRight = isClickOnRightSide(state, lowerPos, facing);
        return clickRight ? HINGE_RIGHT : HINGE_LEFT;
    }

    private static boolean isClickOnRightSide(@NotNull PlacementState state, @NotNull Point blockPos, @NotNull BlockFace facing) {
        double hitX = PlacementRuleUtils.localX(state.cursorPosition(), blockPos);
        double hitZ = PlacementRuleUtils.localZ(state.cursorPosition(), blockPos);
        return switch (facing) {
            case NORTH -> hitX > 0.5;
            case SOUTH -> hitX < 0.5;
            case WEST -> hitZ < 0.5;
            case EAST -> hitZ > 0.5;
            default -> false;
        };
    }

    private static boolean isMatchingDoor(@Nullable Block neighbor, @NotNull Block doorBase, @NotNull BlockFace facing) {
        if (!isDoorBlock(neighbor) || !neighbor.namespace().equals(doorBase.namespace())) {
            return false;
        }
        BlockFace neighborFacing = PlacementRuleUtils.faceFromProperty(neighbor.getProperty(PlacementRuleUtils.PROP_FACING));
        String half = neighbor.getProperty(PROP_HALF);
        return neighborFacing == facing && HALF_LOWER.equals(half);
    }

    private static boolean isDoorBlock(@Nullable Block block) {
        return block != null && block.namespace().path().endsWith("_door") && !block.namespace().path().endsWith("_trapdoor");
    }
}

