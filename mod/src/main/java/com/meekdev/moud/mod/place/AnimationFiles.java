package com.meekdev.moud.mod.place;

import com.meekdev.moud.core.asset.Res;
import com.meekdev.moud.core.character.Animators;
import com.meekdev.moud.core.character.Clip;
import com.meekdev.moud.core.scene.Json;
import com.meekdev.moud.mod.MoudMod;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public final class AnimationFiles {

    private AnimationFiles() {}

    public static void install() {
        Animators.files(AnimationFiles::load);
    }

    @SuppressWarnings("unchecked")
    private static @Nullable Clip load(String res) {
        if (!res.startsWith(Res.SCHEME)) {
            Output.add(Output.Level.ERROR, "animation", "animationId " + res + " must be a res:// path to a .anim file");
            return null;
        }
        try {
            byte[] bytes = SyncedFiles.read(PlaceToml.root(), res);
            if (bytes == null) throw new IOException("the file is not in the place");
            Object parsed = Json.parse(new String(bytes, StandardCharsets.UTF_8));
            if (!(parsed instanceof Map<?, ?> root)) throw new IllegalArgumentException("the file must hold a JSON object");
            return Clip.parse((Map<String, Object>) root);
        } catch (IOException | RuntimeException e) {
            MoudMod.LOG.warn("could not read animation {}: {}", res, e.getMessage());
            Output.add(Output.Level.ERROR, "animation", "could not read " + res + ": " + e.getMessage());
            return null;
        }
    }
}
