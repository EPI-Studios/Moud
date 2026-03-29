package com.moud.client.fabric.render.hud;

import com.moud.net.protocol.SceneSnapshot;

import java.util.ArrayList;
import java.util.List;

public final class LayoutComputer {

    private LayoutComputer() {}

    public static boolean isLayoutContainer(String type) {
        return "HBoxContainer".equals(type)
                || "VBoxContainer".equals(type)
                || "GridContainer".equals(type);
    }

    public static List<ChildRect> compute(SceneSnapshot.NodeSnapshot container,
                                          List<SceneSnapshot.NodeSnapshot> children,
                                          int cx, int cy, int cw, int ch) {
        return switch (container.type()) {
            case "HBoxContainer" -> hbox(container, children, cx, cy, ch);
            case "VBoxContainer" -> vbox(container, children, cx, cy, cw);
            case "GridContainer" -> grid(container, children, cx, cy, cw);
            default -> fallback(children, cx, cy, cw, ch);
        };
    }


    private static List<ChildRect> hbox(SceneSnapshot.NodeSnapshot container,
                                        List<SceneSnapshot.NodeSnapshot> children,
                                        int cx, int cy, int ch) {
        float sep = ControlRenderContext.floatProp(container, "separation", 4f);
        List<ChildRect> result = new ArrayList<>(children.size());
        int cursor = cx;
        for (SceneSnapshot.NodeSnapshot child : children) {
            int w = Math.max(1, (int)(ControlRenderContext.floatProp(child, "w", 100f)
                    * ControlRenderContext.floatProp(child, "sx", 1f)));
            int h = Math.max(1, (int)(ControlRenderContext.floatProp(child, "h", 30f)
                    * ControlRenderContext.floatProp(child, "sy", 1f)));
            result.add(new ChildRect(cursor, cy, w, h));
            cursor += w + (int) sep;
        }
        return result;
    }


    private static List<ChildRect> vbox(SceneSnapshot.NodeSnapshot container,
                                        List<SceneSnapshot.NodeSnapshot> children,
                                        int cx, int cy, int cw) {
        float sep = ControlRenderContext.floatProp(container, "separation", 4f);
        List<ChildRect> result = new ArrayList<>(children.size());
        int cursor = cy;
        for (SceneSnapshot.NodeSnapshot child : children) {
            int w = Math.max(1, (int)(ControlRenderContext.floatProp(child, "w", 100f)
                    * ControlRenderContext.floatProp(child, "sx", 1f)));
            int h = Math.max(1, (int)(ControlRenderContext.floatProp(child, "h", 30f)
                    * ControlRenderContext.floatProp(child, "sy", 1f)));
            result.add(new ChildRect(cx, cursor, w, h));
            cursor += h + (int) sep;
        }
        return result;
    }


    private static List<ChildRect> grid(SceneSnapshot.NodeSnapshot container,
                                        List<SceneSnapshot.NodeSnapshot> children,
                                        int cx, int cy, int cw) {
        int cols = Math.max(1, (int) ControlRenderContext.floatProp(container, "columns", 2f));
        float hSep = ControlRenderContext.floatProp(container, "h_separation", 4f);
        float vSep = ControlRenderContext.floatProp(container, "v_separation", 4f);

        List<ChildRect> result = new ArrayList<>(children.size());
        int col = 0, row = 0;
        int rowH = 0;
        int cursorY = cy;

        int cellW = cols > 0 ? (cw - (int)(hSep * (cols - 1))) / cols : cw;
        cellW = Math.max(1, cellW);

        for (SceneSnapshot.NodeSnapshot child : children) {
            int h = Math.max(1, (int)(ControlRenderContext.floatProp(child, "h", 30f)
                    * ControlRenderContext.floatProp(child, "sy", 1f)));
            rowH = Math.max(rowH, h);
            int x = cx + col * (cellW + (int) hSep);
            result.add(new ChildRect(x, cursorY, cellW, h));
            col++;
            if (col >= cols) {
                col = 0;
                cursorY += rowH + (int) vSep;
                rowH = 0;
            }
        }
        return result;
    }


    private static List<ChildRect> fallback(List<SceneSnapshot.NodeSnapshot> children,
                                            int cx, int cy, int cw, int ch) {
        List<ChildRect> result = new ArrayList<>(children.size());
        for (SceneSnapshot.NodeSnapshot ignored : children) {
            result.add(null);
        }
        return result;
    }


    public record ChildRect(int x, int y, int w, int h) {}
}