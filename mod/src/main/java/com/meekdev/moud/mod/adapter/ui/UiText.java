package com.meekdev.moud.mod.adapter.ui;

import com.meekdev.amnetic.client.surface.draw.UiDraw;
import com.meekdev.amnetic.client.surface.internal.UiBatcher;
import com.meekdev.moud.core.ui.GuiLayout;
import com.meekdev.moud.core.ui.TextBox;
import com.meekdev.moud.core.ui.TextLabel;
import com.meekdev.moud.mod.adapter.text.TextLayout;
import java.util.ArrayList;
import java.util.List;

final class UiText {

    record Line(int start, int end) {}

    private static final UiDraw MEASURING = new UiDraw(UiBatcher.INSTANCE, 0, 0);

    static final GuiLayout.Measure MEASURE = UiText::bounds;

    private UiText() {}

    static GuiLayout.Size bounds(TextLabel label, double wrapWidth) {
        float px = (float) label.textSize;
        String font = GuiLayout.font(label);
        if (label.richText && !(label instanceof TextBox)) {
            float width = Double.isInfinite(wrapWidth) ? 100_000f : (float) wrapWidth;
            TextLayout layout = TextLayout.of(MEASURING, label.text, width, px, font);
            return new GuiLayout.Size(layout.width(), layout.height());
        }
        UiFonts.Face face = UiFonts.of(font);
        List<Line> lines = label instanceof TextBox box && !box.multiLine
                ? List.of(new Line(0, label.text.length()))
                : lines(face, label.text, px, GuiLayout.wraps(label) ? wrapWidth : Double.POSITIVE_INFINITY);
        float widest = 0;
        for (Line line : lines) widest = Math.max(widest, width(face, label.text.substring(line.start(), line.end()), px));
        return new GuiLayout.Size(widest, lineHeight(face, px) * lines.size());
    }

    static float width(UiFonts.Face face, String text, float px) {
        return switch (face) {
            case UiFonts.Game game -> GameText.width(game.font(), text, px);
            case UiFonts.Vector vector -> {
                MEASURING.font(vector.font());
                yield MEASURING.textWidth(text, px);
            }
        };
    }

    static float lineHeight(UiFonts.Face face, float px) {
        return switch (face) {
            case UiFonts.Game ignored -> px;
            case UiFonts.Vector vector -> {
                MEASURING.font(vector.font());
                yield MEASURING.lineHeight(px);
            }
        };
    }

    static List<Line> lines(UiFonts.Face face, String text, float px, double wrapWidth) {
        List<Line> out = new ArrayList<>();
        int paragraph = 0;
        while (true) {
            int stop = text.indexOf('\n', paragraph);
            int end = stop < 0 ? text.length() : stop;
            wrap(face, text, paragraph, end, px, wrapWidth, out);
            if (stop < 0) return out;
            paragraph = stop + 1;
        }
    }

    private static void wrap(UiFonts.Face face, String text, int from, int to, float px, double wrapWidth, List<Line> out) {
        if (Double.isInfinite(wrapWidth)) {
            out.add(new Line(from, to));
            return;
        }
        int start = from;
        int word = from;
        while (word <= to) {
            int space = text.indexOf(' ', word);
            int wordEnd = space < 0 || space > to ? to : space;
            if (word > start && width(face, text.substring(start, wordEnd), px) > wrapWidth) {
                out.add(new Line(start, word - 1));
                start = word;
            }
            if (wordEnd >= to) break;
            word = wordEnd + 1;
        }
        out.add(new Line(start, to));
    }
}
