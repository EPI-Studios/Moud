package com.meekdev.moud.mod.adapter.ui;

import com.meekdev.amnetic.client.surface.draw.UiDraw;
import com.meekdev.amnetic.client.surface.widget.Widget;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.ui.BillboardGui;
import com.meekdev.moud.core.ui.CanvasGroup;
import com.meekdev.moud.core.ui.GuiLayout;
import com.meekdev.moud.core.ui.GuiObject;
import com.meekdev.moud.core.ui.ImageButton;
import com.meekdev.moud.core.ui.ImageLabel;
import com.meekdev.moud.core.ui.ScreenGui;
import com.meekdev.moud.core.ui.ScrollingFrame;
import com.meekdev.moud.core.ui.StrokeMode;
import com.meekdev.moud.core.ui.SurfaceGui;
import com.meekdev.moud.core.ui.TextBox;
import com.meekdev.moud.core.ui.TextButton;
import com.meekdev.moud.core.ui.TextEdit;
import com.meekdev.moud.core.ui.TextLabel;
import com.meekdev.moud.core.ui.UIGradient;
import com.meekdev.moud.core.ui.UIStroke;
import com.meekdev.moud.core.ui.ViewportFrame;
import com.meekdev.moud.mod.adapter.render.Viewports;
import com.meekdev.moud.mod.adapter.text.TextLayout;
import com.meekdev.moud.mod.adapter.text.TextLook;
import com.meekdev.moud.mod.adapter.text.TextPainter;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.InputQuirks;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

final class Node extends Widget {

    private static final long BLINK_NANOS = 530_000_000L;
    private static final long DOUBLE_CLICK_NANOS = 400_000_000L;
    private static final int SELECTION = 0x663D8BFF;
    private static final float WHEEL_STEP = 40;

    final Instance source;

    double scale = 1;
    double offsetX;
    double offsetY;
    float boundsW;
    float boundsH;
    GuiLayout.Box canvas;

    private float alpha = 1;
    private boolean clipped;
    private boolean enterPressed;
    private float scrollX;
    private long blinkFrom;
    private long lastClick;
    private List<UiText.Line> editLines = List.of();
    private float[] editLefts = new float[0];
    private float editTop;
    private float editLine = 9;
    private float pressX;
    private float pressY;
    private Vector3 pressCanvas = Vector3.ZERO;
    private boolean draggingThumb;

    Node(Instance source) {
        this.source = source;
    }

    @Override
    protected void placeChildren() {
        children().sort((a, b) -> Integer.compare(z(a), z(b)));
        GuiLayout.Box self = new GuiLayout.Box(x, y, w, h);
        if (source instanceof TextLabel label) {
            GuiLayout.Size bounds = UiText.bounds(label, GuiLayout.wraps(label) ? w : Double.POSITIVE_INFINITY);
            boundsW = (float) bounds.w();
            boundsH = (float) bounds.h();
        }
        canvas = source instanceof ScrollingFrame frame ? GuiLayout.canvas(frame, self, UiText.MEASURE) : null;
        Map<Instance, GuiLayout.Box> boxes = new IdentityHashMap<>();
        for (GuiLayout.Placed placed : GuiLayout.arrange(source, self, UiText.MEASURE)) boxes.put(placed.object(), placed.box());
        for (Widget child : children()) {
            if (!(child instanceof Node node) || !(node.source instanceof GuiObject object)) continue;
            GuiLayout.Box box = boxes.get(object);
            if (box == null) {
                node.visible(false);
                continue;
            }
            node.visible(true);
            float s = (float) GuiLayout.scale(object);
            float centreX = (float) (box.x() + box.w() * 0.5);
            float centreY = (float) (box.y() + box.h() * 0.5);
            float dx = (float) ((box.x() + box.w() * object.anchorX - centreX) * (1 - s));
            float dy = (float) ((box.y() + box.h() * object.anchorY - centreY) * (1 - s));
            node.translate(dx, dy);
            node.scaleChannel(s);
            node.scale = scale * s;
            node.offsetX = scale * (centreX + dx - s * centreX) + offsetX;
            node.offsetY = scale * (centreY + dy - s * centreY) + offsetY;
            node.layout((float) box.x(), (float) box.y(), (float) box.w(), (float) box.h());
        }
    }

