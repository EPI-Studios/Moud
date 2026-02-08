package com.moud.server.task;

import com.moud.server.MoudEngine;
import com.moud.server.api.exception.APIException;
import com.moud.server.logging.MoudLogger;
import com.moud.server.profiler.model.ScriptExecutionMetadata;
import com.moud.server.profiler.model.ScriptExecutionType;
import net.minestom.server.MinecraftServer;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class AsyncManager {
    private static final MoudLogger LOGGER = MoudLogger.getLogger(AsyncManager.class);
    private final ExecutorService workerPool;
    private final MoudEngine engine;

    public AsyncManager(MoudEngine engine) {
        this.engine = engine;
        int coreCount = Math.max(1, Runtime.getRuntime().availableProcessors() / 2);
        this.workerPool = Executors.newFixedThreadPool(coreCount, r -> new Thread(r, "Moud-Script-Worker"));
    }

    @HostAccess.Export
    public CompletableFuture<Value> submit(Value task) {
        if (!task.canExecute()) {
            throw new APIException("ASYNC_TASK_NOT_EXECUTABLE", "The provided value is not a function.");
        }
        return CompletableFuture.supplyAsync(task::execute, workerPool);
    }

    @HostAccess.Export
    public void runOnServerThread(Value task) {
        if (!task.canExecute()) {
            throw new APIException("SERVER_TASK_NOT_EXECUTABLE", "The provided value is not a function.");
        }
        MinecraftServer.getSchedulerManager().scheduleNextTick(() -> engine.getRuntime().executeCallback(
                task,
                ScriptExecutionMetadata.of(ScriptExecutionType.ASYNC_TASK, "runOnServerThread", "")
        ));
    }

    public void shutdown() {
        workerPool.shutdown();
        try {
            if (!workerPool.awaitTermination(5, TimeUnit.SECONDS)) workerPool.shutdownNow();
        } catch (InterruptedException e) {
            workerPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
