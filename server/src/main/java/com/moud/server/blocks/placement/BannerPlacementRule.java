package com.moud.server.blocks.placement;

import net.minestom.server.coordinate.Point;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockFace;
import net.minestom.server.instance.block.rule.BlockPlacementRule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class BannerPlacementRule extends BlockPlacementRule {
    private static final String PROP_ROTATION = "rotation";
    private static final String PROP_FACING = "facing";

    BannerPlacementRule(@NotNull Block block) {
        super(block);
    }

    @Override
    public @Nullable Block blockPlace(@NotNull PlacementState state) {
        BlockFace face = state.blockFace();
        if (face == BlockFace.BOTTOM) {
            return null;
        }

        Block base = state.block().defaultState();
        boolean isWallBanner = base.namespace().path().endsWith("_wall_banner");
        if (face == BlockFace.TOP) {
            if (isWallBanner) {
                String standingNamespace = toStandingBannerNamespace(base.namespace().asString());
                if (standingNamespace == null) {
                    return null;
                }
                base = Block.fromNamespaceId(standingNamespace);
                if (base == null) {
                    return null;
                }
            }
            Point pos = state.placePosition();
            Block support = state.instance().getBlock(pos.relative(BlockFace.BOTTOM));
            if (!support.registry().collisionShape().isFaceFull(BlockFace.TOP)) {
                return null;
            }
            int segment = rotationSegment(state.playerPosition().yaw());
            return base.withProperty(PROP_ROTATION, Integer.toString(segment));
        }

        String wallNamespace = toWallBannerNamespace(base.namespace().asString());
        if (wallNamespace == null) {
            return null;
        }

        Block wall = Block.fromNamespaceId(wallNamespace);
        if (wall == null) {
            return null;
        }

        Point pos = state.placePosition();
        Block support = state.instance().getBlock(pos.relative(opposite(face)));
        if (!support.registry().collisionShape().isFaceFull(face)) {
            return null;
        }

        return wall.withProperty(PROP_FACING, face.name().toLowerCase());
    }

    @Override
    public @NotNull Block blockUpdate(@NotNull UpdateState state) {
        Block current = state.currentBlock();
        if (current == null || current.isAir()) {
            return current;
        }

        String path = current.namespace().path();
        Point pos = state.blockPosition();
        if (path.endsWith("_wall_banner")) {
            String facingValue = current.getProperty(PROP_FACING);
            if (facingValue == null) {
                return current;
            }
            BlockFace facing = faceFromProperty(facingValue);
            Block support = state.instance().getBlock(pos.relative(opposite(facing)));
            if (!support.registry().collisionShape().isFaceFull(facing)) {
                return Block.AIR;
            }
            return current;
        }

        if (path.endsWith("_banner")) {
            Block support = state.instance().getBlock(pos.relative(BlockFace.BOTTOM));
            if (!support.registry().collisionShape().isFaceFull(BlockFace.TOP)) {
                return Block.AIR;
            }
        }

        return current;
    }

    private static int rotationSegment(float yawDegrees) {
        double rotation = yawDegrees + 180.0;
        int raw = (int) Math.floor(rotation * 16.0 / 360.0 + 0.5);
        return raw & 15;
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

    private static String toWallBannerNamespace(String baseNamespace) {
        if (baseNamespace == null) {
            return null;
        }
        int split = baseNamespace.indexOf(':');
        if (split <= 0 || split == baseNamespace.length() - 1) {
            return null;
        }
        String domain = baseNamespace.substring(0, split);
        String path = baseNamespace.substring(split + 1);
        if (path.endsWith("_wall_banner")) {
            return baseNamespace;
        }
        if (!path.endsWith("_banner")) {
            return null;
        }
        String wallPath = path.substring(0, path.length() - "_banner".length()) + "_wall_banner";
        return domain + ":" + wallPath;
    }

    private static String toStandingBannerNamespace(String baseNamespace) {
        if (baseNamespace == null) {
            return null;
        }
        int split = baseNamespace.indexOf(':');
        if (split <= 0 || split == baseNamespace.length() - 1) {
            return null;
        }
        String domain = baseNamespace.substring(0, split);
        String path = baseNamespace.substring(split + 1);
        if (path.endsWith("_banner") && !path.endsWith("_wall_banner")) {
            return baseNamespace;
        }
        if (!path.endsWith("_wall_banner")) {
            return null;
        }
        String standingPath = path.substring(0, path.length() - "_wall_banner".length()) + "_banner";
        return domain + ":" + standingPath;
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
