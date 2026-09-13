package com.meekdev.moud.mod.adapter.audio;

import com.meekdev.moud.core.asset.Res;
import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.client.PlaceFiles;
import com.meekdev.resona.api.SoundSource;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

final class PlaceSounds implements SoundSource {

    private final Map<Identifier, String> places = new ConcurrentHashMap<>();
    private final Set<String> missing = new HashSet<>();

    @Nullable Identifier id(String soundId) {
        if (soundId.isEmpty()) return null;
        if (soundId.startsWith(Res.SCHEME)) {
            Identifier id;
            try {
                id = PlaceFiles.idOf(soundId);
            } catch (IllegalArgumentException wrong) {
                return unknown(soundId);
            }
            if (PlaceFiles.read(soundId) == null) return unknown(soundId);
            places.put(id, soundId);
            return id;
        }
        Identifier id = Identifier.tryParse(soundId);
        if (id == null || Minecraft.getInstance().getResourceManager().getResource(id).isEmpty()) {
            return unknown(soundId);
        }
        return id;
    }

    private @Nullable Identifier unknown(String soundId) {
        if (missing.add(soundId)) MoudMod.LOG.warn("sound {} is not in the place or a resource pack", soundId);
        return null;
    }

    @Override
    public byte[] read(Identifier sound) {
        String res = places.get(sound);
        return res == null ? null : PlaceFiles.read(res);
    }

    void forget() {
        places.clear();
        missing.clear();
    }
}
