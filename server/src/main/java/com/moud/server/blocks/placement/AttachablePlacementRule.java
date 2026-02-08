package com.moud.server.blocks.placement;

import net.minestom.server.coordinate.Point;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockFace;
import net.minestom.server.instance.block.rule.BlockPlacementRule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

abstract class AttachablePlacementRule extends BlockPlacementRule {
    static final String PROP_FACE = "face";
    static final String PROP_POWERED = "powered";

    AttachablePlacementRule(@NotNull Block block) {
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

        String faceProp;
        String facingProp;
        if (clickedFace == BlockFace.TOP) {
            faceProp = "floor";
            facingProp = PlacementRuleUtils.horizontalFacingFromYaw(state.playerPosition().yaw()).name().toLowerCase();
        } else if (clickedFace == BlockFace.BOTTOM) {
            faceProp = "ceiling";
            facingProp = PlacementRuleUtils.horizontalFacingFromYaw(state.playerPosition().yaw()).name().toLowerCase();
        } else {
            faceProp = "wall";
            facingProp = clickedFace.name().toLowerCase();
        }

        return state.block().defaultState()
                .withProperty(PROP_FACE, faceProp)
                .withProperty(PlacementRuleUtils.PROP_FACING, facingProp)
                .withProperty(PROP_POWERED, "false");
    }

    @Override
    public @NotNull Block blockUpdate(@NotNull UpdateState state) {
        Block current = state.currentBlock();
        if (!isSupportedAttachable(current, state)) {
            return Block.AIR;
        }
        return current;
    }

    private static boolean isSupportedAttachable(@Nullable Block current, @NotNull UpdateState state) {
        if (current == null || current.isAir() || !PlacementRuleUtils.hasProperty(current, PROP_FACE) || !PlacementRuleUtils.hasProperty(current, PlacementRuleUtils.PROP_FACING)) {
            return true;
        }

        String faceProp = current.getProperty(PROP_FACE);
        String facingProp = current.getProperty(PlacementRuleUtils.PROP_FACING);
        if (faceProp == null || facingProp == null) {
            return true;
        }

        Point pos = state.blockPosition();
        return switch (faceProp) {
            case "floor" -> PlacementRuleUtils.isFaceFull(state.instance().getBlock(pos.relative(BlockFace.BOTTOM)), BlockFace.TOP);
            case "ceiling" -> PlacementRuleUtils.isFaceFull(state.instance().getBlock(pos.relative(BlockFace.TOP)), BlockFace.BOTTOM);
            case "wall" -> {
                BlockFace facing = PlacementRuleUtils.faceFromProperty(facingProp);
                Point supportPos = pos.relative(PlacementRuleUtils.opposite(facing));
                yield PlacementRuleUtils.isFaceFull(state.instance().getBlock(supportPos), facing);
            }
            default -> true;
        };
    }
}
