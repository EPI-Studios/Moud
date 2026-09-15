package com.meekdev.moud.mod.adapter.render;

import com.meekdev.moud.core.character.Character;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.part.Part;
import com.meekdev.moud.mod.adapter.gl.RenderState;

public final class EditorView {

    public static final float GHOST_ALPHA = 0.28f;

    private static volatile boolean wireframe;
    private static volatile boolean ghosts;
    private static boolean level;

    private EditorView() {}

    public static boolean wireframe() {
        return wireframe;
    }

    public static void wireframe(boolean on) {
        wireframe = on;
    }

    public static void level(boolean inside) {
        level = inside;
    }

    public static boolean lines() {
        return level && wireframe;
    }

    public static void filled(Runnable draw) {
        if (!lines()) {
            draw.run();
            return;
        }
        RenderState.polygonLines(false);
        try {
            draw.run();
        } finally {
            RenderState.polygonLines(true);
        }
    }

    public static boolean ghosts() {
        return ghosts;
    }

    public static void ghosts(boolean on) {
        ghosts = on;
    }

    public static boolean ghost(Part part) {
        if (!ghosts || part.id() < 0 || (part.visible && part.transparency < 1.0)) return false;
        for (Instance at = part.parent(); at != null; at = at.parent()) {
            if (at instanceof Character) return false;
        }
        return true;
    }
}
