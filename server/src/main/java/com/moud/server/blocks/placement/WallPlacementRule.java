package com.moud.server.blocks.placement;

import java.util.Map;
import java.util.Set;

import net.minestom.server.coordinate.Point;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockFace;
import net.minestom.server.instance.block.rule.BlockPlacementRule;
import net.minestom.server.utils.NamespaceID;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class WallPlacementRule extends BlockPlacementRule {
    private static final String PROP_UP = "up";
    private static final String PROP_WATERLOGGED = "waterlogged";
    private static final Map<BlockFace, String> SIDE_PROPERTIES = Map.of(
            BlockFace.NORTH, "north",
            BlockFace.EAST, "east",
            BlockFace.SOUTH, "south",
            BlockFace.WEST, "west"
    );

    private static final String SIDE_NONE = "none";
    private static final String SIDE_LOW = "low";
    private static final String SIDE_TALL = "tall";

    private static final NamespaceID IRON_BARS = NamespaceID.from("minecraft:iron_bars");

    private static final Set<String> EXCEPTION_BLOCKS = Set.of(
            "minecraft:barrier",
            "minecraft:carved_pumpkin",
            "minecraft:jack_o_lantern",
            "minecraft:melon",
            "minecraft:pumpkin"
    );

    WallPlacementRule(@NotNull Block block) {
        super(block);
    }

    @Override
    public @Nullable Block blockPlace(@NotNull PlacementState state) {
        Point pos = state.placePosition();
        Block above = state.instance().getBlock(pos.relative(BlockFace.TOP));
        boolean aboveSupportsTall = supportsWallTall(above);

        boolean connectNorth = connectsTo(state, pos, BlockFace.NORTH);
        boolean connectEast = connectsTo(state, pos, BlockFace.EAST);
        boolean connectSouth = connectsTo(state, pos, BlockFace.SOUTH);
        boolean connectWest = connectsTo(state, pos, BlockFace.WEST);

        Block wall = state.block().defaultState();
        wall = wall.withProperty(PROP_WATERLOGGED, shouldWaterlog(state) ? "true" : "false");
        wall = applySides(wall, connectNorth, connectEast, connectSouth, connectWest, aboveSupportsTall);
        wall = wall.withProperty(PROP_UP, shouldRaisePost(wall, above) ? "true" : "false");
        return wall;
    }

    @Override
    public @NotNull Block blockUpdate(@NotNull UpdateState state) {
        Block current = state.currentBlock();
        if (!isWallBlock(current)) {
            return current;
        }

        Point pos = state.blockPosition();
        Block above = state.instance().getBlock(pos.relative(BlockFace.TOP));
        boolean aboveSupportsTall = supportsWallTall(above);

        boolean connectNorth = connectsTo(state, pos, BlockFace.NORTH);
        boolean connectEast = connectsTo(state, pos, BlockFace.EAST);
        boolean connectSouth = connectsTo(state, pos, BlockFace.SOUTH);
        boolean connectWest = connectsTo(state, pos, BlockFace.WEST);

        Block updated = applySides(current, connectNorth, connectEast, connectSouth, connectWest, aboveSupportsTall);
        updated = updated.withProperty(PROP_UP, shouldRaisePost(updated, above) ? "true" : "false");
        return updated;
    }

    private static Block applySides(Block base,
                                   boolean north,
                                   boolean east,
                                   boolean south,
                                   boolean west,
                                   boolean aboveSupportsTall) {
        return base.withProperties(Map.of(
                sideProperty(BlockFace.NORTH), wallSideValue(north, aboveSupportsTall),
                sideProperty(BlockFace.EAST), wallSideValue(east, aboveSupportsTall),
                sideProperty(BlockFace.SOUTH), wallSideValue(south, aboveSupportsTall),
                sideProperty(BlockFace.WEST), wallSideValue(west, aboveSupportsTall)
        ));
    }

    private static String wallSideValue(boolean connected, boolean aboveSupportsTall) {
        if (!connected) {
            return SIDE_NONE;
        }
        return aboveSupportsTall ? SIDE_TALL : SIDE_LOW;
    }

    private static boolean shouldWaterlog(PlacementState state) {
        Block existing = state.instance().getBlock(state.placePosition());
        return "minecraft:water".equals(existing.namespace().asString());
    }

    private static boolean isWallBlock(Block block) {
        if (block == null) {
            return false;
        }
        return block.namespace().path().endsWith("_wall");
    }

    private static boolean supportsWallTall(Block blockAbove) {
        if (blockAbove == null || blockAbove.isAir()) {
            return false;
        }
        return blockAbove.registry().collisionShape().isFaceFull(BlockFace.BOTTOM);
    }

    private static boolean shouldRaisePost(Block wallState, Block blockAbove) {
        if (blockAbove != null && isWallBlock(blockAbove) && "true".equals(blockAbove.getProperty(PROP_UP))) {
            return true;
        }

        String north = wallState.getProperty(sideProperty(BlockFace.NORTH));
        String east = wallState.getProperty(sideProperty(BlockFace.EAST));
        String south = wallState.getProperty(sideProperty(BlockFace.SOUTH));
        String west = wallState.getProperty(sideProperty(BlockFace.WEST));

        boolean n = !SIDE_NONE.equals(north);
        boolean e = !SIDE_NONE.equals(east);
        boolean s = !SIDE_NONE.equals(south);
        boolean w = !SIDE_NONE.equals(west);

        boolean none = !n && !e && !s && !w;
        if (none) {
            return true;
        }

        boolean northNone = !n;
        boolean southNone = !s;
        boolean eastNone = !e;
        boolean westNone = !w;
        boolean asymmetry = (northNone && southNone && eastNone && westNone)
                || (northNone != southNone)
                || (eastNone != westNone);
        if (asymmetry) {
            return true;
        }

        boolean oppositeTall = (SIDE_TALL.equals(north) && SIDE_TALL.equals(south))
                || (SIDE_TALL.equals(east) && SIDE_TALL.equals(west));
        if (oppositeTall) {
            return false;
        }

        return blockAbove != null && supportsWallTall(blockAbove);
    }

    private static boolean connectsTo(PlacementState state, Point pos, BlockFace face) {
        Point neighborPos = pos.relative(face);
        Block neighbor = state.instance().getBlock(neighborPos);
        return connectsTo(neighbor, oppositeFace(face));
    }

    private static boolean connectsTo(UpdateState state, Point pos, BlockFace face) {
        Point neighborPos = pos.relative(face);
        Block neighbor = state.instance().getBlock(neighborPos);
        return connectsTo(neighbor, oppositeFace(face));
    }

    private static boolean connectsTo(Block neighbor, BlockFace neighborFaceTowardWall) {
        if (neighbor == null || neighbor.isAir()) {
            return false;
        }
        if (isWallBlock(neighbor)) {
            return true;
        }
        if (!isExceptionForConnection(neighbor) && neighbor.registry().collisionShape().isFaceFull(neighborFaceTowardWall)) {
            return true;
        }
        if (IRON_BARS.equals(neighbor.namespace())) {
            return true;
        }
        return neighbor.namespace().path().endsWith("_fence_gate");
    }

    private static boolean isExceptionForConnection(Block block) {
        String id = block.namespace().asString();
        if (EXCEPTION_BLOCKS.contains(id)) {
            return true;
        }
        String path = block.namespace().path();
        if (path.endsWith("_leaves") || path.contains("leaves")) {
            return true;
        }
        return path.endsWith("shulker_box");
    }

    private static BlockFace oppositeFace(BlockFace face) {
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

    private static String sideProperty(BlockFace face) {
        String prop = SIDE_PROPERTIES.get(face);
        if (prop == null) {
            throw new IllegalArgumentException("Unsupported wall side face: " + face);
        }
        return prop;
    }
}