    private static int z(Widget widget) {
        return widget instanceof Node node && node.source instanceof GuiObject object ? object.zIndex : 0;
    }

    private float local(float surface, double offset) {
        return (float) ((surface - offset) / Math.max(scale, 1e-6));
    }

    boolean contains(float mx, float my) {
        double left = scale * x + offsetX;
        double top = scale * y + offsetY;
        return mx >= left && my >= top && mx < left + scale * w && my < top + scale * h;
    }

    boolean seeThrough() {
        return source instanceof CanvasGroup group && group.seeThrough();
    }

    @Override
    public Widget hitTest(float mx, float my) {
        return seeThrough() ? null : super.hitTest(mx, my);
    }

    boolean clips() {
        return source instanceof GuiObject object && (object.clipsDescendants || source instanceof CanvasGroup);
    }

    @Override
    protected boolean drawsChildren() {
        return !(source instanceof CanvasGroup);
    }

    @Override
    protected void drawSelf(UiDraw d, float alpha) {
        this.alpha = alpha;
        clipped = false;
        if (!(source instanceof GuiObject object)) return;
        if (source instanceof CanvasGroup group) {
            float density = onScreen() ? (float) Minecraft.getInstance().getWindow().getGuiScale() : 2f;
            d.group(this, x, y, w, h, density, argb(group.groupColor, (1 - group.groupTransparency) * alpha), () -> {
                paint(d, group, 1f);
                for (Widget child : children()) child.draw(d, 1f);
            });
            return;
        }
        paint(d, object, alpha);
        if (object.clipsDescendants) {
            d.pushClip(x, y, w, h);
            clipped = true;
        }
    }

    private void paint(UiDraw d, GuiObject object, float alpha) {
        float radius = (float) GuiLayout.cornerRadius(object, w, h);

        Color back = object.backgroundColor;
        if (autoColored()) back = shade(back, pressed ? 0.25 : hovered ? 0.12 : 0);
        float fill = (float) (1 - object.backgroundTransparency) * alpha;
        UIGradient gradient = GuiLayout.component(object, UIGradient.class);
        if (gradient != null && !gradient.enabled) gradient = null;
        if (fill > 0 && back.a() > 0) {
            if (gradient != null) gradient(d, gradient, back, fill, radius);
            else d.roundedRect(x, y, w, h, radius, argb(back, fill));
        }
        if (object.borderSize > 0) {
            d.border(x, y, w, h, radius, (float) object.borderSize, argb(object.borderColor, alpha));
        }
        UIStroke stroke = GuiLayout.component(object, UIStroke.class);
        if (stroke != null && (!stroke.enabled || stroke.thickness <= 0)) stroke = null;
        if (stroke != null && (stroke.applyStrokeMode == StrokeMode.BORDER || !(source instanceof TextLabel))) {
            float t = (float) stroke.thickness;
            d.border(x - t, y - t, w + t * 2, h + t * 2, radius > 0 ? radius + t : 0, t,
                    argb(stroke.color, (1 - stroke.transparency) * alpha));
        }
        UIStroke textStroke = stroke != null && stroke.applyStrokeMode == StrokeMode.CONTEXTUAL ? stroke : null;

        if (source instanceof ViewportFrame viewport) {
            float density = onScreen() ? (float) Minecraft.getInstance().getWindow().getGuiScale() : 1f;
            Viewports.measure(viewport, w * density, h * density);
            int texture = Viewports.texture(viewport);
            if (texture != 0) {
                d.imageRegion(texture, x, y, x + w, y + h, 0, 1, 1, 0, argb(viewport.imageColor, (1 - viewport.imageTransparency) * alpha), false);
            }
        }
        if (source instanceof ImageLabel image) {
            String shown = image instanceof ImageButton button ? button.shownImage() : image.image;
            int texture = shown.isEmpty() ? 0 : UiImages.texture(shown);
            if (texture != 0) {
                Color tint = image.imageColor;
                if (image instanceof ImageButton button && button.autoButtonColor && shown.equals(image.image)) {
                    tint = shade(tint, pressed ? 0.25 : hovered ? 0.12 : 0);
                }
                d.image(texture, x, y, w, h, argb(tint, (1 - image.imageTransparency) * alpha));
            }
        }
        if (source instanceof TextBox box) textBox(d, box, alpha, textStroke);
        else if (source instanceof TextLabel label && !label.text.isEmpty()) text(d, label, alpha, textStroke);
    }

