package com.meekdev.moud.mod.adapter.chat;

import com.meekdev.amnetic.client.framebuffer.Framebuffer;
import com.meekdev.amnetic.client.framebuffer.Framebuffers;
import com.meekdev.amnetic.client.render.GlState;
import com.meekdev.amnetic.client.render.ShaderProgram;
import com.meekdev.amnetic.client.surface.Surfaces;
import com.meekdev.amnetic.client.surface.draw.UiDraw;
import com.meekdev.moud.core.chat.ChatAnimation;
import com.meekdev.moud.core.chat.ChatBackdrop;
import com.meekdev.moud.core.chat.ChatTabs;
import com.meekdev.moud.core.chat.ChatWindow;
import com.meekdev.moud.core.chat.TextChannel;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.text.RichText;
import com.meekdev.moud.core.tween.Easing;
import com.meekdev.moud.core.ui.VerticalAlign;
import com.meekdev.moud.mod.adapter.text.Argb;
import com.meekdev.moud.mod.adapter.text.TextLayout;
import com.meekdev.moud.mod.adapter.text.TextLook;
import com.meekdev.moud.mod.adapter.text.TextPainter;
import com.meekdev.moud.mod.client.ClientPlace;
import com.meekdev.moud.mod.client.ClientScene;
import com.meekdev.moud.script.host.Host;
import com.mojang.blaze3d.opengl.GlTexture;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
import net.minecraft.network.chat.FontDescription;
import net.minecraft.world.item.ItemStack;

public final class ChatView {

    private record Hit(float x0, float y0, float x1, float y1, RichText.Style style, ClientChat.Shown shown,
                       ItemStack item, TextChannel tab) {}

    private record ItemDraw(ItemStack stack, float x, float y, float size) {}

    private record Rect(float x, float y, float w, float h) {}

    private record Placed(ClientChat.Shown shown, TextLayout layout, float alpha, double enter, double exit, float height) {}

    private static final List<Hit> HITS = new ArrayList<>();
    private static final List<ItemDraw> ITEMS = new ArrayList<>();

    private static float scroll;
    private static float maxScroll;
    private static double hidden;
    private static long lastFrame = System.nanoTime();
    private static Framebuffer capture;

    private ChatView() {}

    public static void install() {
        Surfaces.hud().onDraw(ChatView::draw);
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

        float time = TextPainter.time();
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
                    Argb.of(window.backgroundColor, (1 - window.backgroundTransparency) * windowAlpha));
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

        List<Placed> placed = new ArrayList<>();
        float total = 0;
        for (int n = lines.size() - 1; n >= 0; n--) {
            ClientChat.Shown shown = lines.get(n);
            double age = (now - shown.added) / 1e9;
            float alpha = 1;
            double visibleTime = Looks.number(shown.look, "visibleTime", window.visibleTime);
            if (!focused && visibleTime > 0 && shown.removed < 0) {
                if (age > visibleTime + window.fadeTime) continue;
                if (age > visibleTime) alpha = (float) (1 - (age - visibleTime) / Math.max(0.0001, window.fadeTime));
            }
            double enter = window.animationTime <= 0 ? 1 : Math.min(1, age / window.animationTime);
            double exit = shown.removed < 0 ? 0 : window.animationTime <= 0 ? 1 : Math.min(1, (now - shown.removed) / 1e9 / window.animationTime);
            if (exit >= 1) continue;
            TextLayout layout = layout(d, window, shown, contentWidth - (float) window.linePaddingX * 2);
            float height = layout.height() + (float) window.linePaddingY * 2;
            placed.add(new Placed(shown, layout, alpha, enter, exit, height));
            total += height + (float) window.lineGap;
        }
        maxScroll = Math.max(0, total - (rect.h() - padding * 2));
        scroll = Math.clamp(scroll, 0, maxScroll);

