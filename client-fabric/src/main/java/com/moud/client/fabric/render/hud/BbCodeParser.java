package com.moud.client.fabric.render.hud;

import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayDeque;
import java.util.Deque;

public final class BbCodeParser {

    private BbCodeParser() {}

    public static Text parse(String bbcode) {
        if (bbcode == null || bbcode.isEmpty()) return Text.empty();

        MutableText root = Text.literal("");
        Deque<Style> styleStack = new ArrayDeque<>();
        styleStack.push(Style.EMPTY);

        int i = 0;
        StringBuilder plain = new StringBuilder();

        while (i < bbcode.length()) {
            if (bbcode.charAt(i) == '[') {
                int end = bbcode.indexOf(']', i);
                if (end == -1) {
                    plain.append(bbcode.substring(i));
                    break;
                }
                String tag = bbcode.substring(i + 1, end).trim();
                if (!plain.isEmpty()) {
                    root.append(Text.literal(plain.toString()).setStyle(styleStack.peek()));
                    plain.setLength(0);
                }
                applyTag(tag, styleStack);
                i = end + 1;
            } else {
                plain.append(bbcode.charAt(i));
                i++;
            }
        }

        if (!plain.isEmpty()) {
            root.append(Text.literal(plain.toString()).setStyle(styleStack.peek()));
        }

        return root;
    }

    private static void applyTag(String tag, Deque<Style> stack) {
        Style current = stack.peek();
        switch (tag.toLowerCase()) {
            case "b"  -> stack.push(current.withFormatting(Formatting.BOLD));
            case "i"  -> stack.push(current.withFormatting(Formatting.ITALIC));
            case "u"  -> stack.push(current.withFormatting(Formatting.UNDERLINE));
            case "s"  -> stack.push(current.withFormatting(Formatting.STRIKETHROUGH));
            case "/b", "/i", "/u", "/s", "/color" -> {
                if (stack.size() > 1) stack.pop();
            }
            case "br" -> stack.push(current);
            default -> {
                if (tag.toLowerCase().startsWith("color=")) {
                    String hex = tag.substring(6).trim().replace("#", "");
                    try {
                        int rgb = Integer.parseUnsignedInt(hex, 16);
                        stack.push(current.withColor(rgb | 0xFF000000));
                    } catch (NumberFormatException ignored) {
                        stack.push(current);
                    }
                }
            }
        }
    }
}
