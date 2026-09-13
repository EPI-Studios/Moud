package com.meekdev.moud.mod.adapter.chat;

import com.meekdev.amnetic.client.surface.Surfaces;
import com.meekdev.amnetic.client.surface.WorldSurface;
import com.meekdev.amnetic.client.surface.draw.UiDraw;
import com.meekdev.amnetic.client.surface.widget.Widget;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.BubbleChat;
import com.meekdev.moud.core.instance.ChatAnimation;
import com.meekdev.moud.core.instance.Character;
import com.meekdev.moud.core.instance.HorizontalAlign;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Part;
import com.meekdev.moud.core.instance.Spatial;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.tween.Easing;
import com.meekdev.moud.mod.client.ClientScene;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

public final class Bubbles {

    private static final class Bubble {
        final String markup;
        final Map<String, Object> look;
        final long added = System.nanoTime();

        Bubble(String markup, Map<String, Object> look) {
            this.markup = markup;
            this.look = look;
        }
    }

    private static final class Stack extends Widget {
        final Instance target;
        final List<Bubble> bubbles = new ArrayList<>();
        WorldSurface surface;
        float canvasW;
        float canvasH;

        Stack(Instance target) {
            this.target = target;
        }

        @Override
        protected void drawSelf(UiDraw d, float alpha) {
            BubbleChat config = config();
            if (config == null) return;
            long now = System.nanoTime();
            float px = (float) config.textSize;
            float pad = (float) config.padding;
            float bottom = y + h;
            for (int n = bubbles.size() - 1; n >= 0; n--) {
                Bubble bubble = bubbles.get(n);
                double age = (now - bubble.added) / 1e9;
                double life = ChatViewNumbers.number(bubble.look, "visibleTime", config.visibleTime);
                float a = alpha;
                if (age > life) a *= (float) Math.max(0, 1 - (age - life) / 0.3);
                double enter = config.animationTime <= 0 ? 1 : Math.min(1, age / config.animationTime);
                double eased = Easing.BACK.apply(enter, Easing.Direction.OUT);
                if (config.animation == ChatAnimation.FADE) a *= (float) enter;
                if (a <= 0.003) continue;

                String font = ChatViewNumbers.string(bubble.look, "font", config.font);
                float[] size = ChatView.measure(d, bubble.markup, (float) config.maxWidth, px, font);
                float bw = size[0] + pad * 2;
                float bh = size[1] + pad * 2;
                float bx = x + (w - bw) / 2;
                float tail = config.tail && n == bubbles.size() - 1 ? px * 0.5f : 0;
                float by = bottom - bh - tail;
                boolean pop = config.animation == ChatAnimation.POP && enter < 1;
                if (pop) d.pushTransform(bx + bw / 2, by + bh, 0, 0, (float) Math.max(0.01, eased), 0);

                Color back = ChatViewNumbers.color(bubble.look, "backgroundColor", config.backgroundColor);
                double transparency = ChatViewNumbers.number(bubble.look, "backgroundTransparency", config.backgroundTransparency);
                int backArgb = ChatView.argbOf(back, (1 - transparency) * a);
                d.roundedRect(bx, by, bw, bh, (float) config.cornerRadius, backArgb);
                if (tail > 0) {
                    float side = tail * 1.2f;
                    d.pushTransform(bx + bw / 2, by + bh, 0, 0, 1, (float) (Math.PI / 4));
                    d.rect(bx + bw / 2 - side / 2, by + bh - side / 2, side, side, backArgb);
                    d.popTransform();
                }
                ChatView.Look look = new ChatView.Look(ChatViewNumbers.color(bubble.look, "textColor", config.textColor),
                        font, false, Color.BLACK, 0);
                ChatView.text(d, bubble.markup, bx + pad, by + pad, size[0], px, look, a, HorizontalAlign.CENTER);
                if (pop) d.popTransform();
                bottom = by - pad;
            }
        }
    }

    private static final Map<Instance, Stack> STACKS = new HashMap<>();

    private Bubbles() {}

    static @Nullable BubbleChat config() {
        InstanceTree tree = ClientScene.tree();
        if (tree == null) return null;
        List<BubbleChat> all = tree.ofClass(Classes.BUBBLE_CHAT);
        return all.isEmpty() ? null : all.getFirst();
    }

    public static void add(Instance target, String markup, Map<String, Object> look) {
        BubbleChat config = config();
        if (config == null || !config.enabled || !(target instanceof Spatial)) return;
        Stack stack = STACKS.computeIfAbsent(target, Stack::new);
        stack.bubbles.add(new Bubble(markup, look));
        while (stack.bubbles.size() > config.maxBubbles) stack.bubbles.removeFirst();
    }

    public static void frame(float partialTick) {
        BubbleChat config = config();
        long now = System.nanoTime();
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        for (Iterator<Map.Entry<Instance, Stack>> it = STACKS.entrySet().iterator(); it.hasNext(); ) {
            Stack stack = it.next().getValue();
            stack.bubbles.removeIf(b -> config == null
                    || (now - b.added) / 1e9 > ChatViewNumbers.number(b.look, "visibleTime", config.visibleTime) + 0.3);
            if (config == null || !config.enabled || stack.bubbles.isEmpty() || !stack.target.isAlive()
                    || stack.target.tree() != ClientScene.tree()) {
                if (stack.surface != null) stack.surface.remove();
                it.remove();
                continue;
            }
            float ppm = (float) config.pixelsPerMetre;
            float canvasW = (float) (config.maxWidth + config.padding * 2);
            float canvasH = (float) (config.maxBubbles * (config.textSize * 5 + config.padding * 3) + config.textSize);
            if (stack.surface == null || stack.canvasW != canvasW || stack.canvasH != canvasH) {
                if (stack.surface != null) stack.surface.remove();
                stack.surface = Surfaces.world(canvasW / ppm, canvasH / ppm).resolution(Math.round(ppm)).direct(true);
                stack.surface.root().add(stack);
                stack.canvasW = canvasW;
                stack.canvasH = canvasH;
            }
            Vector3 at = ClientScene.motion().sample(stack.target, partialTick).position();
            double top = switch (stack.target) {
                case Character body -> body.height * body.scale;
                case Part part -> part.size.y() / 2;
                default -> 0;
            };
            Vector3fc left = camera.leftVector();
            Vector3fc up = camera.upVector();
            double lift = top + config.offset.y() + canvasH / ppm / 2;
            stack.surface.at(at.x() + config.offset.x(), at.y() + lift, at.z() + config.offset.z())
                    .orient(-left.x(), -left.y(), -left.z(), up.x(), up.y(), up.z())
                    .alwaysOnTop(config.alwaysOnTop)
                    .maxDistance(config.maxDistance <= 0 ? Float.MAX_VALUE : (float) config.maxDistance)
                    .setVisible(true);
        }
    }

    public static void clear() {
        for (Stack stack : STACKS.values()) {
            if (stack.surface != null) stack.surface.remove();
        }
        STACKS.clear();
    }
}
