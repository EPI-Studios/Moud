package com.moud.api.math;

public class Conversion {

    public static double toDouble(Object obj) {
        if (obj == null) {
            throw new IllegalArgumentException("Cannot convert null to double.");
        }
        if (obj instanceof Number) {
            return ((Number) obj).doubleValue();
        } else if (obj instanceof String) {
            return Double.parseDouble((String) obj);
        }
        throw new ClassCastException("Cannot cast " + obj.getClass().getName() + " to a number.");
    }

    public static float toFloat(Object obj) {
        return (float) toDouble(obj);
    }

    public static long toLong(Object obj) {
        if (obj == null) {
            throw new IllegalArgumentException("Cannot convert null to long.");
        }
        if (obj instanceof Number) {
            return ((Number) obj).longValue();
        } else if (obj instanceof String) {
            return Long.parseLong((String) obj);
        }
        throw new ClassCastException("Cannot cast " + obj.getClass().getName() + " to a long.");
    }
}