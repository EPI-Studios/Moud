package com.meekdev.moud.mod.adapter.chat;

import com.meekdev.amnetic.client.framebuffer.Framebuffer;
import com.meekdev.amnetic.client.framebuffer.Framebuffers;
import com.meekdev.amnetic.client.render.GlState;
import com.meekdev.amnetic.client.render.ShaderProgram;
import com.meekdev.amnetic.client.surface.HudSurface;
import com.meekdev.amnetic.client.surface.Surfaces;
import com.meekdev.amnetic.client.surface.draw.UiDraw;
import com.meekdev.moud.core.clazz.Enums;
import com.meekdev.moud.core.instance.ChatAnimation;
import com.meekdev.moud.core.instance.ChatBackdrop;
import com.meekdev.moud.core.instance.ChatTabs;
import com.meekdev.moud.core.instance.ChatWindow;
import com.meekdev.moud.core.instance.HorizontalAlign;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.TextChannel;
import com.meekdev.moud.core.instance.VerticalAlign;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.text.RichText;
import com.meekdev.moud.core.tween.Easing;
import com.meekdev.moud.mod.adapter.ui.UiFonts;
import com.meekdev.moud.mod.adapter.ui.UiImages;
import com.meekdev.moud.mod.client.ClientPlace;
import com.meekdev.moud.mod.client.ClientScene;
import com.meekdev.moud.script.engine.ScriptEngine;
import com.mojang.blaze3d.opengl.GlTexture;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GlyphSource;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.gui.font.glyphs.BakedSheetGlyph;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public final class ChatView {

    private static final float LINE = 9f;

    private enum Kind { TEXT, IMAGE, ITEM }

    private record Glyph(Kind kind, int codepoint, float x, float width, float height, RichText.Style style,
                         int index, int spanStart, int spanLength, Object extra) {}

    private record Row(List<Glyph> glyphs, float width, float height) {}

    private record Laid(List<Row> rows, float width, float height, int glyphs) {}

    private record Hit(float x0, float y0, float x1, float y1, RichText.Style style, ClientChat.Shown shown,
                       ItemStack item, TextChannel tab) {}

    private record ItemDraw(ItemStack stack, float x, float y, float size) {}

    private record Rect(float x, float y, float w, float h) {}

    public record Look(Color textColor, String font, boolean shadow, Color strokeColor, double strokeAlpha) {

        static Look of(ChatWindow window, Map<String, Object> look) {
            return new Look(color(look, "textColor", window.textColor), string(look, "font", window.font),
                    bool(look, "textShadow", window.textShadow), color(look, "textStrokeColor", window.textStrokeColor),
                    1 - number(look, "textStrokeTransparency", window.textStrokeTransparency));
        }
    }

    private static HudSurface hud;
    private static final List<Hit> HITS = new ArrayList<>();
    private static final List<ItemDraw> ITEMS = new ArrayList<>();
    private static final Map<String, ItemStack> STACKS = new HashMap<>();
    private static final long START = System.nanoTime();

    private static float scroll;
    private static float maxScroll;
    private static double hidden;
    private static long lastFrame = System.nanoTime();
    private static Framebuffer capture;

    private ChatView() {}

    public static void install() {
        hud = Surfaces.hud();
        hud.onDraw(ChatView::draw);
    }

    public static boolean active() {
        return ClientChat.active();
    }

    private static boolean focused() {
        return Minecraft.getInstance().screen instanceof ChatScreen;
    }

    private static void draw(UiDraw d) {
        HITS.clear();
        ITEMS.clear();
        if (!active()) return;
        ChatWindow window = ChatLook.windowOrDefault();
        long now = System.nanoTime();
        double dt = Math.min(0.25, (now - lastFrame) / 1e9);
        lastFrame = now;
        double speed = window.animationTime <= 0 ? 1e9 : 1 / window.animationTime;
        hidden = window.visible ? Math.max(0, hidden - dt * speed) : Math.min(1, hidden + dt * speed);
        if (!window.enabled || hidden >= 1 && window.hideAnimation != ChatAnimation.NONE || !window.visible && window.hideAnimation == ChatAnimation.NONE) return;

        float time = (float) ((now - START) / 1e9);
        boolean focused = focused();
        if (!focused) scroll = 0;
        Rect rect = rect(d, window);

        double shownAmount = 1 - window.easing.apply(hidden, Easing.Direction.IN_OUT);
        float windowAlpha = 1;
        float dx = 0;
        float dy = 0;
        switch (window.hideAnimation) {
            case FADE, POP, TYPEWRITER -> windowAlpha = (float) shownAmount;
            case SLIDE_LEFT -> dx = (float) (-(1 - shownAmount) * (rect.x() + rect.w()));
            case SLIDE_RIGHT -> dx = (float) ((1 - shownAmount) * (d.width() - rect.x()));
            case SLIDE_UP -> dy = (float) (-(1 - shownAmount) * (rect.y() + rect.h()));
            case SLIDE_DOWN -> dy = (float) ((1 - shownAmount) * (d.height() - rect.y()));
            default -> {}
        }
        rect = new Rect(rect.x() + dx, rect.y() + dy, rect.w(), rect.h());
        if (window.hideAnimation == ChatAnimation.POP) {
            float s = (float) (0.6 + 0.4 * shownAmount);
            d.pushTransform(rect.x() + rect.w() / 2, rect.y() + rect.h() / 2, 0, 0, s, 0);
        }

        boolean backdrop = window.backdrop == ChatBackdrop.ALWAYS || window.backdrop == ChatBackdrop.FOCUSED && focused;
        if (backdrop) {
            d.roundedRect(rect.x(), rect.y(), rect.w(), rect.h(), (float) window.cornerRadius,
                    argb(window.backgroundColor, (1 - window.backgroundTransparency) * windowAlpha));
        }
        if (focused) tabs(d, window, rect, windowAlpha);

        d.pushClip(rect.x(), rect.y(), rect.w(), rect.h());
        lines(d, window, rect, windowAlpha, time, focused);
        d.popClip();

        if (window.hideAnimation == ChatAnimation.POP) d.popTransform();
        windowShader(d, window, rect, time);
    }

    private static Rect rect(UiDraw d, ChatWindow window) {
        float w = (float) (window.size.xScale() * d.width() + window.size.xOffset());
        float h = (float) (window.size.yScale() * d.height() + window.size.yOffset());
        float x = (float) (window.position.xScale() * d.width() + window.position.xOffset() - window.anchorX * w);
        float y = (float) (window.position.yScale() * d.height() + window.position.yOffset() - window.anchorY * h);
        return new Rect(x, y, Math.max(1, w), Math.max(1, h));
    }

    private static void lines(UiDraw d, ChatWindow window, Rect rect, float windowAlpha, float time, boolean focused) {
        List<ClientChat.Shown> lines = ClientChat.INSTANCE.lines();
        float padding = (float) window.padding;
        float contentWidth = rect.w() - padding * 2;
        long now = System.nanoTime();

        record Placed(ClientChat.Shown shown, Laid laid, float alpha, double enter, double exit, float height) {}
        List<Placed> placed = new ArrayList<>();
        float total = 0;
        for (int n = lines.size() - 1; n >= 0; n--) {
            ClientChat.Shown shown = lines.get(n);
            double age = (now - shown.added) / 1e9;
            float alpha = 1;
            double visibleTime = number(shown.look, "visibleTime", window.visibleTime);
            if (!focused && visibleTime > 0 && shown.removed < 0) {
                if (age > visibleTime + window.fadeTime) continue;
                if (age > visibleTime) alpha = (float) (1 - (age - visibleTime) / Math.max(0.0001, window.fadeTime));
            }
            double enter = window.animationTime <= 0 ? 1 : Math.min(1, age / window.animationTime);
            double exit = shown.removed < 0 ? 0 : window.animationTime <= 0 ? 1 : Math.min(1, (now - shown.removed) / 1e9 / window.animationTime);
            if (exit >= 1) continue;
            float textWidth = contentWidth - (float) window.linePaddingX * 2;
            Laid laid = layout(d, window, shown, textWidth);
            float height = laid.height() + (float) window.linePaddingY * 2;
            placed.add(new Placed(shown, laid, alpha, enter, exit, height));
            total += height + (float) window.lineGap;
        }
        maxScroll = Math.max(0, total - (rect.h() - padding * 2));
        scroll = Math.clamp(scroll, 0, maxScroll);

        boolean bottom = window.verticalAlignment != VerticalAlign.TOP;
        float y = bottom ? rect.y() + rect.h() - padding + scroll : rect.y() + padding - scroll;
        List<Placed> order = placed;
        if (!bottom) {
            order = new ArrayList<>(placed);
            Collections.reverse(order);
            float overflow = Math.max(0, total - (rect.h() - padding * 2));
            y -= overflow - scroll * 2;
        }
        for (Placed one : order) {
            float top = bottom ? y - one.height() : y;
            if (bottom) y -= one.height() + (float) window.lineGap;
            else y += one.height() + (float) window.lineGap;
            if (top > rect.y() + rect.h() || top + one.height() < rect.y()) continue;
            line(d, window, rect, one.shown(), one.laid(), top, one.height(), one.alpha() * windowAlpha,
                    one.enter(), one.exit(), time);
        }
    }

    private static void line(UiDraw d, ChatWindow window, Rect rect, ClientChat.Shown shown, Laid laid, float top,
                             float height, float alpha, double enter, double exit, float time) {
        ChatAnimation in = animation(shown.look, "animation", window.enterAnimation);
        double progress = window.easing.apply(enter, Easing.Direction.OUT) * (1 - window.easing.apply(exit, Easing.Direction.IN));
        ChatAnimation motion = exit > 0 ? window.exitAnimation : in;
        float offsetX = 0;
        float offsetY = 0;
        int reveal = Integer.MAX_VALUE;
        switch (motion) {
            case FADE -> alpha *= (float) progress;
            case SLIDE_LEFT -> offsetX = (float) (-(1 - progress) * rect.w());
            case SLIDE_RIGHT -> offsetX = (float) ((1 - progress) * rect.w());
            case SLIDE_UP -> offsetY = (float) ((1 - progress) * height);
            case SLIDE_DOWN -> offsetY = (float) (-(1 - progress) * height);
            case TYPEWRITER -> reveal = (int) Math.ceil(progress * laid.glyphs());
            default -> {}
        }
        if (alpha <= 0.003) return;
        float padding = (float) window.padding;
        float x = rect.x() + padding + offsetX;
        top += offsetY;
        float lineWidth = rect.w() - padding * 2;
        boolean pop = motion == ChatAnimation.POP && progress < 1;
        if (pop) d.pushTransform(x + lineWidth / 2, top + height / 2, 0, 0, (float) (0.5 + 0.5 * progress), 0);

        float stripWidth = window.lineFitsText ? laid.width() + (float) window.linePaddingX * 2 : lineWidth;
        float stripX = switch (window.horizontalAlignment) {
            case LEFT -> x;
            case CENTER -> x + (lineWidth - stripWidth) / 2;
            case RIGHT -> x + lineWidth - stripWidth;
        };
        Color background = color(shown.look, "backgroundColor", window.lineBackgroundColor);
        double transparency = number(shown.look, "backgroundTransparency", window.lineBackgroundTransparency);
        float radius = (float) number(shown.look, "cornerRadius", window.lineCornerRadius);
        int strip = argb(background, (1 - transparency) * alpha);
        if ((strip >>> 24) != 0) d.roundedRect(stripX, top, stripWidth, height, radius, strip);
        HITS.add(new Hit(stripX, top, stripX + stripWidth, top + height, null, shown, null, null));

        float rowTop = top + (float) window.linePaddingY;
        float innerX = stripX + (float) window.linePaddingX;
        float innerWidth = stripWidth - (float) window.linePaddingX * 2;
        List<Runnable> shaded = new ArrayList<>();
        Look look = Look.of(window, shown.look);
        for (Row row : laid.rows()) {
            float rowX = switch (window.horizontalAlignment) {
                case LEFT -> innerX;
                case CENTER -> innerX + (innerWidth - row.width()) / 2;
                case RIGHT -> innerX + innerWidth - row.width();
            };
            for (Glyph glyph : row.glyphs()) {
                if (glyph.index() >= reveal) break;
                float gx = rowX + glyph.x();
                float gy = rowTop + row.height() - glyph.height();
                glyph(d, look, shown, glyph, gx, gy, alpha, time, shaded);
            }
            rowTop += row.height();
        }
        if (pop) d.popTransform();
    }

    private static void glyph(UiDraw d, Look look, ClientChat.Shown shown, Glyph glyph, float x, float y,
                              float alpha, float time, List<Runnable> shaded) {
        RichText.Style style = glyph.style();
        if (shown != null && (style.click() != null || style.hover() != null || style.body() >= 0 || glyph.kind() == Kind.ITEM)) {
            HITS.add(new Hit(x, y, x + glyph.width(), y + glyph.height(), style, shown,
                    glyph.kind() == Kind.ITEM ? (ItemStack) glyph.extra() : null, null));
        }
        float a = (float) (alpha * (1 - style.transparency()));
        float dx = 0;
        float dy = 0;
        float spin = 0;
        float px = glyph.height();
        for (RichText.Effect effect : style.effects()) {
            double strength = effect.number("strength", 1) * px / LINE;
            double speed = effect.number("speed", 1);
            int i = glyph.index() - glyph.spanStart();
            switch (effect.name()) {
                case "wave" -> dy += (float) (Math.sin(time * speed * 5 + i * 0.6) * strength);
                case "bounce" -> dy -= (float) (Math.abs(Math.sin(time * speed * 4 + i * 0.35)) * strength * 2);
                case "shake" -> {
                    long tick = (long) (time * 30 * speed);
                    dx += (float) ((hash(i, tick) - 0.5) * strength);
                    dy += (float) ((hash(i + 7919, tick) - 0.5) * strength);
                }
                case "pulse" -> a *= (float) (0.55 + 0.45 * Math.sin(time * speed * 4));
                case "fade" -> a *= (float) (0.5 + 0.5 * Math.sin(time * speed * 3 + i * 0.4));
                case "spin" -> spin += (float) (time * speed * 3);
                default -> {}
            }
        }
        if (a <= 0.003) return;
        x += dx;
        y += dy;

        if (style.mark() != null) {
            d.rect(x, y, glyph.width(), glyph.height(), argb(style.mark(), style.mark().a() * a));
        }
        switch (glyph.kind()) {
            case IMAGE -> {
                int texture = UiImages.texture((String) glyph.extra());
                if (texture != 0) d.image(texture, x, y, glyph.width(), glyph.height(), argb(Color.WHITE, a));
                return;
            }
            case ITEM -> {
                if (a > 0.5 && shown != null) ITEMS.add(new ItemDraw((ItemStack) glyph.extra(), x, y, glyph.height()));
                return;
            }
            default -> {}
        }

        Color base = style.color() != null ? style.color() : look.textColor();
        if (style.gradientTo() != null && glyph.spanLength() > 1) {
            float t = (glyph.index() - glyph.spanStart()) / (float) (glyph.spanLength() - 1);
            base = lerp(base, style.gradientTo(), t);
        }
        if (style.rainbow() > 0) {
            float hue = (float) ((glyph.index() * 0.06 + time * 0.25 * style.rainbow()) % 1.0);
            int rgb = hsb(hue, 0.7f, 1f);
            base = new Color(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f, 1);
        }
        int fill = argb(base, a);
        int codepoint = glyph.codepoint();
        if (style.obfuscated() && codepoint != ' ') codepoint = 33 + (int) (hash(glyph.index(), (long) (time * 20)) * 93);

        String fontName = style.font() != null ? style.font() : look.font();
        UiFonts.Face face = UiFonts.of(fontName);
        float scale = px / LINE;
        ShaderProgram shader = style.shader() == null ? null : ChatShaders.text(style.shader());

        Color strokeColor = style.stroke() != null ? style.stroke() : look.strokeColor();
        double strokeAlpha = style.stroke() != null ? style.stroke().a() : look.strokeAlpha();
        float thickness = (float) (style.stroke() != null ? style.strokeThickness() : 1) * scale;
        boolean shadow = style.shadow() != null ? style.shadow().a() > 0 : look.shadow();
        int shadowArgb = style.shadow() != null ? argb(style.shadow(), a) : (fill & 0xFF000000) | ((fill & 0xFCFCFC) >> 2);

        if (face instanceof UiFonts.Vector vector) {
            Identifier previous = d.currentFont();
            d.font(vector.font());
            float baseline = y + px * 0.8f;
            if (shadow) d.glyph(codepoint, x + scale, baseline + scale, px, shadowArgb, 0, 0);
            if (strokeAlpha > 0.003) d.glyph(codepoint, x, baseline, px, argb(strokeColor, strokeAlpha * a), -thickness, 0);
            d.glyph(codepoint, x, baseline, px, fill, 0, 0);
            if (style.bold()) d.glyph(codepoint, x + scale * 0.5f, baseline, px, fill, 0, 0);
            if (previous != null) d.font(previous);
        } else {
            FontDescription font = ((UiFonts.Game) face).font();
            BakedGlyph baked = glyphs(font).getGlyph(codepoint);
            if (baked instanceof BakedSheetGlyph sheet && sheet.textureView.texture() instanceof GlTexture gl) {
                if (shadow) quad(d, gl.glId(), sheet, x + scale, y + scale, scale, style.italic(), spin, shadowArgb, null, shaded);
                if (strokeAlpha > 0.003) {
                    int strokeArgb = argb(strokeColor, strokeAlpha * a);
                    for (int ox = -1; ox <= 1; ox++) {
                        for (int oy = -1; oy <= 1; oy++) {
                            if (ox == 0 && oy == 0) continue;
                            quad(d, gl.glId(), sheet, x + ox * thickness, y + oy * thickness, scale, style.italic(), spin, strokeArgb, null, shaded);
                        }
                    }
                }
                quad(d, gl.glId(), sheet, x, y, scale, style.italic(), spin, fill, shader, shaded);
                if (style.bold()) quad(d, gl.glId(), sheet, x + scale, y, scale, style.italic(), spin, fill, shader, shaded);
            }
        }
        if (style.underline()) d.rect(x, y + px - scale, glyph.width(), scale, fill);
        if (style.strike()) d.rect(x, y + px * 0.45f, glyph.width(), scale, fill);
        if (!shaded.isEmpty()) {
            List<Runnable> run = new ArrayList<>(shaded);
            shaded.clear();
            d.withProgram(shader, program -> program.setFloat("Time", time), () -> run.forEach(Runnable::run));
        }
    }

    private static void quad(UiDraw d, int texture, BakedSheetGlyph sheet, float x, float y, float scale, boolean italic,
                             float spin, int argb, ShaderProgram shader, List<Runnable> shaded) {
        float x0 = x + sheet.left * scale;
        float x1 = x + sheet.right * scale;
        float y0 = y + sheet.up * scale;
        float y1 = y + sheet.down * scale;
        float lean = italic ? (y1 - y0) * 0.25f : 0;
        float[] corners = {x0 + lean, y0, x1 + lean, y0, x1, y1, x0, y1};
        if (spin != 0) {
            float cx = (x0 + x1) / 2;
            float cy = (y0 + y1) / 2;
            float cos = (float) Math.cos(spin);
            float sin = (float) Math.sin(spin);
            for (int i = 0; i < 8; i += 2) {
                float px = corners[i] - cx;
                float py = corners[i + 1] - cy;
                corners[i] = cx + px * cos - py * sin;
                corners[i + 1] = cy + px * sin + py * cos;
            }
        }
        Runnable draw = () -> d.imageQuad(texture, corners[0], corners[1], corners[2], corners[3], corners[4], corners[5],
                corners[6], corners[7], sheet.u0, sheet.v0, sheet.u1, sheet.v1, argb, true);
        if (shader != null) {
            shaded.add(draw);
        } else {
            draw.run();
        }
    }

    private static final Map<String, Laid> LAID = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Laid> eldest) {
            return size() > 256;
        }
    };

    public static float[] measure(UiDraw d, String markup, float width, float px, String font) {
        Laid laid = laid(d, markup, width, px, font);
        return new float[] {laid.width(), laid.height()};
    }

    public static void text(UiDraw d, String markup, float x, float y, float width, float px, Look look, float alpha,
                     HorizontalAlign align) {
        Laid laid = laid(d, markup, width, px, look.font());
        float time = (float) ((System.nanoTime() - START) / 1e9);
        List<Runnable> shaded = new ArrayList<>();
        float rowTop = y;
        for (Row row : laid.rows()) {
            float rowX = switch (align) {
                case LEFT -> x;
                case CENTER -> x + (width - row.width()) / 2;
                case RIGHT -> x + width - row.width();
            };
            for (Glyph glyph : row.glyphs()) {
                glyph(d, look, null, glyph, rowX + glyph.x(), rowTop + row.height() - glyph.height(), alpha, time, shaded);
            }
            rowTop += row.height();
        }
    }

    private static Laid laid(UiDraw d, String markup, float width, float px, String font) {
        String key = markup + "\u0000" + width + "\u0000" + px + "\u0000" + font;
        Laid laid = LAID.get(key);
        if (laid == null) {
            laid = wrap(d, glyphs(d, RichText.parse(markup), px, font), width);
            LAID.put(key, laid);
        }
        return laid;
    }

    private static Laid layout(UiDraw d, ChatWindow window, ClientChat.Shown shown, float width) {
        String composed = compose(window, shown);
        float px = (float) number(shown.look, "textSize", window.textSize);
        String font = string(shown.look, "font", window.font);
        int key = Objects.hash(composed, width, px, font);
        if (shown.layout instanceof Laid laid && shown.layoutKey == key) return laid;
        if (!composed.equals(shown.composed)) {
            shown.composed = composed;
            shown.pieces = RichText.parse(composed);
        }
        Laid laid = wrap(d, glyphs(d, shown.pieces, px, font), width);
        shown.layout = laid;
        shown.layoutKey = key;
        return laid;
    }

    private static String compose(ChatWindow window, ClientChat.Shown shown) {
        String text = string(shown.look, "text", shown.line.text);
        String prefix = string(shown.look, "prefix", shown.line.prefix);
        StringBuilder out = new StringBuilder();
        String icon = string(shown.look, "icon", "");
        if (!icon.isEmpty()) out.append("<img src=\"").append(icon.replace("\"", "")).append("\"/> ");
        if (window.timestamps && shown.line.timestamp > 0) {
            String stamp;
            try {
                stamp = DateTimeFormatter.ofPattern(window.timestampFormat).withZone(ZoneId.systemDefault())
                        .format(Instant.ofEpochMilli(shown.line.timestamp));
            } catch (IllegalArgumentException badPattern) {
                stamp = "";
            }
            out.append("<color=").append(hex(window.timestampColor)).append(">[").append(RichText.escape(stamp)).append("]</color> ");
        }
        if (!prefix.isEmpty()) {
            Color prefixColor = color(shown.look, "prefixColor", window.prefixColor);
            out.append("<color=").append(hex(prefixColor)).append(">").append(prefix).append("</color>: ");
        }
        out.append(text);
        return out.toString();
    }

    private static List<Glyph> glyphs(UiDraw d, List<RichText.Piece> pieces, float px, String windowFont) {
        List<Glyph> out = new ArrayList<>();
        int index = 0;
        for (RichText.Piece piece : pieces) {
            RichText.Style style = piece.style();
            float size = (float) (px * style.size());
            switch (piece) {
                case RichText.Text text -> {
                    String shown = style.uppercase() ? text.text().toUpperCase(Locale.ROOT) : text.text();
                    UiFonts.Face face = UiFonts.of(style.font() != null ? style.font() : windowFont);
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
        GlyphSource source = glyphs(((UiFonts.Game) face).font());
        return source.getGlyph(codepoint).info().getAdvance(bold) * size / LINE;
    }

    private static Laid wrap(UiDraw d, List<Glyph> glyphs, float width) {
        List<Row> rows = new ArrayList<>();
        List<Glyph> row = new ArrayList<>();
        float rowWidth = 0;
        int i = 0;
        while (i < glyphs.size()) {
            Glyph first = glyphs.get(i);
            if (first.kind() == Kind.TEXT && first.codepoint() == '\n') {
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
                if (g.kind() == Kind.TEXT && g.codepoint() == '\n') break;
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
                if (row.isEmpty() && g.kind() == Kind.TEXT && g.codepoint() == ' ') continue;
                row.add(new Glyph(g.kind(), g.codepoint(), rowWidth, g.width(), g.height(), g.style(), g.index(),
                        g.spanStart(), g.spanLength(), g.extra()));
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
        return new Laid(rows, widest, height, glyphs.size());
    }

    private static Row row(List<Glyph> glyphs, float width, float least) {
        float height = least;
        for (Glyph g : glyphs) height = Math.max(height, g.height());
        if (height == 0) height = LINE;
        return new Row(glyphs, width, height);
    }

    private static void tabs(UiDraw d, ChatWindow window, Rect rect, float alpha) {
        ChatTabs tabs = ChatLook.tabs();
        if (tabs == null || !tabs.enabled) return;
        List<TextChannel> channels = ClientChat.INSTANCE.channels();
        if (channels.size() < 2) return;
        Instance target = ClientChat.INSTANCE.target();
        float px = (float) window.textSize;
        float x = rect.x();
        float height = px + 4;
        float y = rect.y() - height - 2;
        FontDescription font = FontDescription.DEFAULT;
        for (TextChannel channel : channels) {
            String name = channel.displayName.isEmpty() ? channel.name() : channel.displayName;
            int unread = ClientChat.INSTANCE.unread(channel);
            String label = unread > 0 ? name + " (" + unread + ")" : name;
            float w = plainWidth(font, label, px) + 8;
            boolean selected = channel == target;
            d.roundedRect(x, y, w, height, 2, argb(selected ? tabs.selectedColor : tabs.backgroundColor, 0.75 * alpha));
            plain(d, font, label, x + 4, y + 2, px, argb(unread > 0 && !selected ? tabs.unreadColor : tabs.textColor, alpha));
            HITS.add(new Hit(x, y, x + w, y + height, null, null, null, channel));
            x += w + 2;
        }
    }

    private static void windowShader(UiDraw d, ChatWindow window, Rect rect, float time) {
        ShaderProgram program = ChatShaders.window(window.shader);
        if (program == null) return;
        d.interrupt(() -> {
            if (capture == null) capture = Framebuffers.captureColor();
            capture.blitColorFromMain();
            float guiToPixels = capture.width() / d.width();
            GlState.beginFullscreen();
            try {
                GlState.bindTexture(0, capture.colorTextureGlId(0));
                program.begin();
                program.setSampler("SceneColorSampler", 0);
                program.setVec2("ScreenSize", capture.width(), capture.height());
                program.setVec4("Rect", rect.x() * guiToPixels, rect.y() * guiToPixels, rect.w() * guiToPixels, rect.h() * guiToPixels);
                program.setFloat("Time", time);
                program.draw();
            } catch (RuntimeException broken) {
                ChatShaders.broke(program, broken);
            } finally {
                GlState.endFullscreen();
            }
        });
    }

    public static void extract(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, boolean foreground) {
        for (ItemDraw item : new ArrayList<>(ITEMS)) {
            graphics.pose().pushMatrix();
            graphics.pose().translate(item.x(), item.y());
            float s = item.size() / 16f;
            graphics.pose().scale(s, s);
            graphics.item(item.stack(), 0, 0);
            graphics.pose().popMatrix();
        }
        if (!foreground) return;
        Hit hit = hitAt(mouseX, mouseY);
        if (hit == null) return;
        if (hit.item() != null) {
            graphics.setTooltipForNextFrame(font, hit.item(), mouseX, mouseY);
        } else if (hit.style() != null && hit.style().hover() != null) {
            graphics.setTooltipForNextFrame(font, ChatText.of(hit.style().hover()), mouseX, mouseY);
        }
    }

    public static boolean click(double mouseX, double mouseY, int button) {
        if (!active() || !focused()) return false;
        Hit hit = hitAt((float) mouseX, (float) mouseY);
        if (hit == null) return false;
        if (hit.tab() != null) {
            ClientChat.INSTANCE.setTarget(hit.tab());
            return true;
        }
        ScriptEngine vm = ClientPlace.vm();
        InstanceTree tree = ClientScene.tree();
        Map<String, Object> message = hit.shown() == null ? Map.of() : hit.shown().line.toMap(tree);
        RichText.Style style = hit.style();
        if (style != null && style.click() != null) {
            act(style.click(), message);
            return true;
        }
        if (style != null && style.body() >= 0 && tree != null) {
            Instance body = tree.byId(style.body());
            if (vm != null) vm.chatEvent("bodyClicked", body, message);
            return true;
        }
        if (hit.shown() != null && vm != null) {
            vm.chatEvent("messageClicked", message, (double) button);
            return false;
        }
        return false;
    }

    private static void act(RichText.Link link, Map<String, Object> message) {
        Minecraft client = Minecraft.getInstance();
        switch (link.action()) {
            case "run" -> {
                if (link.value().startsWith("/") && client.player != null) {
                    client.player.connection.sendCommand(link.value().substring(1));
                } else {
                    ClientChat.INSTANCE.typed(link.value());
                }
            }
            case "suggest" -> {
                if (client.screen instanceof ChatScreen screen) {
                    screen.insertText(link.value(), true);
                } else {
                    client.setScreen(new ChatScreen(link.value(), false));
                }
            }
            case "copy" -> client.keyboardHandler.setClipboard(link.value());
            case "url" -> ConfirmLinkScreen.confirmLinkNow(client.screen, link.value());
            case "callback" -> {
                ScriptEngine vm = ClientPlace.vm();
                if (vm != null) vm.chatEvent("linkClicked", link.value(), message);
            }
            default -> {}
        }
    }

    public static boolean scroll(double amount) {
        if (!active() || !focused()) return false;
        ChatWindow window = ChatLook.windowOrDefault();
        scroll = Math.clamp(scroll + (float) (amount * (window.textSize + window.lineGap + window.linePaddingY * 2) * 3), 0, maxScroll);
        return true;
    }

    private static Hit hitAt(float x, float y) {
        Hit found = null;
        for (Hit hit : HITS) {
            if (x < hit.x0() || y < hit.y0() || x > hit.x1() || y > hit.y1()) continue;
            if (found == null || hit.style() != null || hit.tab() != null || hit.item() != null) found = hit;
        }
        return found;
    }

    private static GlyphSource glyphs(FontDescription font) {
        return Minecraft.getInstance().font.getGlyphSource(font);
    }

    private static float plainWidth(FontDescription font, String text, float px) {
        float pen = 0;
        GlyphSource source = glyphs(font);
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            pen += source.getGlyph(cp).info().getAdvance() * px / LINE;
            i += Character.charCount(cp);
        }
        return pen;
    }

    private static void plain(UiDraw d, FontDescription font, String text, float x, float y, float px, int argb) {
        float scale = px / LINE;
        GlyphSource source = glyphs(font);
        float pen = x;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            BakedGlyph glyph = source.getGlyph(cp);
            if (glyph instanceof BakedSheetGlyph sheet && sheet.textureView.texture() instanceof GlTexture gl) {
                d.imageRegion(gl.glId(), pen + sheet.left * scale, y + sheet.up * scale, pen + sheet.right * scale,
                        y + sheet.down * scale, sheet.u0, sheet.v0, sheet.u1, sheet.v1, argb, true);
            }
            pen += glyph.info().getAdvance() * scale;
            i += Character.charCount(cp);
        }
    }

    private static ItemStack stack(String id, int count) {
        String key = id + "*" + count;
        return STACKS.computeIfAbsent(key, k -> {
            Identifier item = Identifier.tryParse(id);
            if (item == null || !BuiltInRegistries.ITEM.containsKey(item)) return ItemStack.EMPTY;
            return new ItemStack(BuiltInRegistries.ITEM.getValue(item), Math.max(1, count));
        });
    }

    private static double hash(long a, long b) {
        long h = a * 0x9E3779B97F4A7C15L ^ b * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        return (h & 0xFFFFFF) / (double) 0x1000000;
    }

    private static int hsb(float hue, float saturation, float brightness) {
        float h = (hue - (float) Math.floor(hue)) * 6f;
        float c = brightness * saturation;
        float x = c * (1 - Math.abs(h % 2 - 1));
        float m = brightness - c;
        float r, g, b;
        switch ((int) h) {
            case 0 -> { r = c; g = x; b = 0; }
            case 1 -> { r = x; g = c; b = 0; }
            case 2 -> { r = 0; g = c; b = x; }
            case 3 -> { r = 0; g = x; b = c; }
            case 4 -> { r = x; g = 0; b = c; }
            default -> { r = c; g = 0; b = x; }
        }
        return Math.round((r + m) * 255) << 16 | Math.round((g + m) * 255) << 8 | Math.round((b + m) * 255);
    }

    private static Color lerp(Color a, Color b, float t) {
        return new Color(a.r() + (b.r() - a.r()) * t, a.g() + (b.g() - a.g()) * t, a.b() + (b.b() - a.b()) * t,
                a.a() + (b.a() - a.a()) * t);
    }

    private static String hex(Color c) {
        return String.format("#%06x", ChatText.rgb(c));
    }

    public static int argbOf(Color c, double alpha) {
        return argb(c, alpha);
    }

    static int argb(Color c, double alpha) {
        int a = (int) Math.round(Math.clamp(c.a() * alpha, 0, 1) * 255);
        return a << 24 | ChatText.rgb(new Color(Math.clamp(c.r(), 0f, 1f), Math.clamp(c.g(), 0f, 1f), Math.clamp(c.b(), 0f, 1f), 1));
    }

    private static double number(Map<String, Object> look, String key, double fallback) {
        return ChatViewNumbers.number(look, key, fallback);
    }

    private static boolean bool(Map<String, Object> look, String key, boolean fallback) {
        return ChatViewNumbers.bool(look, key, fallback);
    }

    private static String string(Map<String, Object> look, String key, String fallback) {
        return ChatViewNumbers.string(look, key, fallback);
    }

    private static Color color(Map<String, Object> look, String key, Color fallback) {
        return ChatViewNumbers.color(look, key, fallback);
    }

    private static ChatAnimation animation(Map<String, Object> look, String key, ChatAnimation fallback) {
        if (!(look.get(key) instanceof String name)) return fallback;
        for (ChatAnimation one : ChatAnimation.values()) {
            if (Enums.name(one).equals(name)) return one;
        }
        return fallback;
    }
}
