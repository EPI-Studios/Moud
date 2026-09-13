package com.meekdev.moud.core.text;

import com.meekdev.moud.core.math.Color;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.Locale;
import java.util.Map;

public final class RichText {

    public record Link(String action, String value) {}

    public record Effect(String name, Map<String, String> params) {

        public double number(String key, double fallback) {
            try {
                return params.containsKey(key) ? Double.parseDouble(params.get(key)) : fallback;
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
    }

    public record Style(boolean bold, boolean italic, boolean underline, boolean strike, boolean obfuscated,
                        boolean uppercase, boolean smallcaps, Color color, Color gradientTo, double rainbow,
                        double size, String font, Color shadow, Color stroke, double strokeThickness,
                        double transparency, Color mark, List<Effect> effects, String shader, Link click,
                        String hover, int body) {

        public static final Style PLAIN = new Style(false, false, false, false, false, false, false, null, null,
                0, 1, null, null, null, 0, 0, null, List.of(), null, null, null, -1);

        Builder edit() {
            return new Builder(this);
        }
    }

    public sealed interface Piece permits Text, Break, Image, Item {
        Style style();
    }

    public record Text(String text, Style style) implements Piece {}

    public record Break(Style style) implements Piece {}

    public record Image(String src, double width, double height, Style style) implements Piece {}

    public record Item(String id, int count, Style style) implements Piece {}

    private RichText() {}

    public static List<Piece> parse(String markup) {
        List<Piece> out = new ArrayList<>();
        Deque<Open> stack = new ArrayDeque<>();
        stack.push(new Open("", Style.PLAIN));
        StringBuilder run = new StringBuilder();
        int at = 0;
        int n = markup.length();
        while (at < n) {
            char c = markup.charAt(at);
            if (c == '\\' && at + 1 < n && markup.charAt(at + 1) == '<') {
                run.append('<');
                at += 2;
                continue;
            }
            if (c == '&') {
                int semi = markup.indexOf(';', at);
                String entity = semi > at && semi - at <= 6 ? entity(markup.substring(at + 1, semi)) : null;
                if (entity != null) {
                    run.append(entity);
                    at = semi + 1;
                    continue;
                }
            }
            if (c == '<' && markup.startsWith("<!--", at)) {
                int end = markup.indexOf("-->", at + 4);
                if (end > 0) {
                    flush(out, run, stack.peek().style());
                    at = end + 3;
                    continue;
                }
            }
            if (c == '<') {
                int close = tagEnd(markup, at);
                if (close > at) {
                    Tag tag = Tag.read(markup.substring(at + 1, close));
                    if (tag != null && apply(tag, stack, out, run)) {
                        at = close + 1;
                        continue;
                    }
                }
            }
            run.append(c);
            at++;
        }
        flush(out, run, stack.peek().style());
        return out;
    }

    public static String plain(String markup) {
        StringBuilder out = new StringBuilder();
        for (Piece piece : parse(markup)) {
            switch (piece) {
                case Text text -> out.append(text.text());
                case Break ignored -> out.append('\n');
                default -> {}
            }
        }
        return out.toString();
    }

    public static String allow(String text, Set<String> tags) {
        if (tags.isEmpty()) return escape(text);
        boolean all = tags.contains("*");
        StringBuilder out = new StringBuilder(text.length());
        int at = 0;
        while (at < text.length()) {
            char c = text.charAt(at);
            if (c == '<') {
                int close = tagEnd(text, at);
                Tag tag = close > at ? Tag.read(text.substring(at + 1, close)) : null;
                if (tag != null && (all || tags.contains(alias(tag.name())) || tags.contains(tag.name()))) {
                    out.append(text, at, close + 1);
                    at = close + 1;
                    continue;
                }
                out.append("&lt;");
            } else if (c == '&') {
                out.append("&amp;");
            } else {
                out.append(c);
            }
            at++;
        }
        return out.toString();
    }

    public static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;");
    }