        boolean bottom = window.verticalAlignment != VerticalAlign.TOP;
        float y = bottom ? rect.y() + rect.h() - padding + scroll : rect.y() + padding - scroll;
        if (!bottom) {
            Collections.reverse(placed);
            y -= maxScroll - scroll * 2;
        }
        for (Placed one : placed) {
            float top = bottom ? y - one.height() : y;
            if (bottom) y -= one.height() + (float) window.lineGap;
            else y += one.height() + (float) window.lineGap;
            if (top > rect.y() + rect.h() || top + one.height() < rect.y()) continue;
            line(d, window, rect, one, top, one.alpha() * windowAlpha, time);
        }
    }

    private static void line(UiDraw d, ChatWindow window, Rect rect, Placed placed, float top, float alpha, float time) {
        ClientChat.Shown shown = placed.shown();
        float height = placed.height();
        ChatAnimation in = Looks.animation(shown.look, "animation", window.enterAnimation);
        double progress = window.easing.apply(placed.enter(), Easing.Direction.OUT) * (1 - window.easing.apply(placed.exit(), Easing.Direction.IN));
        ChatAnimation motion = placed.exit() > 0 ? window.exitAnimation : in;
        float offsetX = 0;
        float offsetY = 0;
        int reveal = Integer.MAX_VALUE;
        switch (motion) {
            case FADE -> alpha *= (float) progress;
            case SLIDE_LEFT -> offsetX = (float) (-(1 - progress) * rect.w());
            case SLIDE_RIGHT -> offsetX = (float) ((1 - progress) * rect.w());
            case SLIDE_UP -> offsetY = (float) ((1 - progress) * height);
            case SLIDE_DOWN -> offsetY = (float) (-(1 - progress) * height);
            case TYPEWRITER -> reveal = (int) Math.ceil(progress * placed.layout().glyphCount());
            default -> {}
        }
        if (alpha <= 0.003) return;
        float padding = (float) window.padding;
        float x = rect.x() + padding + offsetX;
        top += offsetY;
        float lineWidth = rect.w() - padding * 2;
        boolean pop = motion == ChatAnimation.POP && progress < 1;
        if (pop) d.pushTransform(x + lineWidth / 2, top + height / 2, 0, 0, (float) (0.5 + 0.5 * progress), 0);

        float stripWidth = window.lineFitsText ? placed.layout().width() + (float) window.linePaddingX * 2 : lineWidth;
        float stripX = x + TextLayout.align(window.horizontalAlignment, stripWidth, lineWidth);
        Color background = Looks.color(shown.look, "backgroundColor", window.lineBackgroundColor);
        double transparency = Looks.number(shown.look, "backgroundTransparency", window.lineBackgroundTransparency);
        float radius = (float) Looks.number(shown.look, "cornerRadius", window.lineCornerRadius);
        int strip = Argb.of(background, (1 - transparency) * alpha);
        if ((strip >>> 24) != 0) d.roundedRect(stripX, top, stripWidth, height, radius, strip);
        HITS.add(new Hit(stripX, top, stripX + stripWidth, top + height, null, shown, null, null));

        TextPainter.Hits hits = new TextPainter.Hits() {
            @Override
            public void glyph(TextLayout.Glyph glyph, float gx, float gy) {
                ItemStack item = glyph.kind() == TextLayout.Kind.ITEM ? (ItemStack) glyph.extra() : null;
                HITS.add(new Hit(gx, gy, gx + glyph.width(), gy + glyph.height(), glyph.style(), shown, item, null));
            }

            @Override
            public void item(ItemStack stack, float ix, float iy, float size) {
                ITEMS.add(new ItemDraw(stack, ix, iy, size));
            }
        };
        float innerWidth = stripWidth - (float) window.linePaddingX * 2;
        TextPainter.draw(d, placed.layout(), stripX + (float) window.linePaddingX, top + (float) window.linePaddingY,
                innerWidth, window.horizontalAlignment, look(window, shown.look), alpha, time, reveal, hits);
        if (pop) d.popTransform();
    }

    private static TextLook look(ChatWindow window, Map<String, Object> look) {
        return new TextLook(Looks.color(look, "textColor", window.textColor), Looks.string(look, "font", window.font),
                Looks.bool(look, "textShadow", window.textShadow), Looks.color(look, "textStrokeColor", window.textStrokeColor),
                1 - Looks.number(look, "textStrokeTransparency", window.textStrokeTransparency));
    }

    private static TextLayout layout(UiDraw d, ChatWindow window, ClientChat.Shown shown, float width) {
        String composed = compose(window, shown);
        float px = (float) Looks.number(shown.look, "textSize", window.textSize);
        String font = Looks.string(shown.look, "font", window.font);
        int key = Objects.hash(composed, width, px, font);
        if (shown.layout instanceof TextLayout layout && shown.layoutKey == key) return layout;
        if (!composed.equals(shown.composed)) {
            shown.composed = composed;
            shown.pieces = RichText.parse(composed);
        }
        TextLayout layout = TextLayout.wrap(d, shown.pieces, width, px, font);
        shown.layout = layout;
        shown.layoutKey = key;
        return layout;
    }

    private static String compose(ChatWindow window, ClientChat.Shown shown) {
        String text = Looks.string(shown.look, "text", shown.line.text);
        String prefix = Looks.string(shown.look, "prefix", shown.line.prefix);
        StringBuilder out = new StringBuilder();
        String icon = Looks.string(shown.look, "icon", "");
        if (!icon.isEmpty()) out.append("<img src=\"").append(icon.replace("\"", "")).append("\"/> ");
        if (window.timestamps && shown.line.timestamp > 0) {
            String stamp;
            try {
                stamp = DateTimeFormatter.ofPattern(window.timestampFormat).withZone(ZoneId.systemDefault())
                        .format(Instant.ofEpochMilli(shown.line.timestamp));
            } catch (IllegalArgumentException ignored) {
                stamp = "";
            }
            out.append("<color=").append(Argb.hex(window.timestampColor)).append(">[").append(RichText.escape(stamp)).append("]</color> ");
        }
        if (!prefix.isEmpty()) {
            Color prefixColor = Looks.color(shown.look, "prefixColor", window.prefixColor);
            out.append("<color=").append(Argb.hex(prefixColor)).append(">").append(prefix).append("</color>: ");
        }
        out.append(text);
        return out.toString();
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
        for (TextChannel channel : channels) {
            String name = channel.displayName.isEmpty() ? channel.name() : channel.displayName;
            int unread = ClientChat.INSTANCE.unreadCount(channel);
            String label = unread > 0 ? name + " (" + unread + ")" : name;
            float w = plainWidth(label, px) + 8;
            boolean selected = channel == target;
            d.roundedRect(x, y, w, height, 2, Argb.of(selected ? tabs.selectedColor : tabs.backgroundColor, 0.75 * alpha));
            plain(d, label, x + 4, y + 2, px, Argb.of(unread > 0 && !selected ? tabs.unreadColor : tabs.textColor, alpha));
            HITS.add(new Hit(x, y, x + w, y + height, null, null, null, channel));
            x += w + 2;
        }
    }

    private static float plainWidth(String text, float px) {
        GlyphSource source = Minecraft.getInstance().font.getGlyphSource(FontDescription.DEFAULT);
        float width = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            width += source.getGlyph(cp).info().getAdvance() * px / TextLayout.LINE;
            i += Character.charCount(cp);
        }
        return width;
    }

    private static void plain(UiDraw d, String text, float x, float y, float px, int argb) {
        float scale = px / TextLayout.LINE;
        GlyphSource source = Minecraft.getInstance().font.getGlyphSource(FontDescription.DEFAULT);
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
            } catch (RuntimeException e) {
                ChatShaders.logCompileError(program, e);
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
        Host vm = ClientPlace.host();
        InstanceTree tree = ClientScene.tree();
        Map<String, Object> message = hit.shown() == null ? Map.of() : hit.shown().line.toMap(tree);
        RichText.Style style = hit.style();
        if (style != null && style.click() != null) {
            act(style.click(), message);
            return true;
        }
        if (style != null && style.body() >= 0 && tree != null) {
            if (vm != null) vm.chatEvent("bodyClicked", tree.byId(style.body()), message);
            return true;
        }
        if (hit.shown() != null && vm != null) vm.chatEvent("messageClicked", message, (double) button);
        return false;
    }

    private static void act(RichText.Link link, Map<String, Object> message) {
        Minecraft client = Minecraft.getInstance();
        switch (link.action()) {
            case "run" -> {
                if (link.value().startsWith("/") && client.player != null) {
                    client.player.connection.sendCommand(link.value().substring(1));
                } else {
                    ClientChat.INSTANCE.sendTyped(link.value());
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
                Host vm = ClientPlace.host();
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
}
