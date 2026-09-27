package com.meekdev.moud.core.nav;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.recast4j.detour.NavMesh;
import org.recast4j.detour.QueryFilter;

final class AreaLabels {

    static final int GROUND = 1;
    static final int WATER = 2;
    static final int LAVA = 3;
    static final int JUMP = 4;
    static final int CLIMB = 5;

    private static final double MIN_AREA_COST = 0.01;

    private final Map<String, Integer> areas = new HashMap<>();
    private final String[] labels = new String[NavMesh.DT_MAX_AREAS];
    private final Runnable onNewLabel;

    AreaLabels(Runnable onNewLabel) {
        this.onNewLabel = onNewLabel;
        labels[WATER] = "water";
        labels[LAVA] = "lava";
        labels[JUMP] = "jump";
        labels[CLIMB] = "climb";
        for (int area = WATER; area <= CLIMB; area++) areas.put(labels[area], area);
    }

    String label(int area) {
        return labels[area];
    }

    int known(String label) {
        if (label == null) return GROUND;
        Integer known = areas.get(label);
        return known == null ? GROUND : known;
    }

    int register(String label) {
        if (label == null) return GROUND;
        Integer known = areas.get(label);
        if (known != null) return known;
        int next = areas.size() + WATER;
        if (next >= labels.length) return GROUND;
        areas.put(label, next);
        labels[next] = label;
        onNewLabel.run();
        return next;
    }

    QueryFilter costsFor(Agent agent) {
        float[] perArea = new float[NavMesh.DT_MAX_AREAS];
        Arrays.fill(perArea, 1);
        perArea[LAVA] = Float.POSITIVE_INFINITY;
        if (!agent.canJump()) perArea[JUMP] = Float.POSITIVE_INFINITY;
        if (!agent.canClimb()) perArea[CLIMB] = Float.POSITIVE_INFINITY;
        for (Map.Entry<String, Double> cost : agent.costs().entrySet()) {
            int area = register(cost.getKey());
            if (area == GROUND) continue;
            double value = cost.getValue();
            perArea[area] = Double.isNaN(value) ? 1 : (float) Math.max(MIN_AREA_COST, value);
        }
        return new Costs(perArea);
    }
}
