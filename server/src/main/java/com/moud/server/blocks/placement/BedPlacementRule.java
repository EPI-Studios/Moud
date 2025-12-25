package com.moud.server.blocks.placement;

import net.minestom.server.coordinate.Point;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockFace;
import net.minestom.server.instance.block.rule.BlockPlacementRule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class BedPlacementRule extends BlockPlacementRule {
    private static final String PROP_FACING = "facing";
    private static final String PROP_PART = "part";

    BedPlacementRule(@NotNull Block block) {
        super(block);
    }

    @Override
    public @Nullable Block blockPlace(@NotNull PlacementState state) {
        BlockFace facing = facingFromYaw(state.playerPosition().yaw());
        String facingProp = facing.name().toLowerCase();

        Block bed = state.block().defaultState()
                .withProperty(PROP_FACING, facingProp)
                .withProperty(PROP_PART, "foot");

        Point footPos = state.placePosition();
        Point headPos = footPos.relative(facing);

        Block existing = state.instance().getBlock(headPos);
        boolean canReplace = existing.isAir() || existing.registry().isReplaceable();
        if (!canReplace) {
            return null;
        }

        Block supportFoot = state.instance().getBlock(footPos.relative(BlockFace.BOTTOM));
        Block supportHead = state.instance().getBlock(headPos.relative(BlockFace.BOTTOM));
        if (!supportFoot.registry().collisionShape().isFaceFull(BlockFace.TOP)
                || !supportHead.registry().collisionShape().isFaceFull(BlockFace.TOP)) {
            return null;
        }

        Block head = bed.withProperty(PROP_PART, "head");
        if (!(state.instance() instanceof Instance instance)) {
            return null;
        }
        instance.setBlock(headPos, head);
        return bed;
    }

    @Override
    public @NotNull Block blockUpdate(@NotNull UpdateState state) {
        Block current = state.currentBlock();
        if (!isBedBlock(current)) {
            return current;
        }

        Block support = state.instance().getBlock(state.blockPosition().relative(BlockFace.BOTTOM));
        if (!support.registry().collisionShape().isFaceFull(BlockFace.TOP)) {
            return Block.AIR;
        }

        String facingValue = current.getProperty(PROP_FACING);
        String part = current.getProperty(PROP_PART);
        if (facingValue == null || part == null) {
            return current;
        }

        BlockFace facing = faceFromProperty(facingValue);
        BlockFace otherOffset = "head".equals(part) ? opposite(facing) : facing;

        Point otherPos = state.blockPosition().relative(otherOffset);
        Block other = state.instance().getBlock(otherPos);
        if (!isBedBlock(other) || !other.namespace().equals(current.namespace())) {
            return Block.AIR;
        }

        return current;
    }

    private static boolean isBedBlock(Block block) {
        return block != null && block.namespace().path().endsWith("_bed");
    }

    private static BlockFace faceFromProperty(String facingValue) {
        return switch (facingValue) {
            case "north" -> BlockFace.NORTH;
            case "south" -> BlockFace.SOUTH;
            case "east" -> BlockFace.EAST;
            case "west" -> BlockFace.WEST;
            default -> BlockFace.NORTH;
        };
    }

    private static BlockFace facingFromYaw(float yawDegrees) {
        double yaw = yawDegrees;
        int idx = Math.floorMod((int) Math.floor(yaw / 90.0 + 0.5), 4);
        return switch (idx) {
            case 0 -> BlockFace.SOUTH;
            case 1 -> BlockFace.WEST;
            case 2 -> BlockFace.NORTH;
            case 3 -> BlockFace.EAST;
            default -> BlockFace.NORTH;
        };
    }

    private static BlockFace opposite(BlockFace face) {
        return switch (face) {
            case NORTH -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.NORTH;
            case EAST -> BlockFace.WEST;
            case WEST -> BlockFace.EAST;
            case TOP -> BlockFace.BOTTOM;
            case BOTTOM -> BlockFace.TOP;
            default -> face;
        };
    }
}
