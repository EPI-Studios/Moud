package com.meekdev.moud.core.tween;

import com.meekdev.moud.core.clazz.Enums;
import com.meekdev.moud.core.clazz.PropertyType;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.math.UDim2;
import com.meekdev.moud.core.math.Vector3;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class Keys {

    public record Shape(Interpolation interpolation, Easing easing, Easing.Direction direction) {

        public static final Shape NONE = new Shape(null, null, null);

        public static Shape parse(String word) {
            String spelled = word.trim().replace(' ', '-');
            if (spelled.isEmpty()) return NONE;
            for (Interpolation mode : Interpolation.values()) {
                if (Enums.name(mode).equals(spelled)) return new Shape(mode, null, null);
            }
            int dash = spelled.indexOf('-');
            Easing easing = (Easing) Enums.parse(Easing.class, dash < 0 ? spelled : spelled.substring(0, dash));
            Easing.Direction direction = dash < 0 ? Easing.Direction.IN_OUT
                    : (Easing.Direction) Enums.parse(Easing.Direction.class, spelled.substring(dash + 1));
            return new Shape(null, easing, direction);
        }

        public String write() {
            if (interpolation != null) return Enums.name(interpolation);
            if (easing != null) return Enums.name(easing) + "-" + Enums.name(direction);
            return "";
        }
    }

    public record Key(double time, double[] value, Shape shape) {}

    private Keys() {}

    public static boolean animatable(PropertyType type) {
        return switch (type) {
            case BOOL, INT, NUM, VEC3, QUAT, CFRAME, COLOR, UDIM2 -> true;
            case STRING, ASSET, ENUM, REF -> false;
        };
    }

    public static double[] components(PropertyType type, Object value) {
        return switch (value) {
            case Boolean b when type == PropertyType.BOOL -> new double[] {b ? 1 : 0};
            case Number n when type == PropertyType.NUM || type == PropertyType.INT -> new double[] {n.doubleValue()};
            case Vector3 v when type == PropertyType.VEC3 -> new double[] {v.x(), v.y(), v.z()};
            case Quat q when type == PropertyType.QUAT -> new double[] {q.x(), q.y(), q.z(), q.w()};
            case CFrame c when type == PropertyType.CFRAME -> {
                Vector3 p = c.position();
                Quat q = c.rotation();
                yield new double[] {p.x(), p.y(), p.z(), q.x(), q.y(), q.z(), q.w()};
            }
            case Color c when type == PropertyType.COLOR -> new double[] {c.r(), c.g(), c.b(), c.a()};
            case UDim2 u when type == PropertyType.UDIM2 -> new double[] {u.xScale(), u.xOffset(), u.yScale(), u.yOffset()};
            case null, default -> throw new IllegalArgumentException("expects " + expected(type));
        };
    }

    public static Object value(PropertyType type, double[] c) {
        return switch (type) {
            case BOOL -> c[0] >= 0.5;
            case INT -> (double) Math.round(c[0]);
            case NUM -> c[0];
            case VEC3 -> new Vector3(c[0], c[1], c[2]);
            case QUAT -> new Quat(c[0], c[1], c[2], c[3]).normalize();
            case CFRAME -> new CFrame(new Vector3(c[0], c[1], c[2]), new Quat(c[3], c[4], c[5], c[6]).normalize());
            case COLOR -> new Color((float) c[0], (float) c[1], (float) c[2], (float) c[3]);
            case UDIM2 -> new UDim2(c[0], c[1], c[2], c[3]);
            default -> throw new IllegalArgumentException("cannot animate a " + Enums.name(type));
        };
    }

    public static String expected(PropertyType type) {
        return switch (type) {
            case BOOL -> "a boolean";
            case INT, NUM -> "a number";
            case VEC3 -> "a vec3";
            case QUAT -> "a quat";
            case CFRAME -> "a cframe";
            case COLOR -> "a color";
            case UDIM2 -> "a udim2";
            default -> "something that cannot be animated";
        };
    }

    public static List<Key> parse(String text, PropertyType type) {
        List<Key> out = new ArrayList<>();
        if (text == null || text.isBlank() || !animatable(type)) return out;
        for (String entry : text.split(",")) {
            String[] words = entry.trim().split("\\s+");
            if (words.length < 2) continue;
            try {
                double time = Double.parseDouble(words[0]);
                List<Double> numbers = new ArrayList<>();
                int at = 1;
                for (; at < words.length; at++) {
                    if (type == PropertyType.BOOL && (words[at].equals("true") || words[at].equals("false"))) {
                        numbers.add(words[at].equals("true") ? 1.0 : 0.0);
                    } else if (!words[at].isEmpty() && (Character.isDigit(words[at].charAt(0)) || "-+.".indexOf(words[at].charAt(0)) >= 0)) {
                        numbers.add(Double.parseDouble(words[at]));
                    } else {
                        break;
                    }
                }
                double[] value = widen(type, numbers);
                if (value == null) continue;
                Shape shape = at < words.length ? Shape.parse(words[at]) : Shape.NONE;
                out.add(new Key(Math.max(0, time), value, shape));
            } catch (IllegalArgumentException ignored) {
            }
        }
        out.sort(Comparator.comparingDouble(Key::time));
        return out;
    }

    private static double[] widen(PropertyType type, List<Double> numbers) {
        int n = numbers.size();
        double[] c = new double[n];
        for (int i = 0; i < n; i++) c[i] = numbers.get(i);
        return switch (type) {
            case BOOL, INT, NUM -> n == 1 ? c : null;
            case VEC3 -> n == 3 ? c : null;
            case QUAT, UDIM2 -> n == 4 ? c : null;
            case COLOR -> n == 4 ? c : n == 3 ? new double[] {c[0], c[1], c[2], 1} : null;
            case CFRAME -> n == 7 ? c : n == 3 ? new double[] {c[0], c[1], c[2], 0, 0, 0, 1} : null;
            default -> null;
        };
    }

    public static String write(List<Key> keys, PropertyType type) {
        List<String> entries = new ArrayList<>();
        for (Key key : keys) {
            StringBuilder entry = new StringBuilder(number(key.time()));
            double[] value = key.value();
            int count = type == PropertyType.COLOR && value[3] == 1 ? 3 : value.length;
            for (int i = 0; i < count; i++) {
                entry.append(' ').append(type == PropertyType.BOOL ? String.valueOf(value[i] >= 0.5) : number(value[i]));
            }
            String shape = key.shape().write();
            if (!shape.isEmpty()) entry.append(' ').append(shape);
            entries.add(entry.toString());
        }
        return String.join(", ", entries);
    }

    public static List<Key> with(List<Key> keys, Key key) {
        List<Key> out = new ArrayList<>();
        for (Key existing : keys) {
            if (Math.abs(existing.time() - key.time()) > 1e-6) out.add(existing);
        }
        out.add(key);
        out.sort(Comparator.comparingDouble(Key::time));
        return out;
    }

    public static int index(List<Key> keys, double time) {
        int at = -1;
        for (int n = 0; n < keys.size() && keys.get(n).time() <= time + 1e-9; n++) at = n;
        return at;
    }

    public record Defaults(Interpolation interpolation, Easing easing, Easing.Direction direction) {}

    public static double[] sample(List<Key> keys, double time, double length, boolean wrap, Defaults defaults, int rotation) {
        int n = keys.size();
        if (n == 1) return keys.getFirst().value();
        int at = index(keys, time);
        wrap = wrap && length > 0;
        if (!wrap && at < 0) return keys.getFirst().value();
        if (!wrap && at == n - 1) return keys.getLast().value();
        Key from = key(keys, at);
        Key to = key(keys, at + 1);
        double fromT = time(keys, at, length);
        double toT = time(keys, at + 1, length);
        double span = toT - fromT;
        double alpha = span <= 1e-9 ? 1 : Math.clamp((time - fromT) / span, 0, 1);
        Shape shape = from.shape();
        Interpolation mode = shape.interpolation() != null ? shape.interpolation()
                : shape.easing() != null ? Interpolation.LINEAR : defaults.interpolation();
        if (mode == Interpolation.CONSTANT) return alpha >= 1 ? to.value() : from.value();
        if (mode == Interpolation.LINEAR) {
            Easing easing = shape.easing() != null ? shape.easing() : defaults.easing();
            Easing.Direction direction = shape.easing() != null ? shape.direction() : defaults.direction();
            return lerp(from.value(), to.value(), easing.apply(alpha, direction), rotation);
        }
        int pre = wrap ? at - 1 : Math.max(0, at - 1);
        int post = wrap ? at + 2 : Math.min(n - 1, at + 2);
        double preT = time(keys, pre, length) - fromT;
        double postT = time(keys, post, length) - fromT;
        double[] a = key(keys, pre).value();
        double[] b = from.value();
        double[] c = to.value();
        double[] d = key(keys, post).value();
        double[] out = new double[b.length];
        for (int i = 0; i < out.length; i++) out[i] = cubic(b[i], c[i], a[i], d[i], alpha, span, preT, postT);
        if (rotation >= 0) slerp(out, b, c, alpha, rotation);
        return out;
    }

    private static Key key(List<Key> keys, int at) {
        return keys.get(Math.floorMod(at, keys.size()));
    }

    private static double time(List<Key> keys, int at, double length) {
        return key(keys, at).time() + Math.floorDiv(at, keys.size()) * length;
    }

    private static double[] lerp(double[] a, double[] b, double t, int rotation) {
        double[] out = new double[a.length];
        for (int i = 0; i < a.length; i++) out[i] = a[i] + (b[i] - a[i]) * t;
        if (rotation >= 0) slerp(out, a, b, t, rotation);
        return out;
    }

    private static void slerp(double[] out, double[] a, double[] b, double t, int at) {
        Quat q = new Quat(a[at], a[at + 1], a[at + 2], a[at + 3]).slerp(new Quat(b[at], b[at + 1], b[at + 2], b[at + 3]), t).normalize();
        out[at] = q.x();
        out[at + 1] = q.y();
        out[at + 2] = q.z();
        out[at + 3] = q.w();
    }

    public static int rotation(PropertyType type) {
        return switch (type) {
            case QUAT -> 0;
            case CFRAME -> 3;
            default -> -1;
        };
    }

    static double cubic(double from, double to, double pre, double post, double weight, double toT, double preT, double postT) {
        double t = toT * weight;
        double a1 = mix(pre, from, preT == 0 ? 0 : (t - preT) / -preT);
        double a2 = mix(from, to, toT == 0 ? 0.5 : t / toT);
        double a3 = mix(to, post, postT - toT == 0 ? 1 : (t - toT) / (postT - toT));
        double b1 = mix(a1, a2, toT - preT == 0 ? 0 : (t - preT) / (toT - preT));
        double b2 = mix(a2, a3, postT == 0 ? 1 : t / postT);
        return mix(b1, b2, toT == 0 ? 0.5 : t / toT);
    }

    private static double mix(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static String number(double value) {
        String text = String.format(Locale.ROOT, "%.4f", value);
        text = text.contains(".") ? text.replaceAll("0+$", "") : text;
        text = text.endsWith(".") ? text.substring(0, text.length() - 1) : text;
        return text.equals("-0") ? "0" : text;
    }
}