    @Override
    public void drawAfterChildren(UiDraw d) {
        if (clipped) d.popClip();
        clipped = false;
        if (source instanceof ScrollingFrame frame && canvas != null) scrollBars(d, frame);
    }

    private boolean autoColored() {
        return source instanceof TextButton button && button.autoButtonColor;
    }

    private void gradient(UiDraw d, UIGradient gradient, Color base, float fill, float radius) {
        List<UIGradient.Keypoint> stops = gradient.stops();
        float[] times = new float[stops.size()];
        int[] colours = new int[stops.size()];
        for (int n = 0; n < stops.size(); n++) {
            UIGradient.Keypoint stop = stops.get(n);
            Color c = stop.color();
            times[n] = (float) stop.time();
            colours[n] = argb(new Color(c.r() * base.r(), c.g() * base.g(), c.b() * base.b(), base.a()), fill * (1 - stop.transparency()));
        }
        d.gradient(x, y, w, h, radius, 0, times, colours, (float) Math.toRadians(gradient.rotation),
                (float) gradient.offset.x(), (float) gradient.offset.y());
    }

    private void scrollBars(UiDraw d, ScrollingFrame frame) {
        float thickness = (float) frame.scrollBarThickness;
        int colour = argb(frame.scrollBarImageColor, (1 - frame.scrollBarImageTransparency) * alpha);
        if (thickness <= 0 || (colour >>> 24) == 0) return;
        Vector3 at = frame.clamp(frame.canvasPosition, canvas.w(), canvas.h(), w, h);
        if (frame.scrollingDirection.y() && canvas.h() > h + 0.5) {
            float length = Math.max(thickness * 2, h * h / (float) canvas.h());
            float top = y + (h - length) * (float) (at.y() / (canvas.h() - h));
            d.roundedRect(x + w - thickness, top, thickness, length, thickness * 0.5f, colour);
        }
        if (frame.scrollingDirection.x() && canvas.w() > w + 0.5) {
            float length = Math.max(thickness * 2, w * w / (float) canvas.w());
            float left = x + (w - length) * (float) (at.x() / (canvas.w() - w));
            d.roundedRect(left, y + h - thickness, length, thickness, thickness * 0.5f, colour);
        }
    }

    private void text(UiDraw d, TextLabel label, float alpha, UIStroke stroke) {
        if (label.richText) {
            rich(d, label, alpha, stroke);
            return;
        }
        int colour = argb(label.textColor, (1 - label.textTransparency) * alpha);
        if ((colour >>> 24) == 0) return;
        UiFonts.Face face = UiFonts.of(GuiLayout.font(label));
        Identifier previous = d.currentFont();
        if (face instanceof UiFonts.Vector vector) d.font(vector.font());
        float px = label.textScaled ? scaled(face, label.text, (float) label.textSize) : (float) label.textSize;
        List<UiText.Line> lines = UiText.lines(face, label.text, px, GuiLayout.wraps(label) ? w : Double.POSITIVE_INFINITY);
        float line = UiText.lineHeight(face, px);
        float top = top(label, line * lines.size());
        int outline = stroke == null ? 0 : argb(stroke.color, (1 - stroke.transparency) * (1 - label.textTransparency) * alpha);
        for (int n = 0; n < lines.size(); n++) {
            String one = label.text.substring(lines.get(n).start(), lines.get(n).end());
            float left = left(label, UiText.width(face, one, px));
            run(d, face, one, left, top + line * n, px, colour, label.textShadow, outline, stroke == null ? 0 : (float) stroke.thickness);
        }
        if (previous != null) d.font(previous);
    }

