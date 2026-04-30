package com.moud.core.interp;

import java.util.concurrent.atomic.AtomicReference;

public final class InterpDefaults {

    public static final InterpPolicy FACTORY_DEFAULT = new InterpPolicy(InterpMode.SLERP, 50);

    public static final String PROP_INTERP_MODE = "@interp_mode";
    public static final String PROP_INTERP_LAG_MS = "@interp_lag_ms";

    private static final AtomicReference<InterpPolicy> CURRENT = new AtomicReference<>(FACTORY_DEFAULT);

    private InterpDefaults() {
    }

    public static InterpPolicy current() {
        return CURRENT.get();
    }

    public static void set(InterpPolicy policy) {
        if (policy == null) {
            CURRENT.set(FACTORY_DEFAULT);
        } else {
            CURRENT.set(policy);
        }
    }

    public static InterpPolicy resolve(String modeRaw, String lagRaw) {
        InterpPolicy base = current();
        InterpMode mode = InterpMode.parse(modeRaw, base.mode());
        int lag;
        if (lagRaw == null || lagRaw.isBlank()) {
            lag = base.lagMs();
        } else {
            try {
                lag = InterpPolicy.clampLag(Integer.parseInt(lagRaw.trim()));
            } catch (NumberFormatException e) {
                lag = base.lagMs();
            }
        }
        return new InterpPolicy(mode, lag);
    }
}
