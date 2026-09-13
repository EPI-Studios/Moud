package com.meekdev.moud.mod.adapter.ui;

import com.meekdev.amnetic.client.surface.text.Fonts;
import com.meekdev.moud.core.asset.Res;
import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.client.PlaceFiles;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;

public final class UiFonts {

    public sealed interface Face permits Game, Vector {}

    public record Game(FontDescription font) implements Face {}

    public record Vector(Identifier font) implements Face {}

    public static final Game DEFAULT = new Game(FontDescription.DEFAULT);

    private static final Set<String> KNOWN = new HashSet<>();
    private static final Set<String> MISSING = new HashSet<>();

    private UiFonts() {}

    public static Face of(String chosen) {
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
        if (MISSING.add(chosen)) MoudMod.LOG.warn("failed to load font {}, using the default", chosen);
        return DEFAULT;
    }

    static void forget() {
        KNOWN.clear();
        MISSING.clear();
    }
}