    private void run(UiDraw d, UiFonts.Face face, String text, float left, float top, float px, int colour, boolean shadow,
                     int outline, float thickness) {
        if ((outline >>> 24) != 0 && thickness > 0) {
            for (int ox = -1; ox <= 1; ox++) {
                for (int oy = -1; oy <= 1; oy++) {
                    if (ox == 0 && oy == 0) continue;
                    plain(d, face, text, left + ox * thickness, top + oy * thickness, px, outline, false);
                }
            }
        }
        plain(d, face, text, left, top, px, colour, shadow);
    }

    private static void plain(UiDraw d, UiFonts.Face face, String text, float left, float top, float px, int colour, boolean shadow) {
        switch (face) {
            case UiFonts.Game game -> GameText.draw(d, game.font(), text, left, top, px, colour, shadow);
            case UiFonts.Vector ignored -> {
                if (shadow) {
                    int dark = (colour & 0xFF000000) | ((colour & 0xFCFCFC) >> 2);
                    d.text(text, left + px / GameText.LINE, top + px / GameText.LINE, px, dark);
                }
                d.text(text, left, top, px, colour);
            }
        }
    }

    private float top(TextLabel label, float total) {
        return switch (label.textYAlignment) {
            case TOP -> y;
            case CENTER -> y + (h - total) * 0.5f;
            case BOTTOM -> y + h - total;
        };
    }

    private float left(TextLabel label, float width) {
        return switch (label.textXAlignment) {
            case LEFT -> x;
            case CENTER -> x + (w - width) * 0.5f;
            case RIGHT -> x + w - width;
        };
    }

    private float scaled(UiFonts.Face face, String text, float base) {
        String[] lines = text.split("\n", -1);
        float widest = 0;
        for (String one : lines) widest = Math.max(widest, UiText.width(face, one, base));
        float line = UiText.lineHeight(face, base);
        float byHeight = h / (line * lines.length) * base;
        float byWidth = widest <= 0 ? byHeight : w / widest * base;
        return Math.max(1, Math.min(byHeight, byWidth));
    }

    private void rich(UiDraw d, TextLabel label, float alpha, UIStroke stroke) {
        float px = (float) label.textSize;
        float fade = (float) (1 - label.textTransparency) * alpha;
        if (fade <= 0) return;
        String font = GuiLayout.font(label);
        float total = TextLayout.of(d, label.text, w, px, font).height();
        Color strokeColor = stroke == null ? Color.BLACK : stroke.color;
        double strokeAlpha = stroke == null ? 0 : 1 - stroke.transparency;
        TextPainter.draw(d, label.text, x, top(label, total), w, px, new TextLook(label.textColor, font, label.textShadow, strokeColor, strokeAlpha),
                fade, label.textXAlignment);
    }

