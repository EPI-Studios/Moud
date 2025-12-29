package com.moud.server.blocks.placement;

import net.minestom.server.coordinate.Point;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockFace;
import net.minestom.server.instance.block.rule.BlockPlacementRule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class SnowLayerPlacementRule extends BlockPlacementRule {
    private static final String PROP_LAYERS = "layers";

    SnowLayerPlacementRule(@NotNull Block block) {
        super(block);
    }

    @Override
    public boolean isSelfReplaceable(@NotNull Replacement replacement) {
        Block existing = replacement.block();
        if (existing == null || !isSnow(existing)) {
            return false;
        }
        String layers = existing.getProperty(PROP_LAYERS);
        if (layers == null) {
            return false;
        }
        try {
            return Integer.parseInt(layers) < 8;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    @Override
    public @Nullable Block blockPlace(@NotNull PlacementState state) {
        Block snow = state.block().defaultState();
        Point pos = state.placePosition();

        Block existing = state.instance().getBlock(pos);
        if (existing != null && isSnow(existing)) {
            String layers = existing.getProperty(PROP_LAYERS);
            if (layers != null) {
                try {
                    int current = Integer.parseInt(layers);
                    if (current >= 1 && current < 8) {
                        return existing.withProperty(PROP_LAYERS, Integer.toString(current + 1));
                    }
                } catch (NumberFormatException ignored) {
                }
            }
            return existing;
        }

        Block support = state.instance().getBlock(pos.relative(BlockFace.BOTTOM));
        if (!PlacementRuleUtils.isFaceFull(support, BlockFace.TOP)) {
            return null;
        }

        if (PlacementRuleUtils.hasProperty(snow, PROP_LAYERS)) {
            snow = snow.withProperty(PROP_LAYERS, "1");
        }
        return snow;
    }

    @Override
    public @NotNull Block blockUpdate(@NotNull UpdateState state) {
        Block current = state.currentBlock();
        if (!isSnow(current)) {
            return current;
        }
        Block support = state.instance().getBlock(state.blockPosition().relative(BlockFace.BOTTOM));
        if (!PlacementRuleUtils.isFaceFull(support, BlockFace.TOP)) {
            return Block.AIR;
        }
        return current;
    }

    private static boolean isSnow(@Nullable Block block) {
        return block != null && "snow".equals(block.namespace().path());
    }
}

