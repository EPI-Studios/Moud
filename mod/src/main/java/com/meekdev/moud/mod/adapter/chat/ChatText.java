package com.meekdev.moud.mod.adapter.chat;

import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.text.RichText;
import java.net.URI;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;

public final class ChatText {

    private ChatText() {}

    public static Component of(String markup) {
        MutableComponent out = Component.empty();
        for (RichText.Piece piece : RichText.parse(markup)) {
            switch (piece) {
                case RichText.Text text -> {
                    String shown = text.style().uppercase() ? text.text().toUpperCase(Locale.ROOT) : text.text();
                    out.append(Component.literal(shown).withStyle(style(text.style())));
                }
                case RichText.Break ignored -> out.append(Component.literal("\n"));
                case RichText.Item item -> out.append(Component.literal("[" + item.id() + "]").withStyle(style(item.style())));
                case RichText.Image ignored -> {}
            }
        }
        return out;
    }

    static Style style(RichText.Style s) {
        Style style = Style.EMPTY.withBold(s.bold()).withItalic(s.italic()).withUnderlined(s.underline())
                .withStrikethrough(s.strike()).withObfuscated(s.obfuscated());
        if (s.color() != null) style = style.withColor(TextColor.fromRgb(rgb(s.color())));
        if (s.font() != null) {
            Identifier font = Identifier.tryParse(s.font());
            if (font != null) style = style.withFont(new FontDescription.Resource(font));
        }
        if (s.hover() != null) style = style.withHoverEvent(new HoverEvent.ShowText(of(s.hover())));
        if (s.click() != null) {
            ClickEvent click = switch (s.click().action()) {
                case "run" -> new ClickEvent.RunCommand(s.click().value());
                case "suggest" -> new ClickEvent.SuggestCommand(s.click().value());
                case "copy" -> new ClickEvent.CopyToClipboard(s.click().value());
                case "url" -> {
                    try {
                        yield new ClickEvent.OpenUrl(URI.create(s.click().value()));
                    } catch (IllegalArgumentException ignored) {
                        yield null;
                    }
                }
                default -> null;
            };
            if (click != null) style = style.withClickEvent(click);
        }
        return style;
    }

    public static String markup(Component component) {
        StringBuilder out = new StringBuilder();
        component.visit((style, text) -> {
            if (text.isEmpty()) return Optional.empty();
            StringBuilder open = new StringBuilder();
            StringBuilder close = new StringBuilder();
            wrap(open, close, style.isBold(), "b", "");
            wrap(open, close, style.isItalic(), "i", "");
            wrap(open, close, style.isUnderlined(), "u", "");
            wrap(open, close, style.isStrikethrough(), "s", "");
            wrap(open, close, style.isObfuscated(), "obf", "");
            if (style.getColor() != null) wrap(open, close, true, "color", "=#" + String.format("%06x", style.getColor().getValue()));
            if (style.getHoverEvent() instanceof HoverEvent.ShowText(Component hover)) {
                wrap(open, close, true, "hover", " text=\"" + attr(markup(hover)) + "\"");
            }
            ClickEvent click = style.getClickEvent();
            String action = switch (click) {
                case ClickEvent.RunCommand run -> "run=\"" + attr(run.command()) + "\"";
                case ClickEvent.SuggestCommand suggest -> "suggest=\"" + attr(suggest.command()) + "\"";
                case ClickEvent.CopyToClipboard copy -> "copy=\"" + attr(copy.value()) + "\"";
                case ClickEvent.OpenUrl url -> "url=\"" + attr(url.uri().toString()) + "\"";
                case null, default -> null;
            };
            if (action != null) wrap(open, close, true, "click", " " + action);
            out.append(open).append(RichText.escape(text)).append(close);
            return Optional.empty();
        }, Style.EMPTY);
        return out.toString();
    }

    private static void wrap(StringBuilder open, StringBuilder close, boolean on, String tag, String rest) {
        if (!on) return;
        open.append('<').append(tag).append(rest).append('>');
        close.insert(0, "</" + tag + ">");
    }

    private static String attr(String value) {
        return value.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;");
    }

    public static int rgb(Color color) {
        return (Math.round(color.r() * 255) << 16) | (Math.round(color.g() * 255) << 8) | Math.round(color.b() * 255);
    }
}
