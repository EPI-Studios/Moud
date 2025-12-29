package com.moud.server.blocks.placement;

import net.minestom.server.coordinate.Point;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockFace;
import net.minestom.server.instance.block.rule.BlockPlacementRule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class SkullPlacementRule extends BlockPlacementRule {
    private static final String PROP_ROTATION = "rotation";

    SkullPlacementRule(@NotNull Block block) {
        super(block);
    }

    @Override
    public @Nullable Block blockPlace(@NotNull PlacementState state) {
        BlockFace face = state.blockFace();
        if (face == BlockFace.BOTTOM) {
            return null;
        }

        Block base = state.block().defaultState();
        boolean isWall = isWallSkull(base);
        if (face == BlockFace.TOP) {
            if (isWall) {
                String standingNamespace = toStandingNamespace(base.namespace().asString());
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
            if (!PlacementRuleUtils.isFaceFull(support, BlockFace.TOP)) {
                return null;
            }

            int segment = rotationSegment(state.playerPosition().yaw());
            Block skull = base.withProperty(PROP_ROTATION, Integer.toString(segment));
            skull = PlacementRuleUtils.setWaterlogged(skull, PlacementRuleUtils.shouldWaterlog(state));
            return skull;
        }

        String wallNamespace = toWallNamespace(base.namespace().asString());
        if (wallNamespace == null) {
            return null;
        }

        Block wall = Block.fromNamespaceId(wallNamespace);
        if (wall == null) {
            return null;
        }

        Point pos = state.placePosition();
        Block support = state.instance().getBlock(pos.relative(PlacementRuleUtils.opposite(face)));
        if (!PlacementRuleUtils.isFaceFull(support, face)) {
            return null;
        }

        Block skull = wall.withProperty(PlacementRuleUtils.PROP_FACING, face.name().toLowerCase());
        skull = PlacementRuleUtils.setWaterlogged(skull, PlacementRuleUtils.shouldWaterlog(state));
        return skull;
    }

    @Override
    public @NotNull Block blockUpdate(@NotNull UpdateState state) {
        Block current = state.currentBlock();
        if (current == null || current.isAir()) {
            return current;
        }

        Point pos = state.blockPosition();
        if (isWallSkull(current)) {
            String facingValue = current.getProperty(PlacementRuleUtils.PROP_FACING);
            if (facingValue == null) {
                return current;
            }
            BlockFace facing = PlacementRuleUtils.faceFromProperty(facingValue);
            Block support = state.instance().getBlock(pos.relative(PlacementRuleUtils.opposite(facing)));
            if (!PlacementRuleUtils.isFaceFull(support, facing)) {
                return Block.AIR;
            }
            return current;
        }

        if (isStandingSkull(current)) {
            Block support = state.instance().getBlock(pos.relative(BlockFace.BOTTOM));
            if (!PlacementRuleUtils.isFaceFull(support, BlockFace.TOP)) {
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

    private static boolean isWallSkull(@NotNull Block block) {
        String path = block.namespace().path();
        return path.endsWith("_wall_skull") || path.endsWith("_wall_head");
    }

    private static boolean isStandingSkull(@NotNull Block block) {
        String path = block.namespace().path();
        return path.endsWith("_skull") || path.endsWith("_head");
    }

    private static String toWallNamespace(String baseNamespace) {
        if (baseNamespace == null) {
            return null;
        }
        int split = baseNamespace.indexOf(':');
        if (split <= 0 || split == baseNamespace.length() - 1) {
            return null;
        }
        String domain = baseNamespace.substring(0, split);
        String path = baseNamespace.substring(split + 1);
        if (path.endsWith("_wall_skull") || path.endsWith("_wall_head")) {
            return baseNamespace;
        }
        if (path.endsWith("_skull")) {
            return domain + ":" + path.substring(0, path.length() - "_skull".length()) + "_wall_skull";
        }
        if (path.endsWith("_head")) {
            return domain + ":" + path.substring(0, path.length() - "_head".length()) + "_wall_head";
        }
        return null;
    }

    private static String toStandingNamespace(String baseNamespace) {
        if (baseNamespace == null) {
            return null;
        }
        int split = baseNamespace.indexOf(':');
        if (split <= 0 || split == baseNamespace.length() - 1) {
            return null;
        }
        String domain = baseNamespace.substring(0, split);
        String path = baseNamespace.substring(split + 1);
        if (path.endsWith("_wall_skull")) {
            return domain + ":" + path.substring(0, path.length() - "_wall_skull".length()) + "_skull";
        }
        if (path.endsWith("_wall_head")) {
            return domain + ":" + path.substring(0, path.length() - "_wall_head".length()) + "_head";
        }
        if (path.endsWith("_skull") || path.endsWith("_head")) {
            return baseNamespace;
        }
        return null;
    }
}

