package com.meekdev.moud.mod.adapter.text;

import com.meekdev.amnetic.client.surface.draw.UiDraw;
import com.meekdev.moud.core.text.RichText;
import com.meekdev.moud.core.ui.HorizontalAlign;
import com.meekdev.moud.mod.adapter.ui.UiFonts;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GlyphSource;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public record TextLayout(List<Row> rows, float width, float height, int glyphCount) {

    public static final float LINE = 9f;

    private static final Map<Key, TextLayout> CACHE = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Key, TextLayout> eldest) {
            return size() > 256;
        }
    };
    private static final Map<String, ItemStack> STACKS = new HashMap<>();

    private record Key(String markup, float width, float px, String font) {}

    public enum Kind { TEXT, IMAGE, ITEM }

    public record Glyph(Kind kind, int codepoint, float x, float width, float height, RichText.Style style,
                        int index, int spanStart, int spanLength, Object extra) {

        boolean isBreak() {
            return kind == Kind.TEXT && codepoint == '\n';
        }

        boolean isSpace() {
            return kind == Kind.TEXT && codepoint == ' ';
        }

        Glyph at(float x) {
            return new Glyph(kind, codepoint, x, width, height, style, index, spanStart, spanLength, extra);
        }
    }

    public record Row(List<Glyph> glyphs, float width, float height) {

        public float offset(HorizontalAlign align, float space) {
            return align(align, width, space);
        }
    }

    public static float align(HorizontalAlign align, float width, float space) {
        return switch (align) {
            case LEFT -> 0;
            case CENTER -> (space - width) / 2;
            case RIGHT -> space - width;
        };
    }

    public static TextLayout of(UiDraw d, String markup, float width, float px, String font) {
        Key key = new Key(markup, width, px, font);
        TextLayout layout = CACHE.get(key);
        if (layout == null) {
            layout = wrap(d, RichText.parse(markup), width, px, font);
            CACHE.put(key, layout);
        }
        return layout;
    }

    public static TextLayout wrap(UiDraw d, List<RichText.Piece> pieces, float width, float px, String font) {
        List<Glyph> glyphs = glyphs(d, pieces, px, font);
        List<Row> rows = new ArrayList<>();
        List<Glyph> row = new ArrayList<>();
        float rowWidth = 0;
        int i = 0;
        while (i < glyphs.size()) {
            Glyph first = glyphs.get(i);
            if (first.isBreak()) {
                rows.add(row(row, rowWidth, first.height()));
                row = new ArrayList<>();
                rowWidth = 0;
                i++;
                continue;
            }
            int end = i;
            float wordWidth = 0;
            while (end < glyphs.size()) {
                Glyph g = glyphs.get(end);
                if (g.isBreak()) break;
                wordWidth += g.width();
                end++;
                if (g.kind() != Kind.TEXT || g.codepoint() == ' ') break;
            }
            if (rowWidth + wordWidth > width && !row.isEmpty()) {
                rows.add(row(row, rowWidth, 0));
                row = new ArrayList<>();
                rowWidth = 0;
            }
            for (int n = i; n < end; n++) {
                Glyph g = glyphs.get(n);
                if (rowWidth + g.width() > width && !row.isEmpty()) {
                    rows.add(row(row, rowWidth, 0));
                    row = new ArrayList<>();
                    rowWidth = 0;
                }
                if (row.isEmpty() && g.isSpace()) continue;
                row.add(g.at(rowWidth));
                rowWidth += g.width();
            }
            i = end;
        }
        if (!row.isEmpty() || rows.isEmpty()) rows.add(row(row, rowWidth, 0));
        float widest = 0;
        float height = 0;
        for (Row r : rows) {
            widest = Math.max(widest, r.width());
            height += r.height();
        }
        return new TextLayout(rows, widest, height, glyphs.size());
    }

    private static Row row(List<Glyph> glyphs, float width, float least) {
        float height = least;
        for (Glyph g : glyphs) height = Math.max(height, g.height());
        if (height == 0) height = LINE;
        return new Row(glyphs, width, height);
    }

    private static List<Glyph> glyphs(UiDraw d, List<RichText.Piece> pieces, float px, String defaultFont) {
        List<Glyph> out = new ArrayList<>();
        int index = 0;
        for (RichText.Piece piece : pieces) {
            RichText.Style style = piece.style();
            float size = (float) (px * style.size());
            switch (piece) {
                case RichText.Text text -> {
                    String shown = style.uppercase() ? text.text().toUpperCase(Locale.ROOT) : text.text();
                    UiFonts.Face face = UiFonts.of(style.font() != null ? style.font() : defaultFont);
                    int start = index;
                    int length = shown.codePointCount(0, shown.length());
                    for (int i = 0; i < shown.length(); ) {
                        int cp = shown.codePointAt(i);
                        i += Character.charCount(cp);
                        float glyphSize = size;
                        if (style.smallcaps() && Character.isLowerCase(cp)) {
                            cp = Character.toUpperCase(cp);
                            glyphSize = size * 0.8f;
                        }
                        float advance = advance(d, face, cp, glyphSize, style.bold());
                        out.add(new Glyph(Kind.TEXT, cp, 0, advance, glyphSize, style, index++, start, length, null));
                    }
                }
                case RichText.Break ignored -> out.add(new Glyph(Kind.TEXT, '\n', 0, 0, size, style, index++, index, 1, null));
                case RichText.Image image -> out.add(new Glyph(Kind.IMAGE, 0, 0, (float) (image.width() * size),
                        (float) (image.height() * size), style, index++, index, 1, image.src()));
                case RichText.Item item -> {
                    ItemStack stack = stack(item.id(), item.count());
                    if (stack.isEmpty()) continue;
                    float edge = size * 1.2f;
                    out.add(new Glyph(Kind.ITEM, 0, 0, edge, edge, style, index++, index, 1, stack));
                }
            }
        }
        return out;
    }

    private static float advance(UiDraw d, UiFonts.Face face, int codepoint, float size, boolean bold) {
        if (face instanceof UiFonts.Vector vector) {
            Identifier previous = d.currentFont();
            d.font(vector.font());
            float width = d.textWidth(new String(Character.toChars(codepoint)), size);
            if (previous != null) d.font(previous);
            return width + (bold ? size / LINE * 0.5f : 0);
        }
        return source(((UiFonts.Game) face).font()).getGlyph(codepoint).info().getAdvance(bold) * size / LINE;
    }

    static GlyphSource source(FontDescription font) {
        return Minecraft.getInstance().font.getGlyphSource(font);
    }

    private static ItemStack stack(String id, int count) {
        return STACKS.computeIfAbsent(id + "*" + count, k -> {
            Identifier item = Identifier.tryParse(id);
            if (item == null || !BuiltInRegistries.ITEM.containsKey(item)) return ItemStack.EMPTY;
            return new ItemStack(BuiltInRegistries.ITEM.getValue(item), Math.max(1, count));
        });
    }
}
