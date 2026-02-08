package com.moud.server.blocks.placement;

import net.minestom.server.coordinate.Point;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.rule.BlockPlacementRule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class CandlePlacementRule extends BlockPlacementRule {
    private static final String PROP_CANDLES = "candles";

    CandlePlacementRule(@NotNull Block block) {
        super(block);
    }

    @Override
    public boolean isSelfReplaceable(@NotNull Replacement replacement) {
        Block existing = replacement.block();
        if (existing == null || !existing.namespace().equals(getBlock().namespace())) {
            return false;
        }
        String candles = existing.getProperty(PROP_CANDLES);
        if (candles == null) {
            return false;
        }
        try {
            return Integer.parseInt(candles) < 4;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    @Override
    public @Nullable Block blockPlace(@NotNull PlacementState state) {
        Block base = state.block().defaultState();
        if (!PlacementRuleUtils.hasProperty(base, PROP_CANDLES)) {
            return base;
        }

        Point pos = state.placePosition();
        Block existing = state.instance().getBlock(pos);
        if (existing != null && existing.namespace().equals(base.namespace())) {
            String candles = existing.getProperty(PROP_CANDLES);
            if (candles != null) {
                try {
                    int current = Integer.parseInt(candles);
                    if (current >= 1 && current < 4) {
                        return existing.withProperty(PROP_CANDLES, Integer.toString(current + 1));
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }

        base = base.withProperty(PROP_CANDLES, "1");
        base = PlacementRuleUtils.setWaterlogged(base, PlacementRuleUtils.shouldWaterlog(state));
        return base;
    }
}

