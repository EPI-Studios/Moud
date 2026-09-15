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
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
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
            } catch (IllegalArgumentException ignored) {
                return unknown(soundId);
            }
            if (PlaceFiles.read(soundId) == null) return unknown(soundId);
            places.put(id, soundId);
            return id;
        }
        Identifier id = Identifier.tryParse(soundId);
        if (id == null) return unknown(soundId);
        if (Minecraft.getInstance().getResourceManager().getResource(id).isPresent()) return id;
        Identifier file = eventFile(id);
        return file != null ? file : unknown(soundId);
    }

    static @Nullable Identifier eventFile(Identifier event) {
        WeighedSoundEvents sounds = Minecraft.getInstance().getSoundManager().getSoundEvent(event);
        if (sounds == null) return null;
        Sound picked = sounds.getSound(RandomSource.create());
        if (picked == null || picked == SoundManager.EMPTY_SOUND) return null;
        Identifier path = picked.getPath();
        return Minecraft.getInstance().getResourceManager().getResource(path).isPresent() ? path : null;
    }

    private @Nullable Identifier unknown(String soundId) {
        if (missing.add(soundId)) MoudMod.LOG.warn("sound {} not found", soundId);
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