    private void textBox(UiDraw d, TextBox box, float alpha, UIStroke stroke) {
        UiFonts.Face face = UiFonts.of(GuiLayout.font(box));
        Identifier previous = d.currentFont();
        if (face instanceof UiFonts.Vector vector) d.font(vector.font());
        float px = (float) box.textSize;
        String text = box.text;
        editLine = UiText.lineHeight(face, px);
        editLines = box.multiLine ? UiText.lines(face, text, px, GuiLayout.wraps(box) ? w : Double.POSITIVE_INFINITY)
                : List.of(new UiText.Line(0, text.length()));
        editTop = top(box, editLine * editLines.size());
        editLefts = new float[editLines.size()];
        TextEdit edit = box.edit();
        float full = UiText.width(face, text, px);
        boolean overflow = !box.multiLine && full > w;
        if (!overflow) {
            scrollX = 0;
        } else if (focused) {
            float caretAt = UiText.width(face, text.substring(0, edit.caret()), px);
            if (caretAt - scrollX > w - 1) scrollX = caretAt - w + 1;
            if (caretAt - scrollX < 0) scrollX = caretAt;
            scrollX = Math.clamp(scrollX, 0, Math.max(0, full - w + 1));
        }
        int colour = argb(box.textColor, (1 - box.textTransparency) * alpha);
        int outline = stroke == null ? 0 : argb(stroke.color, (1 - stroke.transparency) * (1 - box.textTransparency) * alpha);
        float thickness = stroke == null ? 0 : (float) stroke.thickness;
        d.pushClip(x, y, w, h);
        if (text.isEmpty() && !box.placeholderText.isEmpty()) {
            String hint = box.placeholderText;
            run(d, face, hint, left(box, UiText.width(face, hint, px)), editTop, px,
                    argb(box.placeholderColor, (1 - box.textTransparency) * alpha), false, 0, 0);
        }
        boolean blink = focused && ((System.nanoTime() - blinkFrom) / BLINK_NANOS) % 2 == 0;
        for (int n = 0; n < editLines.size(); n++) {
            UiText.Line line = editLines.get(n);
            String one = text.substring(line.start(), line.end());
            float left = overflow ? x - scrollX : left(box, UiText.width(face, one, px));
            editLefts[n] = left;
            float top = editTop + editLine * n;
            if (focused && edit.hasSelection()) {
                int from = Math.max(edit.start(), line.start());
                int to = Math.min(edit.end(), line.end());
                if (from < to) {
                    float start = left + UiText.width(face, text.substring(line.start(), from), px);
                    float span = UiText.width(face, text.substring(from, to), px);
                    d.rect(start, top, span, editLine, fade(SELECTION, alpha));
                }
            }
            if (!one.isEmpty()) run(d, face, one, left, top, px, colour, box.textShadow, outline, thickness);
            boolean here = edit.caret() >= line.start() && edit.caret() <= line.end()
                    && (n == editLines.size() - 1 || edit.caret() < editLines.get(n + 1).start());
            if (blink && here) {
                float at = left + UiText.width(face, text.substring(line.start(), edit.caret()), px);
                d.rect(at, top, Math.max(1, px / GameText.LINE), editLine, colour);
            }
        }
        d.popClip();
        if (previous != null) d.font(previous);
    }

    private int indexAt(TextBox box, float mx, float my) {
        if (box.text.isEmpty() || editLines.isEmpty() || editLefts.length != editLines.size()) return box.text.length();
        int n = Math.clamp((int) Math.floor((my - editTop) / editLine), 0, editLines.size() - 1);
        UiText.Line line = editLines.get(n);
        UiFonts.Face face = UiFonts.of(GuiLayout.font(box));
        float px = (float) box.textSize;
        float left = editLefts[n];
        int best = Math.min(line.start(), box.text.length());
        float nearest = Math.abs(mx - left);
        for (int at = best; at < Math.min(line.end(), box.text.length()); ) {
            int next = at + Character.charCount(box.text.codePointAt(at));
            float pen = left + UiText.width(face, box.text.substring(line.start(), next), px);
            if (Math.abs(mx - pen) < nearest) {
                nearest = Math.abs(mx - pen);
                best = next;
            }
            at = next;
        }
        return best;
    }

    private void apply(TextBox box, TextEdit next) {
        if (!box.isAlive()) return;
        if (!next.text().equals(box.text)) {
            Instances.setObj(box, box.def().property("text"), next.text());
        }
        blinkFrom = System.nanoTime();
        Instances.setNum(box, box.def().property("cursorPosition"), next.caret() + 1);
        Instances.setNum(box, box.def().property("selectionStart"), next.hasSelection() ? next.anchor() + 1 : -1);
    }

    private static Color shade(Color c, double amount) {
        float keep = (float) (1 - amount);
        return new Color(c.r() * keep, c.g() * keep, c.b() * keep, c.a());
    }

    static int argb(Color c, double alpha) {
        int a = (int) Math.round(Math.clamp(c.a() * alpha, 0, 1) * 255);
        int r = Math.round(Math.clamp(c.r(), 0f, 1f) * 255);
        int g = Math.round(Math.clamp(c.g(), 0f, 1f) * 255);
        int b = Math.round(Math.clamp(c.b(), 0f, 1f) * 255);
        return a << 24 | r << 16 | g << 8 | b;
    }

