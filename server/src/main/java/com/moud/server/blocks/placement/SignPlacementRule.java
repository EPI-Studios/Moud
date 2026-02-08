package com.moud.server.blocks.placement;

import net.minestom.server.coordinate.Point;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockFace;
import net.minestom.server.instance.block.rule.BlockPlacementRule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class SignPlacementRule extends BlockPlacementRule {
    private static final String PROP_ROTATION = "rotation";

    SignPlacementRule(@NotNull Block block) {
        super(block);
    }

    @Override
    public @Nullable Block blockPlace(@NotNull PlacementState state) {
        BlockFace face = state.blockFace();
        if (face == BlockFace.BOTTOM) {
            return null;
        }

        Block base = state.block().defaultState();
        boolean isWallSign = base.namespace().path().endsWith("_wall_sign");
        if (face == BlockFace.TOP) {
            if (isWallSign) {
                String standingNamespace = toStandingSignNamespace(base.namespace().asString());
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
            Block sign = base.withProperty(PROP_ROTATION, Integer.toString(segment));
            sign = PlacementRuleUtils.setWaterlogged(sign, PlacementRuleUtils.shouldWaterlog(state));
            return sign;
        }

        String wallNamespace = toWallSignNamespace(base.namespace().asString());
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

        Block sign = wall.withProperty(PlacementRuleUtils.PROP_FACING, face.name().toLowerCase());
        sign = PlacementRuleUtils.setWaterlogged(sign, PlacementRuleUtils.shouldWaterlog(state));
        return sign;
    }

    @Override
    public @NotNull Block blockUpdate(@NotNull UpdateState state) {
        Block current = state.currentBlock();
        if (current == null || current.isAir()) {
            return current;
        }

        String path = current.namespace().path();
        Point pos = state.blockPosition();
        if (path.endsWith("_wall_sign")) {
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

        if (path.endsWith("_sign")) {
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

    private static String toWallSignNamespace(String baseNamespace) {
        if (baseNamespace == null) {
            return null;
        }
        int split = baseNamespace.indexOf(':');
        if (split <= 0 || split == baseNamespace.length() - 1) {
            return null;
        }
        String domain = baseNamespace.substring(0, split);
        String path = baseNamespace.substring(split + 1);
        if (path.endsWith("_wall_sign")) {
            return baseNamespace;
        }
        if (!path.endsWith("_sign") || path.endsWith("_hanging_sign")) {
            return null;
        }
        String wallPath = path.substring(0, path.length() - "_sign".length()) + "_wall_sign";
        return domain + ":" + wallPath;
    }

    private static String toStandingSignNamespace(String baseNamespace) {
        if (baseNamespace == null) {
            return null;
        }
        int split = baseNamespace.indexOf(':');
        if (split <= 0 || split == baseNamespace.length() - 1) {
            return null;
        }
        String domain = baseNamespace.substring(0, split);
        String path = baseNamespace.substring(split + 1);
        if (path.endsWith("_sign") && !path.endsWith("_wall_sign") && !path.endsWith("_hanging_sign")) {
            return baseNamespace;
        }
        if (!path.endsWith("_wall_sign")) {
            return null;
        }
        String standingPath = path.substring(0, path.length() - "_wall_sign".length()) + "_sign";
        return domain + ":" + standingPath;
    }
}

