package com.meekdev.moud.core.ui;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Vector3;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class UIGradient extends UIComponent {

    public record Keypoint(double time, Color color, double transparency) {}

    public boolean enabled = true;

    public Color startColor = Color.WHITE;
    public Color endColor = Color.WHITE;

    @Prop(min = 0, max = 1) public double startTransparency;
    @Prop(min = 0, max = 1) public double endTransparency;

    public String keypoints = "";

    public double rotation;

    public Vector3 offset = Vector3.ZERO;

    public List<Keypoint> stops() {
        List<Keypoint> parsed = parse(keypoints);
        if (parsed.size() >= 2) return parsed;
        return List.of(new Keypoint(0, startColor, startTransparency), new Keypoint(1, endColor, endTransparency));
    }

    public static List<Keypoint> parse(String text) {
        List<Keypoint> out = new ArrayList<>();
        if (text == null || text.isBlank()) return out;
        for (String entry : text.split(",")) {
            String[] parts = entry.trim().split("\\s+");
            if (parts.length < 4) continue;
            try {
                double time = Math.clamp(Double.parseDouble(parts[0]), 0, 1);
                Color color = new Color(Float.parseFloat(parts[1]), Float.parseFloat(parts[2]), Float.parseFloat(parts[3]), 1f);
                double transparency = parts.length > 4 ? Math.clamp(Double.parseDouble(parts[4]), 0, 1) : 0;
                out.add(new Keypoint(time, color, transparency));
            } catch (NumberFormatException ignored) {
                return List.of();
            }
        }
        out.sort(Comparator.comparingDouble(Keypoint::time));
        return out;
    }

    public static String write(List<Keypoint> stops) {
        List<String> parts = new ArrayList<>();
        for (Keypoint stop : stops) {
            parts.add(String.format(Locale.ROOT, "%s %s %s %s %s", number(stop.time()), number(stop.color().r()),
                    number(stop.color().g()), number(stop.color().b()), number(stop.transparency())));
        }
        return String.join(", ", parts);
    }

    private static String number(double value) {
        String text = String.format(Locale.ROOT, "%.4f", value);
        text = text.contains(".") ? text.replaceAll("0+$", "") : text;
        return text.endsWith(".") ? text.substring(0, text.length() - 1) : text;
    }

    public static Keypoint sample(List<Keypoint> stops, double time) {
        if (time <= stops.getFirst().time()) return stops.getFirst();
        for (int n = 1; n < stops.size(); n++) {
            Keypoint to = stops.get(n);
            if (time > to.time()) continue;
            Keypoint from = stops.get(n - 1);
            double span = to.time() - from.time();
            double t = span <= 0 ? 1 : (time - from.time()) / span;
            return new Keypoint(time, from.color().lerp(to.color(), (float) t),
                    from.transparency() + (to.transparency() - from.transparency()) * t);
        }
        return stops.getLast();
    }
}
