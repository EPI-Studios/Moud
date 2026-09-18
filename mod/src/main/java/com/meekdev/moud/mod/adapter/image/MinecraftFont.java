package com.meekdev.moud.mod.adapter.image;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.meekdev.moud.core.image.Glyph;
import com.meekdev.moud.core.image.GlyphFont;
import com.meekdev.moud.mod.MoudMod;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.imageio.ImageIO;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

final class MinecraftFont {

    private static final Identifier DEFAULT = Identifier.withDefaultNamespace("default");
    private static final int LINE_HEIGHT = 9;
    private static final int BITMAP_HEIGHT = 8;

    private MinecraftFont() {}

    static GlyphFont read(ResourceManager resources) {
        Map<Integer, Glyph> glyphs = new HashMap<>();
        include(resources, DEFAULT, glyphs, new HashSet<>());
        if (glyphs.isEmpty()) MoudMod.LOG.warn("found no glyphs in {}, text will draw as boxes", DEFAULT);
        return new GlyphFont(glyphs, LINE_HEIGHT);
    }

    private static void include(ResourceManager resources, Identifier font, Map<Integer, Glyph> glyphs, Set<Identifier> open) {
        if (!open.add(font)) return;
        Identifier file = font.withPath(path -> "font/" + path + ".json");
        for (Resource resource : resources.getResourceStack(file).reversed()) {
            JsonObject json;
            try (Reader reader = resource.openAsReader()) {
                json = JsonParser.parseReader(reader).getAsJsonObject();
            } catch (IOException | RuntimeException e) {
                MoudMod.LOG.warn("could not read {} from {}: {}", file, resource.sourcePackId(), e.getMessage());
                continue;
            }
            if (!json.has("providers")) continue;
            for (JsonElement element : json.getAsJsonArray("providers")) {
                try {
                    provider(resources, element.getAsJsonObject(), glyphs, open);
                } catch (IOException | RuntimeException e) {
                    MoudMod.LOG.warn("skipped a font provider in {} from {}: {}", file, resource.sourcePackId(), e.getMessage());
                }
            }
        }
        open.remove(font);
    }

    private static void provider(ResourceManager resources, JsonObject provider, Map<Integer, Glyph> glyphs, Set<Identifier> open) throws IOException {
        if (!shown(provider)) return;
        switch (provider.get("type").getAsString()) {
            case "reference" -> include(resources, Identifier.parse(provider.get("id").getAsString()), glyphs, open);
            case "space" -> {
                for (Map.Entry<String, JsonElement> entry : provider.getAsJsonObject("advances").entrySet()) {
                    int[] codePoints = entry.getKey().codePoints().toArray();
                    if (codePoints.length == 1) glyphs.putIfAbsent(codePoints[0], Glyph.space(entry.getValue().getAsDouble()));
                }
            }
            case "bitmap" -> bitmap(resources, provider, glyphs);
            default -> {}
        }
    }

    private static boolean shown(JsonObject provider) {
        if (!provider.has("filter")) return true;
        for (Map.Entry<String, JsonElement> option : provider.getAsJsonObject("filter").entrySet()) {
            if (option.getValue().getAsBoolean()) return false;
        }
        return true;
    }

    private static void bitmap(ResourceManager resources, JsonObject provider, Map<Integer, Glyph> glyphs) throws IOException {
        Identifier texture = Identifier.parse(provider.get("file").getAsString()).withPrefix("textures/");
        int height = provider.has("height") ? provider.get("height").getAsInt() : BITMAP_HEIGHT;
        int ascent = provider.get("ascent").getAsInt();
        List<int[]> rows = provider.getAsJsonArray("chars").asList().stream().map(row -> row.getAsString().codePoints().toArray()).toList();
        if (rows.isEmpty() || rows.getFirst().length == 0) return;
        Optional<Resource> resource = resources.getResource(texture);
        if (resource.isEmpty()) throw new IOException("there is no " + texture);
        BufferedImage image;
        try (InputStream in = resource.get().open()) {
            image = ImageIO.read(in);
        }
        if (image == null) throw new IOException(texture + " is not an image");
        int columns = rows.getFirst().length;
        int cellWidth = image.getWidth() / columns;
        int cellHeight = image.getHeight() / rows.size();
        if (cellWidth <= 0 || cellHeight <= 0) throw new IOException(texture + " is too small for its characters");
        double scale = (double) height / cellHeight;
        for (int row = 0; row < rows.size(); row++) {
            int[] line = rows.get(row);
            for (int column = 0; column < line.length && column < columns; column++) {
                int codePoint = line[column];
                if (codePoint == 0 || glyphs.containsKey(codePoint)) continue;
                int x = column * cellWidth;
                int y = row * cellHeight;
                int width = inked(image, x, y, cellWidth, cellHeight);
                int[] argb = width == 0 ? new int[0] : image.getRGB(x, y, width, cellHeight, null, 0, width);
                double advance = (int) (0.5 + width * scale) + 1;
                glyphs.put(codePoint, new Glyph(width, width == 0 ? 0 : cellHeight, argb, scale, ascent, advance));
            }
        }
    }

    private static int inked(BufferedImage image, int x, int y, int cellWidth, int cellHeight) {
        for (int column = cellWidth - 1; column >= 0; column--) {
            for (int row = 0; row < cellHeight; row++) {
                if ((image.getRGB(x + column, y + row) >>> 24) != 0) return column + 1;
            }
        }
        return 0;
    }
}
