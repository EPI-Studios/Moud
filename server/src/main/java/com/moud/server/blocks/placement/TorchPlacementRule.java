package com.moud.server.blocks.placement;

import net.minestom.server.coordinate.Point;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockFace;
import net.minestom.server.instance.block.rule.BlockPlacementRule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class TorchPlacementRule extends BlockPlacementRule {
    private static final String PROP_FACING = "facing";

    TorchPlacementRule(@NotNull Block block) {
        super(block);
    }

    @Override
    public @Nullable Block blockPlace(@NotNull PlacementState state) {
        BlockFace face = state.blockFace();
        if (face == BlockFace.BOTTOM) {
            return null;
        }

        Block base = state.block().defaultState();
        boolean isWallTorch = base.namespace().path().endsWith("_wall_torch");
        if (face == BlockFace.TOP) {
            if (isWallTorch) {
                String standingNamespace = toStandingTorchNamespace(base.namespace().asString());
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
            return base;
        }

        String wallNamespace = toWallTorchNamespace(base.namespace().asString());
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
        if (path.endsWith("_wall_torch")) {
            String facingValue = current.getProperty(PROP_FACING);
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

        if (path.endsWith("_torch")) {
            Block support = state.instance().getBlock(pos.relative(BlockFace.BOTTOM));
            if (!PlacementRuleUtils.isFaceFull(support, BlockFace.TOP)) {
                return Block.AIR;
            }
        }

        return current;
    }

    private static String toWallTorchNamespace(String baseNamespace) {
        if (baseNamespace == null) {
            return null;
        }
        int split = baseNamespace.indexOf(':');
        if (split <= 0 || split == baseNamespace.length() - 1) {
            return null;
        }
        String domain = baseNamespace.substring(0, split);
        String path = baseNamespace.substring(split + 1);
        if (path.endsWith("_wall_torch")) {
            return baseNamespace;
        }
        if (!path.endsWith("_torch")) {
            return null;
        }
        String wallPath = path.substring(0, path.length() - "_torch".length()) + "_wall_torch";
        return domain + ":" + wallPath;
    }

    private static String toStandingTorchNamespace(String baseNamespace) {
        if (baseNamespace == null) {
            return null;
        }
        int split = baseNamespace.indexOf(':');
        if (split <= 0 || split == baseNamespace.length() - 1) {
            return null;
        }
        String domain = baseNamespace.substring(0, split);
        String path = baseNamespace.substring(split + 1);
        if (path.endsWith("_torch") && !path.endsWith("_wall_torch")) {
            return baseNamespace;
        }
        if (!path.endsWith("_wall_torch")) {
            return null;
        }
        String standingPath = path.substring(0, path.length() - "_wall_torch".length()) + "_torch";
        return domain + ":" + standingPath;
    }
}

