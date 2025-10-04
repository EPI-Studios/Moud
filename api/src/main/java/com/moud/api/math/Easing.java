package com.moud.api.math;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class Easing {
    private static final Map<String, Function<Double, Double>> functions = new HashMap<>();

    static {
        functions.put("linear", t -> t);
        functions.put("ease-in-quad", t -> t * t);
        functions.put("ease-out-quad", t -> t * (2 - t));
        functions.put("ease-in-out-quad", t -> t < 0.5 ? 2 * t * t : -1 + (4 - 2 * t) * t);
        functions.put("ease-in-cubic", t -> t * t * t);
        functions.put("ease-out-cubic", t -> (--t) * t * t + 1);
        functions.put("ease-in-out-cubic", t -> t < 0.5 ? 4 * t * t * t : (t - 1) * (2 * t - 2) * (2 * t - 2) + 1);
        functions.put("ease-out-elastic", t -> {
            double p = 0.3;
            return Math.pow(2, -10 * t) * Math.sin((t - p / 4) * (2 * Math.PI) / p) + 1;
        });
    }

    public static Function<Double, Double> get(String name) {
        return functions.getOrDefault(name.toLowerCase(), functions.get("ease-out-quad"));
    }
}