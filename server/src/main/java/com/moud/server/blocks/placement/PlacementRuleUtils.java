package com.moud.server.blocks.placement;

import net.minestom.server.coordinate.Point;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockFace;
import net.minestom.server.instance.block.rule.BlockPlacementRule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class PlacementRuleUtils {
    static final String PROP_FACING = "facing";
    static final String PROP_WATERLOGGED = "waterlogged";

    private PlacementRuleUtils() {
    }

    static boolean hasProperty(@NotNull Block block, @NotNull String name) {
        return block.properties().containsKey(name);
    }

    static @NotNull Block setWaterlogged(@NotNull Block block, boolean waterlogged) {
        if (!hasProperty(block, PROP_WATERLOGGED)) {
            return block;
        }
        return block.withProperty(PROP_WATERLOGGED, waterlogged ? "true" : "false");
    }

    static boolean shouldWaterlog(@NotNull BlockPlacementRule.PlacementState state) {
        Block existing = state.instance().getBlock(state.placePosition());
        return existing != null && "minecraft:water".equals(existing.namespace().asString());
    }

    static double localX(@NotNull Point cursorPosition, @NotNull Point blockPosition) {
        return cursorPosition.x() - blockPosition.x();
    }

    static double localY(@NotNull Point cursorPosition, @NotNull Point blockPosition) {
        return cursorPosition.y() - blockPosition.y();
    }

    static double localZ(@NotNull Point cursorPosition, @NotNull Point blockPosition) {
        return cursorPosition.z() - blockPosition.z();
    }

    static @NotNull BlockFace horizontalFacingFromYaw(float yawDegrees) {
        int idx = Math.floorMod((int) Math.floor(yawDegrees / 90.0 + 0.5), 4);
        return switch (idx) {
            case 0 -> BlockFace.SOUTH;
            case 1 -> BlockFace.WEST;
            case 2 -> BlockFace.NORTH;
            case 3 -> BlockFace.EAST;
            default -> BlockFace.NORTH;
        };
    }

    static @NotNull BlockFace faceFromProperty(@Nullable String facingValue) {
        if (facingValue == null) {
            return BlockFace.NORTH;
        }
        return switch (facingValue) {
            case "north" -> BlockFace.NORTH;
            case "south" -> BlockFace.SOUTH;
            case "east" -> BlockFace.EAST;
            case "west" -> BlockFace.WEST;
            case "up" -> BlockFace.TOP;
            case "down" -> BlockFace.BOTTOM;
            default -> BlockFace.NORTH;
        };
    }

    static @NotNull BlockFace opposite(@NotNull BlockFace face) {
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

    static @NotNull BlockFace rotateLeft(@NotNull BlockFace face) {
        return switch (face) {
            case NORTH -> BlockFace.WEST;
            case WEST -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.EAST;
            case EAST -> BlockFace.NORTH;
            default -> face;
        };
    }

    static @NotNull BlockFace rotateRight(@NotNull BlockFace face) {
        return switch (face) {
            case NORTH -> BlockFace.EAST;
            case EAST -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.WEST;
            case WEST -> BlockFace.NORTH;
            default -> face;
        };
    }

    static @NotNull String facingPropertyValue(@NotNull BlockFace face) {
        return switch (face) {
            case TOP -> "up";
            case BOTTOM -> "down";
            default -> face.name().toLowerCase();
        };
    }

    static boolean canReplace(@Nullable Block block) {
        if (block == null) {
            return true;
        }
        return block.isAir() || block.registry().isReplaceable();
    }

    static boolean isFaceFull(@Nullable Block block, @NotNull BlockFace face) {
        if (block == null || block.isAir()) {
            return false;
        }
        return block.registry().collisionShape().isFaceFull(face);
    }
}
