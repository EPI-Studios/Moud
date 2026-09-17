package com.meekdev.moud.mod.client.input;

import com.meekdev.amnetic.client.camera.AmneticCamera;
import com.meekdev.amnetic.client.camera.Ray;
import com.meekdev.moud.core.character.Character;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.input.ClickDetector;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Transforms;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.query.Queries;
import com.meekdev.moud.core.ui.ViewportFrame;
import com.meekdev.moud.mod.client.ClientPlace;
import com.meekdev.moud.mod.client.ClientScene;
import com.meekdev.moud.mod.client.WindowApi;
import com.meekdev.moud.mod.server.input.ServerClicks;
import com.meekdev.moud.mod.transport.payload.ClickPayload;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

public final class ClickDetectors {

    private static final double REACH = 512;

    private static @Nullable ClickDetector hovered;
    private static @Nullable ClickDetector left;
    private static @Nullable ClickDetector right;

    private ClickDetectors() {}

    public static void frame() {
        ClickDetector next = Devices.processed() ? null : find();
        if (hovered != null && !hovered.isAlive()) hovered = null;
        if (next != hovered) {
            if (hovered != null) fire(hovered, ClickPayload.HOVER_LEAVE);
            hovered = next;
            if (next != null) fire(next, ClickPayload.HOVER_ENTER);
        }
        boolean free = !Minecraft.getInstance().mouseHandler.isMouseGrabbed();
        WindowApi.INSTANCE.hover(hovered != null && free ? hovered.cursorIcon.isEmpty() ? "hand" : hovered.cursorIcon : null);
    }

    public static boolean button(int button, boolean pressed) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT && button != GLFW.GLFW_MOUSE_BUTTON_RIGHT) return false;
        boolean primary = button == GLFW.GLFW_MOUSE_BUTTON_LEFT;
        if (pressed) {
            if (primary) left = hovered;
            else right = hovered;
            return hovered != null;
        }
        ClickDetector down = primary ? left : right;
        if (primary) left = null;
        else right = null;
        if (down != null && down == hovered && down.isAlive()) fire(down, primary ? ClickPayload.CLICK : ClickPayload.RIGHT_CLICK);
        return down != null;
    }

    private static void fire(ClickDetector detector, int kind) {
        Minecraft client = Minecraft.getInstance();
        if (!detector.isAlive() || client.player == null) return;
        ClickDetector.Clicker clicker = new ClickDetector.Clicker(client.player.getUUID().toString());
        switch (kind) {
            case ClickPayload.CLICK -> detector.mouseClick.fire(clicker);
            case ClickPayload.RIGHT_CLICK -> detector.rightMouseClick.fire(clicker);
            case ClickPayload.HOVER_ENTER -> detector.mouseHoverEnter.fire(clicker);
            default -> detector.mouseHoverLeave.fire(clicker);
        }
        if (detector.id() >= 0 && ClientPlayNetworking.canSend(ClickPayload.TYPE)) {
            ClientPlayNetworking.send(new ClickPayload(detector.id(), kind));
        }
    }

    private static @Nullable ClickDetector find() {
        InstanceTree tree = ClientScene.tree();
        Minecraft client = Minecraft.getInstance();
        if (tree == null || client.player == null || client.level == null || !AmneticCamera.isReady()) return null;
        List<ClickDetector> detectors = tree.ofClass(Classes.CLICK_DETECTOR);
        if (detectors.isEmpty()) return null;
        boolean grabbed = client.mouseHandler.isMouseGrabbed();
        double x = grabbed ? client.getWindow().getGuiScaledWidth() / 2.0 : ClientPlace.input().mouseX();
        double y = grabbed ? client.getWindow().getGuiScaledHeight() / 2.0 : ClientPlace.input().mouseY();
        Ray ray = AmneticCamera.screenToRay(x, y);
        Vector3 from = new Vector3(ray.origin().x(), ray.origin().y(), ray.origin().z());
        Vector3 way = new Vector3(ray.direction().x(), ray.direction().y(), ray.direction().z());
        Character me = ClientScene.own();
        Queries.Filter filter = new Queries.Filter(me == null ? List.of() : List.of(me), false, false);
        Queries.Cast cast = Queries.raycast(tree.root(), from, way, REACH, filter);
        if (cast == null || blocked(client, from, cast.at())) return null;
        ClickDetector detector = detectorOf(cast.part());
        if (detector == null || ViewportFrame.inside(detector)) return null;
        Vector3 origin = me != null ? Transforms.world(me).position() : new Vector3(client.player.getX(), client.player.getEyeY(), client.player.getZ());
        return ServerClicks.distance(detector, origin) <= detector.maxActivationDistance ? detector : null;
    }

    private static @Nullable ClickDetector detectorOf(Instance part) {
        for (Instance at = part; at != null && at.parent() != null; at = at.parent()) {
            for (Instance child : at.children()) {
                if (child instanceof ClickDetector detector) return detector;
            }
        }
        return null;
    }

    private static boolean blocked(Minecraft client, Vector3 from, Vector3 to) {
        HitResult hit = client.level.clip(new ClipContext(new Vec3(from.x(), from.y(), from.z()), new Vec3(to.x(), to.y(), to.z()),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, CollisionContext.empty()));
        return hit.getType() != HitResult.Type.MISS;
    }
}
