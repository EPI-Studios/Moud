package com.meekdev.moud.mod.adapter.ui;

import com.meekdev.moud.core.instance.BillboardGui;
import com.meekdev.moud.core.instance.GuiLayout;
import com.meekdev.moud.core.instance.GuiObject;
import com.meekdev.moud.core.instance.ImageLabel;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.ScreenGui;
import com.meekdev.moud.core.instance.SurfaceGui;
import com.meekdev.moud.core.instance.TextButton;
import com.meekdev.moud.core.instance.TextLabel;
import com.meekdev.moud.core.math.Color;
import com.meekdev.amnetic.client.surface.draw.UiDraw;
import com.meekdev.amnetic.client.surface.widget.Widget;
import com.mojang.blaze3d.opengl.GlTexture;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;

// one instance of an interface, read live every frame, so a write shows on the next one
final class Node extends Widget {

    final Instance source;

    Node(Instance source) {
        this.source = source;
    }

    @Override
    protected void placeChildren() {
        // stable, so equal zIndex keeps the order children were added in
        children().sort((a, b) -> Integer.compare(z(a), z(b)));
        GuiLayout.Box self = new GuiLayout.Box(x, y, w, h);
        for (Widget child : children()) {
            if (!(child instanceof Node node) || !(node.source instanceof GuiObject object)) continue;
            node.visible(object.visible);
            GuiLayout.Box box = GuiLayout.place(object, self);
            node.layout((float) box.x(), (float) box.y(), (float) box.w(), (float) box.h());
        }
    }

    private static int z(Widget widget) {
        return widget instanceof Node node && node.source instanceof GuiObject object ? object.zIndex : 0;
    }

    @Override
    protected void drawSelf(UiDraw d, float alpha) {
        if (!(source instanceof GuiObject object)) return;
        float radius = (float) Math.min(object.cornerRadius, Math.min(w, h) * 0.5);

        Color back = object.backgroundColor;
        if (source instanceof TextButton button && button.autoButtonColor) {
            back = shade(back, pressed ? 0.25 : hovered ? 0.12 : 0);
        }
        int backArgb = argb(back, (1 - object.backgroundTransparency) * alpha);
        if ((backArgb >>> 24) != 0) d.roundedRect(x, y, w, h, radius, backArgb);
        if (object.borderSize > 0) {
            d.border(x, y, w, h, radius, (float) object.borderSize, argb(object.borderColor, alpha));
        }

        if (source instanceof ImageLabel image && !image.image.isEmpty()) {
            int texture = texture(image.image);
            if (texture != 0) {
                d.image(texture, x, y, w, h, argb(image.imageColor, (1 - image.imageTransparency) * alpha));
            }
        }
        if (source instanceof TextLabel label && !label.text.isEmpty()) {
            // a draw call has no font until it is given one, and without one every text call
            // quietly draws nothing
            Identifier previous = d.currentFont();
            d.font(UiFonts.of(GuiLayout.font(label)));
            text(d, label, alpha);
            if (previous != null) d.font(previous);
        }

        if (object.clipsDescendants) d.pushClip(x, y, w, h);
    }

    @Override
    public void drawAfterChildren(UiDraw d) {
        if (source instanceof GuiObject object && object.clipsDescendants) d.popClip();
    }

    private void text(UiDraw d, TextLabel label, float alpha) {
        float px = (float) label.textSize;
        int colour = argb(label.textColor, (1 - label.textTransparency) * alpha);
        if ((colour >>> 24) == 0) return;
        List<String> lines = label.textWrapped ? wrap(d, label.text, px, w) : List.of(label.text.split("\n", -1));
        float line = d.lineHeight(px);
        float total = line * lines.size();
        float top = switch (label.textYAlignment) {
            case TOP -> y;
            case CENTER -> y + (h - total) * 0.5f;
            case BOTTOM -> y + h - total;
        };
        for (int n = 0; n < lines.size(); n++) {
            String one = lines.get(n);
            float width = d.textWidth(one, px);
            float left = switch (label.textXAlignment) {
                case LEFT -> x;
                case CENTER -> x + (w - width) * 0.5f;
                case RIGHT -> x + w - width;
            };
            d.text(one, left, top + line * n, px, colour);
        }
    }

    // words onto lines no wider than the box, and a word longer than the box on a line of its own
    private static List<String> wrap(UiDraw d, String text, float px, float width) {
        List<String> lines = new ArrayList<>();
        for (String paragraph : text.split("\n", -1)) {
            StringBuilder current = new StringBuilder();
            for (String word : paragraph.split(" ")) {
                String tried = current.isEmpty() ? word : current + " " + word;
                if (!current.isEmpty() && d.textWidth(tried, px) > width) {
                    lines.add(current.toString());
                    current = new StringBuilder(word);
                } else {
                    current = new StringBuilder(tried);
                }
            }
            lines.add(current.toString());
        }
        return lines;
    }

    // asked of the game each frame, which loads it the first time and hands back the same one after
    private static int texture(String name) {
        Identifier id = Identifier.tryParse(name);
        if (id == null) return 0;
        AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(id);
        return texture != null && texture.getTexture() instanceof GlTexture gl ? gl.glId() : 0;
    }

    private static Color shade(Color c, double amount) {
        float keep = (float) (1 - amount);
        return new Color(c.r() * keep, c.g() * keep, c.b() * keep, c.a());
    }

    private static int argb(Color c, double alpha) {
        int a = (int) Math.round(Math.clamp(c.a() * alpha, 0, 1) * 255);
        int r = Math.round(Math.clamp(c.r(), 0f, 1f) * 255);
        int g = Math.round(Math.clamp(c.g(), 0f, 1f) * 255);
        int b = Math.round(Math.clamp(c.b(), 0f, 1f) * 255);
        return a << 24 | r << 16 | g << 8 | b;
    }

    @Override
    protected boolean interactive() {
        return source instanceof TextButton;
    }

    @Override
    public boolean onMouseDown(float mx, float my, int button) {
        return button == 0 && source instanceof TextButton;
    }

    // a press that is let go outside the button is a press taken back
    @Override
    public void onMouseUp(float mx, float my, int button) {
        if (!(source instanceof TextButton pressedButton) || !pressedButton.isAlive()) return;
        if (mx < x || my < y || mx > x + w || my > y + h) return;
        pressedButton.activated.fire(pressedButton);
    }

    // a surface's own node fills whatever it is laid out into
    static boolean surface(Instance instance) {
        return instance instanceof ScreenGui || instance instanceof BillboardGui || instance instanceof SurfaceGui;
    }
}
