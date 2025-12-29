package com.moud.server.blocks.placement;

import net.minestom.server.coordinate.Point;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockFace;
import net.minestom.server.instance.block.rule.BlockPlacementRule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class FenceGatePlacementRule extends BlockPlacementRule {
    private static final String PROP_OPEN = "open";
    private static final String PROP_POWERED = "powered";
    private static final String PROP_IN_WALL = "in_wall";

    FenceGatePlacementRule(@NotNull Block block) {
        super(block);
    }

    @Override
    public @Nullable Block blockPlace(@NotNull PlacementState state) {
        BlockFace facing = PlacementRuleUtils.horizontalFacingFromYaw(state.playerPosition().yaw());
        Block gate = state.block().defaultState()
                .withProperty(PlacementRuleUtils.PROP_FACING, facing.name().toLowerCase())
                .withProperty(PROP_OPEN, "false")
                .withProperty(PROP_POWERED, "false");
        gate = setInWall(state.instance(), state.placePosition(), gate);
        return gate;
    }

    @Override
    public @NotNull Block blockUpdate(@NotNull UpdateState state) {
        Block current = state.currentBlock();
        if (!isFenceGateBlock(current)) {
            return current;
        }
        return setInWall(state.instance(), state.blockPosition(), current);
    }

    private static @NotNull Block setInWall(@NotNull Block.Getter instance, @NotNull Point pos, @NotNull Block gate) {
        if (!PlacementRuleUtils.hasProperty(gate, PROP_IN_WALL)) {
            return gate;
        }

        BlockFace facing = PlacementRuleUtils.faceFromProperty(gate.getProperty(PlacementRuleUtils.PROP_FACING));
        BlockFace left = PlacementRuleUtils.rotateLeft(facing);
        BlockFace right = PlacementRuleUtils.rotateRight(facing);

        boolean inWall = isWallBlock(instance.getBlock(pos.relative(left))) || isWallBlock(instance.getBlock(pos.relative(right)));
        return gate.withProperty(PROP_IN_WALL, inWall ? "true" : "false");
    }

    private static boolean isWallBlock(@Nullable Block block) {
        return block != null && block.namespace().path().endsWith("_wall");
    }

    private static boolean isFenceGateBlock(@Nullable Block block) {
        return block != null && block.namespace().path().endsWith("_fence_gate");
    }
}

