package com.moud.client.fabric.scene;

import com.moud.core.util.ParseUtils;

public final class Transform3DMirror {
    private Transform3DMirror() {}

    public static boolean isTransformKey(String key) {
        return key != null && switch (key) {
            case "x", "y", "z", "rx", "ry", "rz", "sx", "sy", "sz" -> true;
            default -> false;
        };
    }

    public static void apply(long nodeId, String key, String value) {
        if (nodeId == 0L || !isTransformKey(key)) return;
        if (value == null || value.isBlank()) {
            applyClear(nodeId, key);
            return;
        }
        double v = ParseUtils.parseFloat(value, Float.NaN);
        if (!Double.isFinite(v)) return;
        Transform3DCell cell = SceneStore.getOrCreate(nodeId);
        if (cell == null) return;
        int axis = axisIndex(key);
        if (key.length() == 1) {
            cell.setPositionComponent(axis, v);
        } else if (key.charAt(0) == 'r') {
            cell.setEulerComponent(axis, v);
        } else {
            cell.setScaleComponent(axis, v);
        }
    }

    private static void applyClear(long nodeId, String key) {
        Transform3DCell cell = SceneStore.get(nodeId);
        if (cell == null) return;
        int axis = axisIndex(key);
        if (axis < 0) return;
        if (key.length() == 1) {
            cell.clearPositionComponent(axis);
        } else if (key.charAt(0) == 'r') {
            cell.clearEulerComponent(axis);
        } else {
            cell.clearScaleComponent(axis);
        }
    }

    private static int axisIndex(String key) {
        if (key.length() == 1) {
            return switch (key.charAt(0)) {
                case 'x' -> 0;
                case 'y' -> 1;
                case 'z' -> 2;
                default -> -1;
            };
        }
        if (key.length() == 2) {
            char a = key.charAt(0);
            if (a != 'r' && a != 's') return -1;
            return switch (key.charAt(1)) {
                case 'x' -> 0;
                case 'y' -> 1;
                case 'z' -> 2;
                default -> -1;
            };
        }
        return -1;
    }
}
