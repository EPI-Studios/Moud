package com.meekdev.moud.mod.client;

import com.meekdev.amnetic.client.surface.Surfaces;
import com.meekdev.amnetic.client.surface.WorldSurface;
import com.meekdev.amnetic.client.surface.draw.UiDraw;
import com.meekdev.amnetic.client.surface.widget.Widget;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.Character;
import com.meekdev.moud.core.instance.HorizontalAlign;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.ProximityPrompt;
import com.meekdev.moud.core.instance.Queries;
import com.meekdev.moud.core.instance.Transforms;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Vec3;
import com.meekdev.moud.core.text.RichText;
import com.meekdev.moud.mod.adapter.chat.ChatView;
import com.meekdev.moud.mod.server.ServerPrompts;
import com.meekdev.moud.mod.transport.Packets;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.joml.Vector3fc;

public final class ClientPrompts {

    private static final float WIDTH = 160;
    private static final float HEIGHT = 40;
    private static final float PPM = 80;

    private static ProximityPrompt active;
    private static double held;
    private static boolean holding;
    private static boolean wasDown;
    private static WorldSurface surface;

    private static final class Label extends Widget {
        @Override
        protected void drawSelf(UiDraw d, float alpha) {
            ProximityPrompt prompt = active;
            if (prompt == null) return;
            d.roundedRect(x, y, w, h, 6, ChatView.argbOf(prompt.backgroundColor, 1 - prompt.backgroundTransparency));
            float key = h - 12;
            d.roundedRect(x + 6, y + 6, key, key, 4, ChatView.argbOf(prompt.keyColor, 0.9));
            String letter = prompt.keys.split(",")[0].trim().toUpperCase(Locale.ROOT);
            ChatView.Look dark = new ChatView.Look(new Color(0.05f, 0.05f, 0.07f, 1), "", false, Color.BLACK, 0);
            ChatView.text(d, RichText.escape(letter), x + 6, y + 6 + (key - 9) / 2, key, 9, dark, 1, HorizontalAlign.CENTER);
            ChatView.Look light = new ChatView.Look(prompt.textColor, "", true, Color.BLACK, 0);
            float textX = x + key + 14;
            float textW = w - key - 20;
            if (!prompt.objectText.isEmpty()) {
                ChatView.text(d, "<alpha=0.7>" + RichText.escape(prompt.objectText) + "</alpha>", textX, y + 7, textW, 9, light, 1, HorizontalAlign.LEFT);
            }
            float actionY = prompt.objectText.isEmpty() ? y + (h - 9) / 2 : y + 21;
            ChatView.text(d, RichText.escape(prompt.actionText), textX, actionY, textW, 9, light, 1, HorizontalAlign.LEFT);
            if (prompt.holdDuration > 0 && held > 0) {
                float fill = (float) Math.min(1, held / prompt.holdDuration);
                d.rect(x + 6, y + h - 4, (w - 12) * fill, 2, ChatView.argbOf(prompt.keyColor, 1));
            }
        }
    }

    private ClientPrompts() {}

    public static void tick(double dt) {
        InstanceTree tree = ClientScene.tree();
        Character me = ClientScene.own();
        ProximityPrompt next = tree == null || me == null ? null : closest(tree, me);
        if (next != active) {
            if (active != null) {
                if (holding) send(active, Packets.PromptUp.HOLD_ENDED);
                if (active.isAlive()) active.hidden.fire(active);
            }
            active = next;
            held = 0;
            holding = false;
            wasDown = true;
            if (active != null) active.shown.fire(active);
        }
        if (active == null) return;
        boolean down = Minecraft.getInstance().screen == null && Actions.pressed(active.keys);
        if (active.holdDuration <= 0) {
            if (down && !wasDown) fire(active, me);
        } else if (down) {
            if (!holding) {
                holding = true;
                active.holdBegan.fire(me);
                send(active, Packets.PromptUp.HOLD_BEGAN);
            }
            held += dt;
            if (held >= active.holdDuration) {
                fire(active, me);
                holding = false;
                held = 0;
            }
        } else if (holding) {
            holding = false;
            held = 0;
            active.holdEnded.fire(me);
            send(active, Packets.PromptUp.HOLD_ENDED);
        }
        wasDown = down;
    }

    public static void frame() {
        ProximityPrompt prompt = active;
        if (prompt == null || !prompt.isAlive()) {
            if (surface != null) surface.setVisible(false);
            return;
        }
        if (surface == null) {
            surface = Surfaces.world(WIDTH / PPM, HEIGHT / PPM).resolution(Math.round(PPM)).direct(true);
            surface.root().add(new Label());
        }
        Vec3 at = ServerPrompts.position(prompt);
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vector3fc left = camera.leftVector();
        Vector3fc up = camera.upVector();
        surface.at(at.x(), at.y(), at.z()).orient(-left.x(), -left.y(), -left.z(), up.x(), up.y(), up.z())
                .alwaysOnTop(true).setVisible(true);
    }

    private static void fire(ProximityPrompt prompt, Character me) {
        prompt.triggered.fire(me);
        send(prompt, Packets.PromptUp.TRIGGERED);
    }

    private static void send(ProximityPrompt prompt, int kind) {
        if (prompt.id() >= 0) ClientPlayNetworking.send(new Packets.PromptUp(prompt.id(), kind));
    }

    static ProximityPrompt closest(InstanceTree tree, Character me) {
        Vec3 eye = Transforms.world(me).position().add(new Vec3(0, me.height * me.scale * 0.9, 0));
        ProximityPrompt best = null;
        double bestDistance = Double.MAX_VALUE;
        List<ProximityPrompt> prompts = tree.ofClass(Classes.PROXIMITY_PROMPT);
        for (ProximityPrompt prompt : prompts) {
            if (!prompt.enabled || prompt.parent() == null) continue;
            Vec3 at = ServerPrompts.position(prompt);
            double distance = at.distance(eye);
            if (distance > prompt.maxActivationDistance || distance >= bestDistance) continue;
            if (prompt.requiresLineOfSight && !visible(tree, me, prompt, eye, at)) continue;
            best = prompt;
            bestDistance = distance;
        }
        return best;
    }

    private static boolean visible(InstanceTree tree, Character me, ProximityPrompt prompt, Vec3 eye, Vec3 at) {
        Vec3 way = at.sub(eye);
        double length = way.length();
        if (length < 1e-4) return true;
        Queries.Filter filter = new Queries.Filter(List.of(me, prompt.parent()), false, true);
        if (Queries.raycast(tree.root(), eye, way, length, filter) != null) return false;
        var level = Minecraft.getInstance().level;
        if (level == null) return true;
        var hit = level.clip(new ClipContext(new net.minecraft.world.phys.Vec3(eye.x(), eye.y(), eye.z()),
                new net.minecraft.world.phys.Vec3(at.x(), at.y(), at.z()), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, CollisionContext.empty()));
        return hit.getType() == HitResult.Type.MISS;
    }
}
