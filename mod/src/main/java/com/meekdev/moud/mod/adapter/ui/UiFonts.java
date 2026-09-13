package com.meekdev.moud.mod.adapter.ui;

import com.meekdev.moud.core.asset.Res;
import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.client.PlaceFiles;
import com.meekdev.amnetic.client.surface.text.Fonts;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.resources.Identifier;

// turns what a place wrote in font into something the text renderer can load
final class UiFonts {

    static final Identifier DEFAULT = Identifier.fromNamespaceAndPath("moud", "fonts/minecraftia.ttf");

    private static final Set<String> KNOWN = new HashSet<>();
    private static final Set<String> MISSING = new HashSet<>();

    private UiFonts() {}

    static Identifier of(String chosen) {
        if (chosen.isEmpty()) return DEFAULT;
        if (!chosen.startsWith(Res.SCHEME)) {
            Identifier id = Identifier.tryParse(chosen);
            return id == null ? DEFAULT : id;
        }
        Identifier id = PlaceFiles.idOf(chosen);
        if (KNOWN.contains(chosen)) return id;
        byte[] bytes = PlaceFiles.read(chosen);
        if (bytes == null) {
            if (MISSING.add(chosen)) MoudMod.LOG.warn("font {} is not in the place, using the default", chosen);
            return DEFAULT;
        }
        Fonts.register(id, bytes);
        KNOWN.add(chosen);
        return id;
    }

    // a reload may have changed the files, so they are read again the next time they are asked for
    static void forget() {
        KNOWN.clear();
        MISSING.clear();
    }
}
