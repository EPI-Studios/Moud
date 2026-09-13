package com.meekdev.moud.core.text;

import com.meekdev.moud.core.math.Color;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

// the little markup a place styles chat with: <b>, <i>, <u>, <s>, <color=#ff8800> and their closing
// tags. a tag that is not one of these is text, so a player typing a < is not eaten, and \< is always
// a plain <
public final class Markup {

    public record Span(String text, boolean bold, boolean italic, boolean underline, boolean strike,
                       Color color) {}

    private record Style(boolean bold, boolean italic, boolean underline, boolean strike, Color color, String tag) {}

    private Markup() {}

    public static List<Span> parse(String text) {
        List<Span> out = new ArrayList<>();
        Deque<Style> stack = new ArrayDeque<>();
        stack.push(new Style(false, false, false, false, null, ""));
        StringBuilder run = new StringBuilder();
        int at = 0;
        while (at < text.length()) {
            char c = text.charAt(at);
            if (c == '\\' && at + 1 < text.length() && text.charAt(at + 1) == '<') {
                run.append('<');
                at += 2;
                continue;
            }
            int close = c == '<' ? text.indexOf('>', at) : -1;
            if (close > at) {
                String tag = text.substring(at + 1, close).trim().toLowerCase();
                Style top = stack.peek();
                Style next = open(top, tag);
                boolean closing = tag.startsWith("/") && closes(stack, tag.substring(1));
                if (next != null || closing) {
                    flush(out, run, top);
                    if (next != null) {
                        stack.push(next);
                    } else {
                        stack.pop();
                    }
                    at = close + 1;
                    continue;
                }
            }
            run.append(c);
            at++;
        }
        flush(out, run, stack.peek());
        return out;
    }

    // text that shows exactly as written, whatever tags are in it
    public static String escape(String text) {
        return text.replace("<", "\\<");
    }

    // everything but the tags, for a log or a filter that should not see markup
    public static String plain(String text) {
        StringBuilder out = new StringBuilder();
        for (Span span : parse(text)) out.append(span.text());
        return out.toString();
    }

    private static Style open(Style top, String tag) {
        return switch (tag) {
            case "b" -> new Style(true, top.italic(), top.underline(), top.strike(), top.color(), "b");
            case "i" -> new Style(top.bold(), true, top.underline(), top.strike(), top.color(), "i");
            case "u" -> new Style(top.bold(), top.italic(), true, top.strike(), top.color(), "u");
            case "s" -> new Style(top.bold(), top.italic(), top.underline(), true, top.color(), "s");
            default -> {
                if (!tag.startsWith("color=")) yield null;
                Color color = hex(tag.substring(6).trim());
                yield color == null ? null
                        : new Style(top.bold(), top.italic(), top.underline(), top.strike(), color, "color");
            }
        };
    }

    // only the innermost open tag closes, so <b><i>x</b> leaves the </b> as text
    private static boolean closes(Deque<Style> stack, String tag) {
        return stack.size() > 1 && stack.peek().tag().equals(tag);
    }

    private static Color hex(String value) {
        String digits = value.startsWith("#") ? value.substring(1) : value;
        if (digits.length() != 6) return null;
        try {
            int rgb = Integer.parseInt(digits, 16);
            return new Color(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f, 1);
        } catch (NumberFormatException notHex) {
            return null;
        }
    }

    private static void flush(List<Span> out, StringBuilder run, Style style) {
        if (run.isEmpty()) return;
        out.add(new Span(run.toString(), style.bold(), style.italic(), style.underline(), style.strike(), style.color()));
        run.setLength(0);
    }
}
