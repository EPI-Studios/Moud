package com.moud.server.blocks.placement;

import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.rule.BlockPlacementRule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class Rotation16PlacementRule extends BlockPlacementRule {
    private static final String PROP_ROTATION = "rotation";

    Rotation16PlacementRule(@NotNull Block block) {
        super(block);
    }

    @Override
    public @Nullable Block blockPlace(@NotNull PlacementState state) {
        Block base = state.block().defaultState();
        if (!PlacementRuleUtils.hasProperty(base, PROP_ROTATION)) {
            return base;
        }
        int segment = rotationSegment(state.playerPosition().yaw());
        base = base.withProperty(PROP_ROTATION, Integer.toString(segment));
        base = PlacementRuleUtils.setWaterlogged(base, PlacementRuleUtils.shouldWaterlog(state));
        return base;
    }

    private static int rotationSegment(float yawDegrees) {
        double rotation = yawDegrees + 180.0;
        int raw = (int) Math.floor(rotation * 16.0 / 360.0 + 0.5);
        return raw & 15;
    }
}

