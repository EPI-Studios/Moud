package com.moud.client.fabric.render.scene.subrender.particle;

import java.util.ArrayList;
import java.util.List;

public final class ParticleCurve {
    private static final ParticleCurve FLAT_ONE = new ParticleCurve(new float[]{0f, 1f}, new float[]{1f, 1f});

    private final float[] times;
    private final float[] values;

    private ParticleCurve(float[] times, float[] values) {
        this.times = times;
        this.values = values;
    }

    public static ParticleCurve flat() {
        return FLAT_ONE;
    }

    public static ParticleCurve parse(String spec, float fallback) {
        if (spec == null || spec.isBlank()) {
            return new ParticleCurve(new float[]{0f, 1f}, new float[]{fallback, fallback});
        }
        List<float[]> keys = new ArrayList<>();
        for (String segment : spec.split("\\|")) {
            String s = segment.trim();
            if (s.isEmpty()) continue;
            int colon = s.indexOf(':');
            if (colon <= 0 || colon == s.length() - 1) continue;
            try {
                float t = Float.parseFloat(s.substring(0, colon).trim());
                float v = Float.parseFloat(s.substring(colon + 1).trim());
                if (!Float.isFinite(t) || !Float.isFinite(v)) continue;
                keys.add(new float[]{Math.max(0f, Math.min(1f, t)), v});
            } catch (NumberFormatException ignored) {
            }
        }
        if (keys.isEmpty()) {
            return new ParticleCurve(new float[]{0f, 1f}, new float[]{fallback, fallback});
        }
        keys.sort((a, b) -> Float.compare(a[0], b[0]));
        float[] t = new float[keys.size()];
        float[] v = new float[keys.size()];
        for (int i = 0; i < keys.size(); i++) {
            t[i] = keys.get(i)[0];
            v[i] = keys.get(i)[1];
        }
        return new ParticleCurve(t, v);
    }

    public float sample(float t) {
        int n = times.length;
        if (n == 0) return 0f;
        if (t <= times[0]) return values[0];
        if (t >= times[n - 1]) return values[n - 1];
        for (int i = 1; i < n; i++) {
            if (t <= times[i]) {
                float span = times[i] - times[i - 1];
                float local = span > 1.0e-6f ? (t - times[i - 1]) / span : 0f;
                return values[i - 1] + (values[i] - values[i - 1]) * local;
            }
        }
        return values[n - 1];
    }
}
