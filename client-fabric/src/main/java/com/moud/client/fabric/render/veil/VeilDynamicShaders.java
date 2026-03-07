package com.moud.client.fabric.render.veil;

import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.shader.ShaderManager;
import foundry.veil.api.client.render.shader.program.ShaderProgram;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Objects;
import net.minecraft.util.Identifier;


public final class VeilDynamicShaders {
    private static final ConcurrentHashMap<Identifier, Entry> entries = new ConcurrentHashMap<>();

    private VeilDynamicShaders() {
    }

    public static void clear() {
        entries.clear();
    }

    public static ShaderProgram getOrCompile(Identifier programId, Int2ObjectMap<String> stageSources) {
        Objects.requireNonNull(programId, "programId");
        Objects.requireNonNull(stageSources, "stageSources");

        Entry entry = entries.computeIfAbsent(programId, k -> new Entry());
        synchronized (entry) {
            if (entry.program != null) {
                return entry.program.isValid() ? entry.program : null;
            }
            if (entry.future != null) {
                if (!entry.future.isDone()) {
                    return null;
                }
                try {
                    entry.program = entry.future.getNow(null);
                } catch (Exception ignored) {
                    entry.program = null;
                } finally {
                    entry.future = null;
                }
                return entry.program != null && entry.program.isValid() ? entry.program : null;
            }

            ShaderManager sm = VeilRenderSystem.renderer().getShaderManager();
            entry.future = sm.createDynamicProgram(programId, new Int2ObjectArrayMap<>(stageSources));
            return null;
        }
    }

    private static final class Entry {
        private CompletableFuture<ShaderProgram> future;
        private ShaderProgram program;
    }
}
