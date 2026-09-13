package com.meekdev.moud.mod.adapter.chat;

import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.text.Markup;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

// markup as the game's own chat text
public final class ChatText {

    private ChatText() {}

    public static Component of(String markup) {
        MutableComponent out = Component.empty();
        for (Markup.Span span : Markup.parse(markup)) {
            Style style = Style.EMPTY.withBold(span.bold()).withItalic(span.italic())
                    .withUnderlined(span.underline()).withStrikethrough(span.strike());
            Color color = span.color();
            if (color != null) style = style.withColor(TextColor.fromRgb(rgb(color)));
            out.append(Component.literal(span.text()).withStyle(style));
        }
        return out;
    }

    public static int rgb(Color color) {
        return (Math.round(color.r() * 255) << 16) | (Math.round(color.g() * 255) << 8) | Math.round(color.b() * 255);
    }
}