    public static Color color(String value) {
        if (value == null) return null;
        String v = value.trim().toLowerCase(Locale.ROOT);
        Integer named = NAMED.get(v);
        if (named != null) return rgb(named, 1);
        String digits = v.startsWith("#") ? v.substring(1) : v;
        try {
            return switch (digits.length()) {
                case 3 -> {
                    int r = Integer.parseInt(digits.substring(0, 1), 16);
                    int g = Integer.parseInt(digits.substring(1, 2), 16);
                    int b = Integer.parseInt(digits.substring(2, 3), 16);
                    yield new Color(r * 17 / 255f, g * 17 / 255f, b * 17 / 255f, 1);
                }
                case 6 -> rgb(Integer.parseInt(digits, 16), 1);
                case 8 -> rgb((int) (Long.parseLong(digits, 16) >> 8), (Long.parseLong(digits, 16) & 255) / 255f);
                default -> null;
            };
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static final Map<String, Integer> NAMED = Map.ofEntries(
            Map.entry("black", 0x000000), Map.entry("dark_blue", 0x0000AA), Map.entry("dark_green", 0x00AA00),
            Map.entry("dark_aqua", 0x00AAAA), Map.entry("dark_red", 0xAA0000), Map.entry("dark_purple", 0xAA00AA),
            Map.entry("gold", 0xFFAA00), Map.entry("gray", 0xAAAAAA), Map.entry("dark_gray", 0x555555),
            Map.entry("blue", 0x5555FF), Map.entry("green", 0x55FF55), Map.entry("aqua", 0x55FFFF),
            Map.entry("red", 0xFF5555), Map.entry("light_purple", 0xFF55FF), Map.entry("yellow", 0xFFFF55),
            Map.entry("white", 0xFFFFFF));

    private static Color rgb(int rgb, float alpha) {
        return new Color(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f, alpha);
    }

    private record Open(String name, Style style) {}

    private record Tag(String name, String value, Map<String, String> attrs, boolean closing, boolean selfClosing) {

        static Tag read(String inside) {
            String body = inside.trim();
            boolean closing = body.startsWith("/");
            if (closing) body = body.substring(1).trim();
            boolean self = body.endsWith("/");
            if (self) body = body.substring(0, body.length() - 1).trim();
            if (body.isEmpty()) return null;
            int i = 0;
            while (i < body.length() && (Character.isLetterOrDigit(body.charAt(i)) || body.charAt(i) == '_')) i++;
            if (i == 0) return null;
            String name = body.substring(0, i).toLowerCase(Locale.ROOT);
            String value = null;
            Map<String, String> attrs = new LinkedHashMap<>();
            String rest = body.substring(i).trim();
            if (rest.startsWith("=")) {
                String[] one = value(rest.substring(1).trim());
                value = one[0];
                rest = one[1].trim();
            }
            while (!rest.isEmpty()) {
                int j = 0;
                while (j < rest.length() && (Character.isLetterOrDigit(rest.charAt(j)) || rest.charAt(j) == '_')) j++;
                if (j == 0) return null;
                String key = rest.substring(0, j).toLowerCase(Locale.ROOT);
                rest = rest.substring(j).trim();
                if (!rest.startsWith("=")) {
                    attrs.put(key, "true");
                    continue;
                }
                String[] one = value(rest.substring(1).trim());
                attrs.put(key, one[0]);
                rest = one[1].trim();
            }
            return new Tag(name, value, attrs, closing, self);
        }

        private static String[] value(String from) {
            if (from.isEmpty()) return new String[] {"", ""};
            char q = from.charAt(0);
            if (q == '"' || q == '\'') {
                int end = from.indexOf(q, 1);
                if (end < 0) return new String[] {from.substring(1), ""};
                return new String[] {unescape(from.substring(1, end)), from.substring(end + 1)};
            }
            int end = 0;
            while (end < from.length() && !Character.isWhitespace(from.charAt(end))) end++;
            return new String[] {from.substring(0, end), from.substring(end)};
        }

        String get(String key) {
            String v = attrs.get(key);
            return v != null ? v : value;
        }

        double number(String key, double fallback) {
            String v = attrs.get(key);
            if (v == null && key.equals("value")) v = value;
            try {
                return v == null ? fallback : Double.parseDouble(v);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
    }

    private static int tagEnd(String markup, int open) {
        char quote = 0;
        for (int i = open + 1; i < markup.length(); i++) {
            char c = markup.charAt(i);
            if (quote != 0) {
                if (c == quote) quote = 0;
            } else if (c == '"' || c == '\'') {
                quote = c;
            } else if (c == '<') {
                return -1;
            } else if (c == '>') {
                return i;
            }
        }
        return -1;
    }

    private static boolean apply(Tag tag, Deque<Open> stack, List<Piece> out, StringBuilder run) {
        Style top = stack.peek().style();
        if (tag.closing()) {
            String name = alias(tag.name());
            boolean open = false;
            for (Open o : stack) {
                if (o.name().equals(name)) {
                    open = true;
                    break;
                }
            }
            if (!open) return false;
            flush(out, run, top);
            while (!stack.peek().name().equals(name)) stack.pop();
            stack.pop();
            return true;
        }
        switch (tag.name()) {
            case "br" -> {
                flush(out, run, top);
                out.add(new Break(top));
                return true;
            }
            case "img" -> {
                String src = tag.get("src");
                if (src == null) return false;
                flush(out, run, top);
                double h = tag.number("height", tag.number("size", 1));
                out.add(new Image(src, tag.number("width", h), h, top));
                return true;
            }
            case "item" -> {
                String id = tag.get("id");
                if (id == null) return false;
                flush(out, run, top);
                out.add(new Item(id, (int) tag.number("count", 1), top));
                return true;
            }
            default -> {}
        }
        Style next = open(tag, top);
        if (next == null) return false;
        flush(out, run, top);
        if (!tag.selfClosing()) stack.push(new Open(alias(tag.name()), next));
        return true;
    }

    private static String alias(String name) {
        return switch (name) {
            case "uppercase" -> "uc";
            case "smallcaps" -> "sc";
            case "obfuscated" -> "obf";
            case "bold" -> "b";
            case "italic" -> "i";
            case "underline" -> "u";
            case "strike", "strikethrough" -> "s";
            case "colour" -> "color";
            default -> name;
        };
    }

    private static Style open(Tag tag, Style top) {
        Builder b = top.edit();
        switch (alias(tag.name())) {
            case "b" -> b.bold = true;
            case "i" -> b.italic = true;
            case "u" -> b.underline = true;
            case "s" -> b.strike = true;
            case "obf" -> b.obfuscated = true;
            case "uc" -> b.uppercase = true;
            case "sc" -> b.smallcaps = true;
            case "color" -> {
                Color c = color(tag.get("value"));
                if (c == null) return null;
                b.color = c;
                b.gradientTo = null;
                b.rainbow = 0;
            }
            case "font" -> {
                if (tag.attrs().isEmpty() && tag.value() == null) return null;
                Color c = color(tag.attrs().get("color"));
                if (c != null) b.color = c;
                if (tag.attrs().containsKey("size")) b.size = top.size() * tag.number("size", 1);
                String face = tag.attrs().getOrDefault("face", tag.value());
                if (face != null) b.font = face;
                if (tag.attrs().containsKey("transparency")) b.transparency = tag.number("transparency", 0);
            }
            case "size" -> b.size = top.size() * tag.number("value", 1);
            case "alpha" -> b.transparency = 1 - tag.number("value", 1);
            case "transparency" -> b.transparency = tag.number("value", 0);
            case "stroke" -> {
                Color c = color(tag.get("color"));
                b.stroke = c == null ? new Color(0, 0, 0, 1) : c;
                b.strokeThickness = tag.attrs().containsKey("thickness") ? tag.number("thickness", 1)
                        : tag.number("th", 1);
            }
            case "mark" -> {
                Color c = color(tag.get("color"));
                if (c == null) c = new Color(1, 1, 0, 0.4f);
                if (tag.attrs().containsKey("transparency")) {
                    c = new Color(c.r(), c.g(), c.b(), (float) (1 - tag.number("transparency", 0)));
                }
                b.mark = c;
            }
            case "shadow" -> {
                Color c = color(tag.get("color"));
                b.shadow = c == null ? new Color(0.25f, 0.25f, 0.25f, 1) : c;
            }
            case "noshadow" -> b.shadow = new Color(0, 0, 0, 0);
            case "gradient" -> {
                String from = tag.attrs().get("from");
                String to = tag.attrs().get("to");
                if (from == null && tag.value() != null && tag.value().contains(",")) {
                    String[] pair = tag.value().split(",", 2);
                    from = pair[0];
                    to = pair[1];
                }
                Color a = color(from);
                Color z = color(to);
                if (a == null || z == null) return null;
                b.color = a;
                b.gradientTo = z;
                b.rainbow = 0;
            }
            case "rainbow" -> b.rainbow = Math.max(0.0001, tag.number("speed", tag.number("value", 1)));
            case "wave", "shake", "pulse", "bounce", "fade", "spin" -> {
                List<Effect> effects = new ArrayList<>(top.effects());
                Map<String, String> params = new LinkedHashMap<>(tag.attrs());
                if (tag.value() != null) params.put("strength", tag.value());
                effects.add(new Effect(tag.name(), params));
                b.effects = List.copyOf(effects);
            }
            case "shader" -> {
                String name = tag.get("name");
                if (name == null) return null;
                b.shader = name;
            }
            case "click" -> {
                Link link = null;
                for (String action : List.of("run", "suggest", "url", "copy", "callback")) {
                    if (tag.attrs().containsKey(action)) link = new Link(action, tag.attrs().get(action));
                }
                if (link == null) return null;
                b.click = link;
            }
            case "hover" -> {
                String text = tag.get("text");
                if (text == null) return null;
                b.hover = text;
            }
            case "body" -> {
                int id = (int) tag.number("id", tag.number("value", -1));
                if (id < 0) return null;
                b.body = id;
            }
            default -> {
                return null;
            }
        }
        return b.build();
    }

    private static String entity(String name) {
        return switch (name) {
            case "lt" -> "<";
            case "gt" -> ">";
            case "amp" -> "&";
            case "quot" -> "\"";
            case "apos" -> "'";
            default -> null;
        };
    }

    private static String unescape(String value) {
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < value.length()) {
            char c = value.charAt(i);
            int semi = c == '&' ? value.indexOf(';', i) : -1;
            String e = semi > i && semi - i <= 6 ? entity(value.substring(i + 1, semi)) : null;
            if (e != null) {
                out.append(e);
                i = semi + 1;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    private static void flush(List<Piece> out, StringBuilder run, Style style) {
        if (run.isEmpty()) return;
        out.add(new Text(run.toString(), style));
        run.setLength(0);
    }

    static final class Builder {
        boolean bold, italic, underline, strike, obfuscated, uppercase, smallcaps;
        Color color, gradientTo, shadow, stroke, mark;
        double rainbow, size, strokeThickness, transparency;
        String font, shader, hover;
        List<Effect> effects;
        Link click;
        int body;

        Builder(Style s) {
            bold = s.bold(); italic = s.italic(); underline = s.underline(); strike = s.strike();
            obfuscated = s.obfuscated(); uppercase = s.uppercase(); smallcaps = s.smallcaps();
            color = s.color(); gradientTo = s.gradientTo(); rainbow = s.rainbow(); size = s.size();
            font = s.font(); shadow = s.shadow(); stroke = s.stroke(); strokeThickness = s.strokeThickness();
            transparency = s.transparency(); mark = s.mark(); effects = s.effects(); shader = s.shader();
            click = s.click(); hover = s.hover(); body = s.body();
        }

        Style build() {
            return new Style(bold, italic, underline, strike, obfuscated, uppercase, smallcaps, color, gradientTo,
                    rainbow, size, font, shadow, stroke, strokeThickness, transparency, mark, effects, shader,
                    click, hover, body);
        }
    }
}