    boolean onScreen() {
        for (Instance at = source.parent(); at != null; at = at.parent()) {
            if (at instanceof ScreenGui) return true;
            if (at instanceof SurfaceGui || at instanceof BillboardGui) return false;
        }
        return false;
    }

    @Override
    protected boolean interactive() {
        return source instanceof TextButton || source instanceof ImageButton || source instanceof TextBox
                || source instanceof ScrollingFrame frame && frame.scrollingEnabled;
    }

    @Override
    protected boolean focusable() {
        return source instanceof TextBox;
    }

    @Override
    public boolean onMouseDown(float surfaceX, float surfaceY, int button) {
        float mx = local(surfaceX, offsetX);
        float my = local(surfaceY, offsetY);
        return switch (source) {
            case TextButton ignored -> button == 0;
            case ImageButton ignored -> button == 0;
            case TextBox box -> {
                if (button == 0) pressText(box, mx, my);
                yield true;
            }
            case ScrollingFrame frame when frame.scrollingEnabled && button == 0 && canvas != null -> {
                pressX = mx;
                pressY = my;
                pressCanvas = frame.clamp(frame.canvasPosition, canvas.w(), canvas.h(), w, h);
                draggingThumb = (frame.scrollingDirection.y() && mx >= x + w - frame.scrollBarThickness && canvas.h() > h)
                        || (frame.scrollingDirection.x() && my >= y + h - frame.scrollBarThickness && canvas.w() > w);
                yield true;
            }
            default -> false;
        };
    }

    private void pressText(TextBox box, float mx, float my) {
        int at = indexAt(box, mx, my);
        long now = System.nanoTime();
        TextEdit edit = box.edit();
        if (now - lastClick < DOUBLE_CLICK_NANOS && at == edit.caret() && !edit.hasSelection()) {
            apply(box, edit.word(at));
            lastClick = 0;
            return;
        }
        lastClick = now;
        apply(box, edit.move(at, shiftDown()));
    }

    @Override
    public void onMouseDrag(float surfaceX, float surfaceY) {
        float mx = local(surfaceX, offsetX);
        float my = local(surfaceY, offsetY);
        switch (source) {
            case TextBox box -> apply(box, box.edit().move(indexAt(box, mx, my), true));
            case ScrollingFrame frame when frame.scrollingEnabled && canvas != null -> {
                double dx = mx - pressX;
                double dy = my - pressY;
                Vector3 wanted = draggingThumb
                        ? new Vector3(pressCanvas.x() + dx / Math.max(1, w) * canvas.w(), pressCanvas.y() + dy / Math.max(1, h) * canvas.h(), 0)
                        : new Vector3(pressCanvas.x() - dx, pressCanvas.y() - dy, 0);
                scrollTo(frame, wanted);
            }
            default -> { }
        }
    }

    @Override
    public void onMouseUp(float surfaceX, float surfaceY, int button) {
        float mx = local(surfaceX, offsetX);
        float my = local(surfaceY, offsetY);
        if (mx < x || my < y || mx > x + w || my > y + h) return;
        switch (source) {
            case TextButton clicked when clicked.isAlive() -> clicked.activated.fire(clicked);
            case ImageButton clicked when clicked.isAlive() -> clicked.activated.fire(clicked);
            default -> { }
        }
    }

    @Override
    public boolean onScroll(float amount) {
        if (!(source instanceof ScrollingFrame frame) || !frame.scrollingEnabled || canvas == null) return false;
        Vector3 at = frame.clamp(frame.canvasPosition, canvas.w(), canvas.h(), w, h);
        boolean vertical = frame.scrollingDirection.y() && canvas.h() > h;
        boolean horizontal = frame.scrollingDirection.x() && canvas.w() > w;
        if (!vertical && !horizontal) return false;
        double step = amount * WHEEL_STEP;
        scrollTo(frame, vertical ? new Vector3(at.x(), at.y() - step, 0) : new Vector3(at.x() - step, at.y(), 0));
        return true;
    }

