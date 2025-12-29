package com.moud.server.blocks.placement;

import net.minestom.server.coordinate.Point;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockFace;
import net.minestom.server.instance.block.rule.BlockPlacementRule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class SlabPlacementRule extends BlockPlacementRule {
    private static final String PROP_TYPE = "type";

    private static final String TYPE_BOTTOM = "bottom";
    private static final String TYPE_TOP = "top";
    private static final String TYPE_DOUBLE = "double";

    SlabPlacementRule(@NotNull Block block) {
        super(block);
    }

    @Override
    public boolean isSelfReplaceable(@NotNull Replacement replacement) {
        Block current = replacement.block();
        if (current == null) {
            return false;
        }
        if (!current.namespace().equals(getBlock().namespace())) {
            return false;
        }
        String type = current.getProperty(PROP_TYPE);
        return type != null && !TYPE_DOUBLE.equals(type);
    }

    @Override
    public @Nullable Block blockPlace(@NotNull PlacementState state) {
        Point pos = state.placePosition();
        Block existing = state.instance().getBlock(pos);

        Block slab = state.block().defaultState();
        if (!PlacementRuleUtils.hasProperty(slab, PROP_TYPE)) {
            return slab;
        }

        if (existing != null && existing.namespace().equals(slab.namespace())) {
            Block merged = tryMerge(existing, state, slab);
            if (merged != null) {
                return merged;
            }
        }

        String type = placedType(state);
        slab = slab.withProperty(PROP_TYPE, type);
        slab = PlacementRuleUtils.setWaterlogged(slab, PlacementRuleUtils.shouldWaterlog(state));
        return slab;
    }

    private static @Nullable Block tryMerge(@NotNull Block existing, @NotNull PlacementState state, @NotNull Block slab) {
        String existingType = existing.getProperty(PROP_TYPE);
        if (existingType == null || TYPE_DOUBLE.equals(existingType)) {
            return null;
        }

        BlockFace face = state.blockFace();
        boolean upperClick = PlacementRuleUtils.localY(state.cursorPosition(), state.placePosition()) > 0.5;

        boolean canMerge = switch (existingType) {
            case TYPE_BOTTOM -> face == BlockFace.TOP || (face != BlockFace.BOTTOM && upperClick);
            case TYPE_TOP -> face == BlockFace.BOTTOM || (face != BlockFace.TOP && !upperClick);
            default -> false;
        };
        if (!canMerge) {
            return null;
        }

        Block merged = slab.withProperty(PROP_TYPE, TYPE_DOUBLE);
        merged = PlacementRuleUtils.setWaterlogged(merged, false);
        return merged;
    }

    private static @NotNull String placedType(@NotNull PlacementState state) {
        BlockFace face = state.blockFace();
        if (face == BlockFace.BOTTOM) {
            return TYPE_TOP;
        }
        if (face == BlockFace.TOP) {
            return TYPE_BOTTOM;
        }
        boolean upperHalf = PlacementRuleUtils.localY(state.cursorPosition(), state.placePosition()) > 0.5;
        return upperHalf ? TYPE_TOP : TYPE_BOTTOM;
    }
}

