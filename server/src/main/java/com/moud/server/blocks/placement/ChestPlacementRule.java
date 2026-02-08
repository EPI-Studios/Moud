package com.moud.server.blocks.placement;

import net.minestom.server.coordinate.Point;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockFace;
import net.minestom.server.instance.block.rule.BlockPlacementRule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class ChestPlacementRule extends BlockPlacementRule {
    private static final String PROP_FACING = "facing";
    private static final String PROP_TYPE = "type";

    private static final String TYPE_SINGLE = "single";
    private static final String TYPE_LEFT = "left";
    private static final String TYPE_RIGHT = "right";

    ChestPlacementRule(@NotNull Block block) {
        super(block);
    }

    @Override
    public @Nullable Block blockPlace(@NotNull PlacementState state) {
        Block base = state.block().defaultState();
        if (!PlacementRuleUtils.hasProperty(base, PROP_FACING) || !PlacementRuleUtils.hasProperty(base, PROP_TYPE)) {
            return base;
        }

        BlockFace playerFacing = PlacementRuleUtils.horizontalFacingFromYaw(state.playerPosition().yaw());
        BlockFace facing = PlacementRuleUtils.opposite(playerFacing);
        base = base.withProperty(PROP_FACING, facing.name().toLowerCase()).withProperty(PROP_TYPE, TYPE_SINGLE);

        if (state.isPlayerShifting()) {
            return base;
        }

        Point pos = state.placePosition();
        BlockFace leftDir = PlacementRuleUtils.rotateLeft(facing);
        BlockFace rightDir = PlacementRuleUtils.rotateRight(facing);

        Block left = state.instance().getBlock(pos.relative(leftDir));
        Block right = state.instance().getBlock(pos.relative(rightDir));

        boolean canMergeLeft = canMergeWith(left, base, facing);
        boolean canMergeRight = canMergeWith(right, base, facing);

        if (!canMergeLeft && !canMergeRight) {
            return base;
        }

        boolean mergeLeft = canMergeLeft && !canMergeRight;
        if (canMergeLeft && canMergeRight) {
            mergeLeft = true;
        }

        if (!(state.instance() instanceof Instance instance)) {
            return base;
        }

        if (mergeLeft) {
            Block updatedSelf = base.withProperty(PROP_TYPE, TYPE_RIGHT);
            Block updatedNeighbor = left.withProperty(PROP_TYPE, TYPE_LEFT);
            instance.setBlock(pos.relative(leftDir), updatedNeighbor);
            return updatedSelf;
        }

        Block updatedSelf = base.withProperty(PROP_TYPE, TYPE_LEFT);
        Block updatedNeighbor = right.withProperty(PROP_TYPE, TYPE_RIGHT);
        instance.setBlock(pos.relative(rightDir), updatedNeighbor);
        return updatedSelf;
    }

    @Override
    public @NotNull Block blockUpdate(@NotNull UpdateState state) {
        Block current = state.currentBlock();
        if (!isChest(current)) {
            return current;
        }

        String type = current.getProperty(PROP_TYPE);
        if (type == null || TYPE_SINGLE.equals(type)) {
            return current;
        }

        String facingValue = current.getProperty(PROP_FACING);
        if (facingValue == null) {
            return current;
        }
        BlockFace facing = PlacementRuleUtils.faceFromProperty(facingValue);

        BlockFace otherDir = otherHalfOffset(facing, type);
        Block other = state.instance().getBlock(state.blockPosition().relative(otherDir));
        if (!canMergeWith(other, current.withProperty(PROP_TYPE, TYPE_SINGLE), facing)) {
            return current.withProperty(PROP_TYPE, TYPE_SINGLE);
        }

        return current;
    }

    private static boolean canMergeWith(@Nullable Block neighbor, @NotNull Block base, @NotNull BlockFace facing) {
        if (!isChest(neighbor) || !neighbor.namespace().equals(base.namespace())) {
            return false;
        }
        String neighborType = neighbor.getProperty(PROP_TYPE);
        if (neighborType == null || !TYPE_SINGLE.equals(neighborType)) {
            return false;
        }
        String neighborFacing = neighbor.getProperty(PROP_FACING);
        return neighborFacing != null && facing == PlacementRuleUtils.faceFromProperty(neighborFacing);
    }

    private static BlockFace otherHalfOffset(@NotNull BlockFace facing, @NotNull String type) {
        if (TYPE_LEFT.equals(type)) {
            return PlacementRuleUtils.rotateRight(facing);
        }
        if (TYPE_RIGHT.equals(type)) {
            return PlacementRuleUtils.rotateLeft(facing);
        }
        return PlacementRuleUtils.rotateRight(facing);
    }

    private static boolean isChest(@Nullable Block block) {
        if (block == null) {
            return false;
        }
        String path = block.namespace().path();
        return "chest".equals(path) || "trapped_chest".equals(path);
    }
}

