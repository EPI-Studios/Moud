package com.moud.client.fabric.render.veil;

import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.shader.ShaderManager;
import foundry.veil.api.client.render.shader.program.ShaderProgram;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Objects;
import net.minecraft.util.Identifier;


public final class VeilDynamicShaders {
    private static final ConcurrentHashMap<Identifier, Entry> entries = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Identifier, String> lastErrors = new ConcurrentHashMap<>();

    private VeilDynamicShaders() {
    }

    public static void clear() {
        entries.clear();
        lastErrors.clear();
    }

    public static String getLastError(Identifier programId) {
        return lastErrors.get(programId);
    }

    public static Map<Identifier, String> getAllErrors() {
        return Collections.unmodifiableMap(lastErrors);
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
                } catch (Exception ex) {
                    entry.program = null;
                    String msg = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                    lastErrors.put(programId, msg != null ? msg : "Unknown shader compilation error");
                } finally {
                    entry.future = null;
                }
                if (entry.program != null && entry.program.isValid()) {
                    lastErrors.remove(programId);
                    return entry.program;
                }
                if (entry.program == null && !lastErrors.containsKey(programId)) {
                    lastErrors.put(programId, "Shader program returned null");
                }
                return null;
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