    private void scrollTo(ScrollingFrame frame, Vector3 wanted) {
        if (!frame.isAlive()) return;
        Vector3 clamped = frame.clamp(wanted, canvas.w(), canvas.h(), w, h);
        Instances.setObj(frame, frame.def().property("canvasPosition"), clamped);
    }

    @Override
    public void onFocusGained() {
        if (!(source instanceof TextBox box) || !box.isAlive()) return;
        KeyMapping.releaseAll();
        if (box.clearTextOnFocus && box.textEditable) Instances.setObj(box, box.def().property("text"), "");
        apply(box, TextEdit.at(box.text, box.text.length()));
        Ui.focusMoved(this);
        box.gainedFocus();
    }

    @Override
    public void onFocusLost() {
        if (!(source instanceof TextBox box) || !box.isAlive()) return;
        boolean enter = enterPressed;
        enterPressed = false;
        Instances.setNum(box, box.def().property("cursorPosition"), -1);
        Instances.setNum(box, box.def().property("selectionStart"), -1);
        box.lostFocus(enter);
    }

    void blur(boolean enter) {
        enterPressed = enter;
        Ui.blur(this);
    }

    @Override
    public boolean onChar(int codepoint) {
        if (!(source instanceof TextBox box) || !box.isAlive()) return false;
        if (!box.textEditable || codepoint < 32 || codepoint == 127) return true;
        apply(box, box.edit().insert(Character.toString(codepoint), box.multiLine));
        return true;
    }

    @Override
    public boolean onKey(int key, int modifiers) {
        if (!(source instanceof TextBox box) || !box.isAlive()) return false;
        boolean shortcut = (modifiers & InputQuirks.EDIT_SHORTCUT_KEY_MODIFIER) != 0;
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        boolean word = (modifiers & (InputQuirks.REPLACE_CTRL_KEY_WITH_CMD_KEY ? GLFW.GLFW_MOD_ALT : GLFW.GLFW_MOD_CONTROL)) != 0;
        boolean editable = box.textEditable;
        TextEdit edit = box.edit();
        switch (key) {
            case GLFW.GLFW_KEY_ESCAPE -> blur(false);
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                if (box.multiLine && editable) apply(box, edit.insert("\n", true));
                else blur(true);
            }
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (editable) apply(box, edit.backspace(word));
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (editable) apply(box, edit.delete(word));
            }
            case GLFW.GLFW_KEY_LEFT -> apply(box, edit.left(word, shift));
            case GLFW.GLFW_KEY_RIGHT -> apply(box, edit.right(word, shift));
            case GLFW.GLFW_KEY_UP -> apply(box, edit.up(shift));
            case GLFW.GLFW_KEY_DOWN -> apply(box, edit.down(shift));
            case GLFW.GLFW_KEY_HOME -> apply(box, shortcut ? edit.move(0, shift) : edit.home(shift));
            case GLFW.GLFW_KEY_END -> apply(box, shortcut ? edit.move(box.text.length(), shift) : edit.end(shift));
            case GLFW.GLFW_KEY_A -> {
                if (shortcut) apply(box, edit.all());
            }
            case GLFW.GLFW_KEY_C -> {
                if (shortcut && edit.hasSelection()) Minecraft.getInstance().keyboardHandler.setClipboard(edit.selected());
            }
            case GLFW.GLFW_KEY_X -> {
                if (shortcut && edit.hasSelection()) {
                    Minecraft.getInstance().keyboardHandler.setClipboard(edit.selected());
                    if (editable) apply(box, edit.insert("", true));
                }
            }
            case GLFW.GLFW_KEY_V -> {
                if (shortcut && editable) apply(box, edit.insert(Minecraft.getInstance().keyboardHandler.getClipboard(), box.multiLine));
            }
            default -> { }
        }
        return true;
    }

    private static boolean shiftDown() {
        Window window = Minecraft.getInstance().getWindow();
        return InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_SHIFT) || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    static boolean surface(Instance instance) {
        return instance instanceof ScreenGui || instance instanceof BillboardGui || instance instanceof SurfaceGui;
    }
}
