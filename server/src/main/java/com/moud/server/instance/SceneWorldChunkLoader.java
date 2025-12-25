package com.moud.server.instance;

import net.hollowcube.polar.PolarLoader;
import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.IChunkLoader;
import net.minestom.server.instance.Instance;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

final class SceneWorldChunkLoader implements IChunkLoader {
    private final PolarLoader delegate;
    private final SceneWorldAccess worldAccess;
    private final Executor ioExecutor;

    SceneWorldChunkLoader(Path worldFile, SceneWorldAccess worldAccess) throws IOException {
        this(worldFile, worldAccess, ForkJoinPool.commonPool());
    }

    SceneWorldChunkLoader(Path worldFile, SceneWorldAccess worldAccess, Executor ioExecutor) throws IOException {
        this.worldAccess = Objects.requireNonNull(worldAccess, "worldAccess");
        this.ioExecutor = Objects.requireNonNull(ioExecutor, "ioExecutor");

        this.delegate = createLoader(worldFile)
                .setWorldAccess(worldAccess)
                .setParallel(false);
    }

    @Override
    public void loadInstance(@NotNull Instance instance) {
        delegate.loadInstance(instance);
        worldAccess.ensureSceneInitialized(instance);
    }

    @Override
    public @NotNull CompletableFuture<Chunk> loadChunk(@NotNull Instance instance, int chunkX, int chunkZ) {
        return delegate.loadChunk(instance, chunkX, chunkZ);
    }

    @Override
    public @NotNull CompletableFuture<Void> saveInstance(@NotNull Instance instance) {
        return CompletableFuture.runAsync(() -> {
            delegate.saveInstance(instance);
        }, ioExecutor);
    }

    @Override
    public @NotNull CompletableFuture<Void> saveChunk(@NotNull Chunk chunk) {
        Instance instance = chunk.getInstance();
        return saveInstance(instance);
    }

    @Override
    public @NotNull CompletableFuture<Void> saveChunks(@NotNull Collection<Chunk> chunks) {
        if (chunks.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        Instance instance = chunks.iterator().next().getInstance();
        return saveInstance(instance);
    }

    @Override
    public void unloadChunk(@NotNull Chunk chunk) {
        delegate.unloadChunk(chunk);
    }

    @Override
    public boolean supportsParallelLoading() {
        return false;
    }

    @Override
    public boolean supportsParallelSaving() {
        return false;
    }

    private static PolarLoader createLoader(Path worldFile) throws IOException {
        if (worldFile == null) throw new IOException("World file path cannot be null");
        return new PolarLoader(worldFile);
    }
}
