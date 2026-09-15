package com.meekdev.moud.mod.client.editor.style;

import com.meekdev.moud.mod.MoudMod;
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

    private static final String ROOT = "/assets/moud/editor/fonts/";

    private static @Nullable ImFont body;
    private static @Nullable ImFont title;
    private static @Nullable ImFont monospace;

    private EditorFonts() {}

    public static void register(ImFontAtlas atlas) {
        ImFontConfig config = new ImFontConfig();
        config.setGlyphRanges(ranges(atlas));
        try {
            body = atlas.addFontFromMemoryTTF(read("inter-regular.ttf"), BODY, config);
            title = atlas.addFontFromMemoryTTF(read("inter-semibold.ttf"), TITLE, config);
            monospace = atlas.addFontFromMemoryTTF(read("noto-sans-mono.ttf"), MONOSPACE, config);
            EditorStyle.setTitleFont(title);
            EditorStyle.setSmallFont(body);
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
