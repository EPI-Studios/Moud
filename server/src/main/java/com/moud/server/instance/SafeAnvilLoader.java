package com.moud.server.instance;

import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.IChunkLoader;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.anvil.AnvilLoader;
import net.minestom.server.instance.block.Block;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public class SafeAnvilLoader implements IChunkLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(SafeAnvilLoader.class);
    private final AnvilLoader delegate;

    public SafeAnvilLoader(Path worldPath) {
        this.delegate = new AnvilLoader(worldPath);
    }

    @Override
    public @NotNull CompletableFuture<@Nullable Chunk> loadChunk(@NotNull Instance instance, int chunkX, int chunkZ) {
        return delegate.loadChunk(instance, chunkX, chunkZ).exceptionally(throwable -> {
            if (throwable.getMessage() != null && throwable.getMessage().contains("Unknown block")) {
                LOGGER.debug("Skipping chunk ({}, {}) due to unknown blocks: {}", chunkX, chunkZ, throwable.getMessage());
            } else {
                LOGGER.warn("Failed to load chunk ({}, {}): {}", chunkX, chunkZ, throwable.getMessage());
            }
            return null;
        });
    }

    @Override
    public @NotNull CompletableFuture<Void> saveChunk(@NotNull Chunk chunk) {
        return delegate.saveChunk(chunk);
    }
}