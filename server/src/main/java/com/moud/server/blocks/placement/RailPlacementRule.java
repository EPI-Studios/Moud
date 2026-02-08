package com.moud.server.blocks.placement;

import java.util.Set;

import net.minestom.server.coordinate.Point;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockFace;
import net.minestom.server.instance.block.rule.BlockPlacementRule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class RailPlacementRule extends BlockPlacementRule {
    private static final String PROP_SHAPE = "shape";

    private static final Set<String> CORNER_VALUES = Set.of("north_east", "north_west", "south_east", "south_west");

    RailPlacementRule(@NotNull Block block) {
        super(block);
    }

    @Override
    public @Nullable Block blockPlace(@NotNull PlacementState state) {
        Block rail = state.block().defaultState();
        if (!PlacementRuleUtils.hasProperty(rail, PROP_SHAPE)) {
            return rail;
        }

        Point pos = state.placePosition();
        Block support = state.instance().getBlock(pos.relative(BlockFace.BOTTOM));
        if (!PlacementRuleUtils.isFaceFull(support, BlockFace.TOP)) {
            return null;
        }

        String shape = computeShape(state.instance(), pos, rail, state.playerPosition().yaw());
        return rail.withProperty(PROP_SHAPE, shape);
    }

    @Override
    public @NotNull Block blockUpdate(@NotNull UpdateState state) {
        Block current = state.currentBlock();
        if (!isRail(current) || !PlacementRuleUtils.hasProperty(current, PROP_SHAPE)) {
            return current;
        }

        Block support = state.instance().getBlock(state.blockPosition().relative(BlockFace.BOTTOM));
        if (!PlacementRuleUtils.isFaceFull(support, BlockFace.TOP)) {
            return Block.AIR;
        }

        String shape = computeShape(state.instance(), state.blockPosition(), current, 0.0f);
        return current.withProperty(PROP_SHAPE, shape);
    }

    private static @NotNull String computeShape(@NotNull Block.Getter instance, @NotNull Point pos, @NotNull Block rail, float yaw) {
        boolean north = isRail(instance.getBlock(pos.relative(BlockFace.NORTH)));
        boolean east = isRail(instance.getBlock(pos.relative(BlockFace.EAST)));
        boolean south = isRail(instance.getBlock(pos.relative(BlockFace.SOUTH)));
        boolean west = isRail(instance.getBlock(pos.relative(BlockFace.WEST)));

        boolean cornersAllowed = cornersAllowed(rail);
        if (cornersAllowed) {
            String corner = cornerShape(north, east, south, west);
            if (corner != null) {
                return corner;
            }
        }

        boolean ns = north || south;
        boolean ew = east || west;
        if (ns && !ew) {
            return "north_south";
        }
        if (ew && !ns) {
            return "east_west";
        }

        if (yaw != 0.0f) {
            BlockFace playerFacing = PlacementRuleUtils.horizontalFacingFromYaw(yaw);
            return (playerFacing == BlockFace.NORTH || playerFacing == BlockFace.SOUTH) ? "north_south" : "east_west";
        }

        String existing = rail.getProperty(PROP_SHAPE);
        if (existing != null) {
            return existing;
        }
        return "north_south";
    }

    private static @Nullable String cornerShape(boolean north, boolean east, boolean south, boolean west) {
        if (north && east && !south && !west) {
            return "north_east";
        }
        if (north && west && !south && !east) {
            return "north_west";
        }
        if (south && east && !north && !west) {
            return "south_east";
        }
        if (south && west && !north && !east) {
            return "south_west";
        }
        return null;
    }

    private static boolean cornersAllowed(@NotNull Block rail) {
        for (Block state : rail.defaultState().possibleStates()) {
            String value = state.getProperty(PROP_SHAPE);
            if (value == null) {
                continue;
            }
            if (CORNER_VALUES.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isRail(@Nullable Block block) {
        if (block == null) {
            return false;
        }
        String path = block.namespace().path();
        return "rail".equals(path) || path.endsWith("_rail");
    }
}

