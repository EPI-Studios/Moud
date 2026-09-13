package com.meekdev.moud.mod.client.debug;

import com.meekdev.amnetic.client.render.CameraSnapshot;
import com.meekdev.amnetic.client.surface.Surfaces;
import com.meekdev.amnetic.client.surface.draw.UiDraw;
import com.meekdev.amnetic.client.ui.Gizmos;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.text.RichText;
import com.meekdev.moud.core.ui.HorizontalAlign;
import com.meekdev.moud.mod.adapter.chat.ChatView;
import com.meekdev.moud.mod.client.ClientScene;
import com.meekdev.moud.mod.transport.payload.DebugPayload;
import com.meekdev.moud.script.api.DebugRef;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import org.joml.Vector4f;

public final class ClientDebug implements DebugRef {

    public static final ClientDebug INSTANCE = new ClientDebug();

    private record Shape(List<Vector3[]> lines, Vector3 label, String text, int argb, long until) {}

    private final List<Shape> shapes = new ArrayList<>();
    private final Map<String, String> watched = new LinkedHashMap<>();
    private final Queue<DebugPayload> incoming = new ConcurrentLinkedQueue<>();
    private final Vector4f scratch = new Vector4f();

    private ClientDebug() {}

    public static void install() {
        ClientPlayNetworking.registerGlobalReceiver(DebugPayload.TYPE, (payload, context) -> INSTANCE.incoming.add(payload));
        Surfaces.hud().onDraw(INSTANCE::draw);
    }

    @Override
    public void line(Vector3 from, Vector3 to, Color color, double seconds) {
        add(List.<Vector3[]>of(new Vector3[] {from, to}), null, null, color, seconds);
    }

    @Override
    public void box(CFrame frame, Vector3 size, Color color, double seconds) {
        Vector3 h = size.mul(0.5);
        Vector3[] c = new Vector3[8];
        int n = 0;
        for (int x = -1; x <= 1; x += 2) for (int y = -1; y <= 1; y += 2) for (int z = -1; z <= 1; z += 2) {
            c[n++] = frame.pointToWorld(new Vector3(h.x() * x, h.y() * y, h.z() * z));
        }
        int[][] edges = {{0, 1}, {2, 3}, {4, 5}, {6, 7}, {0, 2}, {1, 3}, {4, 6}, {5, 7}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
        List<Vector3[]> lines = new ArrayList<>();
        for (int[] e : edges) lines.add(new Vector3[] {c[e[0]], c[e[1]]});
        add(lines, null, null, color, seconds);
    }

    @Override
    public void sphere(Vector3 centre, double radius, Color color, double seconds) {
        List<Vector3[]> lines = new ArrayList<>();
        int segments = 24;
        for (int axis = 0; axis < 3; axis++) {
            for (int i = 0; i < segments; i++) {
                lines.add(new Vector3[] {circle(centre, radius, axis, i, segments), circle(centre, radius, axis, i + 1, segments)});
            }
        }
        add(lines, null, null, color, seconds);
    }

    private static Vector3 circle(Vector3 centre, double radius, int axis, int i, int segments) {
        double a = 2 * Math.PI * i / segments;
        double u = Math.cos(a) * radius, v = Math.sin(a) * radius;
        return centre.add(switch (axis) {
            case 0 -> new Vector3(u, v, 0);
            case 1 -> new Vector3(u, 0, v);
            default -> new Vector3(0, u, v);
        });
    }

    @Override
    public void label(Vector3 at, String text, Color color, double seconds) {
        add(List.of(), at, text, color, seconds);
    }

    @Override
    public void watch(String name, String value) {
        watched.put(name, value);
    }

    public void unwatch(String name) {
        watched.remove(name);
    }

    @Override
    public void clear() {
        shapes.clear();
        watched.clear();
    }

    private void add(List<Vector3[]> lines, Vector3 label, String text, Color color, double seconds) {
        long until = System.nanoTime() + (long) (Math.max(0, seconds) * 1e9);
        shapes.add(new Shape(lines, label, text, ChatView.argbOf(color, 1), seconds <= 0 ? 0 : until));
    }

    private void drain() {
        for (DebugPayload down; (down = incoming.poll()) != null; ) {
            double[] n = down.numbers();
            Color color = new Color(((down.argb() >> 16) & 255) / 255f, ((down.argb() >> 8) & 255) / 255f,
                    (down.argb() & 255) / 255f, ((down.argb() >>> 24) & 255) / 255f);
            switch (down.kind()) {
                case 0 -> line(new Vector3(n[0], n[1], n[2]), new Vector3(n[3], n[4], n[5]), color, down.seconds());
                case 1 -> box(new CFrame(new Vector3(n[0], n[1], n[2]), new Quat(n[3], n[4], n[5], n[6])),
                        new Vector3(n[7], n[8], n[9]), color, down.seconds());
                case 2 -> sphere(new Vector3(n[0], n[1], n[2]), n[3], color, down.seconds());
                case 3 -> label(new Vector3(n[0], n[1], n[2]), down.text(), color, down.seconds());
                case 4 -> {
                    int colon = down.text().indexOf(": ");
                    watch(colon < 0 ? down.text() : down.text().substring(0, colon), colon < 0 ? "" : down.text().substring(colon + 2));
                }
                case 5 -> clear();
                default -> {}
            }
        }
    }

    private void draw(UiDraw d) {
        drain();
        if (ClientScene.tree() == null) {
            shapes.clear();
            watched.clear();
            return;
        }
        CameraSnapshot camera = CameraSnapshot.current();
        long now = System.nanoTime();
        if (camera != null) {
            for (Iterator<Shape> it = shapes.iterator(); it.hasNext(); ) {
                Shape shape = it.next();
                for (Vector3[] segment : shape.lines()) {
                    float[] a = Gizmos.project(camera, scratch, segment[0].x(), segment[0].y(), segment[0].z(), d.width(), d.height());
                    float[] b = Gizmos.project(camera, scratch, segment[1].x(), segment[1].y(), segment[1].z(), d.width(), d.height());
                    if (a == null || b == null) continue;
                    float dx = b[0] - a[0], dy = b[1] - a[1];
                    float length = (float) Math.sqrt(dx * dx + dy * dy);
                    if (length < 0.5f) continue;
                    d.pushTransform(a[0], a[1], 0, 0, 1, (float) Math.atan2(dy, dx));
                    d.rect(a[0], a[1] - 0.5f, length, 1, shape.argb());
                    d.popTransform();
                }
                if (shape.label() != null) {
                    float[] at = Gizmos.project(camera, scratch, shape.label().x(), shape.label().y(), shape.label().z(), d.width(), d.height());
                    if (at != null) {
                        Color c = new Color(((shape.argb() >> 16) & 255) / 255f, ((shape.argb() >> 8) & 255) / 255f, (shape.argb() & 255) / 255f, 1);
                        ChatView.text(d, RichText.escape(shape.text()), at[0] - 100, at[1], 200, 9,
                                new ChatView.Look(c, "", true, Color.BLACK, 0), 1, HorizontalAlign.CENTER);
                    }
                }
                if (shape.until() == 0 || now > shape.until()) it.remove();
            }
        }
        float y = 4;
        for (Map.Entry<String, String> entry : watched.entrySet()) {
            String line = entry.getKey() + ": " + entry.getValue();
            d.rect(d.width() - 204, y - 1, 200, 11, 0x80000000);
            ChatView.text(d, RichText.escape(line), d.width() - 202, y, 196, 9,
                    new ChatView.Look(Color.WHITE, "", true, Color.BLACK, 0), 1, HorizontalAlign.LEFT);
            y += 12;
        }
    }
}
