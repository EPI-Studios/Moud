package com.meekdev.moud.mod.adapter.ui;

import com.meekdev.moud.core.asset.Res;
import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.client.PlaceFiles;
import com.meekdev.amnetic.client.surface.text.Fonts;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;

// turns what a place wrote in font into a way to draw it: one of the game's own fonts, or a .ttf
final class UiFonts {

    sealed interface Face permits Game, Vector {}

    // a font the game loaded, the default one unless a place names another like minecraft:uniform
    record Game(FontDescription font) implements Face {}

    // a .ttf, drawn as a distance field
    record Vector(Identifier font) implements Face {}

    static final Game DEFAULT = new Game(FontDescription.DEFAULT);

    private static final Set<String> KNOWN = new HashSet<>();
    private static final Set<String> MISSING = new HashSet<>();

    private UiFonts() {}

    static Face of(String chosen) {
        if (chosen.isEmpty()) return DEFAULT;
        String lower = chosen.toLowerCase(Locale.ROOT);
        boolean file = lower.endsWith(".ttf") || lower.endsWith(".otf");
        if (chosen.startsWith(Res.SCHEME)) {
            if (!file) return missing(chosen);
            Identifier id = PlaceFiles.idOf(chosen);
            if (!KNOWN.contains(chosen)) {
                byte[] bytes = PlaceFiles.read(chosen);
                if (bytes == null) return missing(chosen);
                Fonts.register(id, bytes);
                KNOWN.add(chosen);
            }
            return new Vector(id);
        }
        Identifier id = Identifier.tryParse(chosen);
        if (id == null) return missing(chosen);
        return file ? new Vector(id) : new Game(new FontDescription.Resource(id));
    }

    private static Face missing(String chosen) {
        if (MISSING.add(chosen)) MoudMod.LOG.warn("font {} cannot be loaded, using the game's", chosen);
        return DEFAULT;
    }

    // a reload may have changed the files, so they are read again the next time they are asked for
    static void forget() {
        KNOWN.clear();
        MISSING.clear();
    }
}
