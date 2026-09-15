package com.meekdev.moud.mod.client.editor.style;

import com.meekdev.moud.mod.MoudMod;
import foundry.imgui.api.ImGuiMC;
import imgui.ImFont;
import imgui.ImFontAtlas;
import imgui.ImFontConfig;
import imgui.ImFontGlyphRangesBuilder;
import java.io.IOException;
import java.io.InputStream;
import org.jspecify.annotations.Nullable;

public final class EditorFonts {

    public static final float BODY = 14.0f;
    public static final float TITLE = 15.0f;
    public static final float SMALL = 13.0f;
    public static final float MONOSPACE = 14.0f;
    public static final float HEADING = 22.0f;
    public static final float DISPLAY = 34.0f;

    private static final String ROOT = "/assets/moud/editor/fonts/";

    private static float baked;
    private static float requested;
    private static @Nullable ImFont body;
    private static @Nullable ImFont small;
    private static @Nullable ImFont heading;
    private static @Nullable ImFont display;
    private static @Nullable ImFont title;
    private static @Nullable ImFont monospace;

    private EditorFonts() {}

    public static void rebuildFor(float factor) {
        if (factor == baked || factor == requested) return;
        requested = factor;
        ImGuiMC.rebuildFonts();
    }

    public static void register(ImFontAtlas atlas) {
        float factor = EditorScaling.target();
        baked = factor;
        requested = factor;
        ImFontConfig config = new ImFontConfig();
        config.setGlyphRanges(ranges(atlas));
        config.setPixelSnapH(true);
        try {
            byte[] regular = read("inter-regular.ttf");
            byte[] semibold = read("inter-semibold.ttf");
            body = atlas.addFontFromMemoryTTF(regular, Math.round(BODY * factor), config);
            small = atlas.addFontFromMemoryTTF(regular, Math.round(SMALL * factor), config);
            title = atlas.addFontFromMemoryTTF(semibold, Math.round(TITLE * factor), config);
            heading = atlas.addFontFromMemoryTTF(semibold, Math.round(HEADING * factor), config);
            display = atlas.addFontFromMemoryTTF(semibold, Math.round(DISPLAY * factor), config);
            monospace = atlas.addFontFromMemoryTTF(read("noto-sans-mono.ttf"), Math.round(MONOSPACE * factor), config);
            EditorStyle.setTitleFont(title);
            EditorStyle.setSmallFont(small);
            EditorStyle.setMonospaceFont(monospace);
        } catch (IOException e) {
            MoudMod.LOG.error("the editor fonts could not be read, imgui keeps its own", e);
        } finally {
            config.destroy();
        }
    }

    public static @Nullable ImFont body() {
        return body;
    }

    public static @Nullable ImFont small() {
        return small;
    }

    public static @Nullable ImFont heading() {
        return heading;
    }

    public static @Nullable ImFont display() {
        return display;
    }

    public static @Nullable ImFont title() {
        return title;
    }

    public static @Nullable ImFont monospace() {
        return monospace;
    }

    private static short[] ranges(ImFontAtlas atlas) {
        ImFontGlyphRangesBuilder builder = new ImFontGlyphRangesBuilder();
        builder.addRanges(atlas.getGlyphRangesDefault());
        builder.addRanges(atlas.getGlyphRangesCyrillic());
        return builder.buildRanges();
    }

    private static byte[] read(String name) throws IOException {
        try (InputStream in = EditorFonts.class.getResourceAsStream(ROOT + name)) {
            if (in == null) throw new IOException("missing " + ROOT + name);
            return in.readAllBytes();
        }
    }
}
