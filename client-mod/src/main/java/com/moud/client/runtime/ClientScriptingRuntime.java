package com.moud.client.runtime;

import com.moud.client.api.service.ClientAPIService;
import net.minecraft.client.MinecraftClient;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class ClientScriptingRuntime {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientScriptingRuntime.class);
    private static volatile ClientScriptingRuntime instance;

    private final ClientAPIService apiService;
    private volatile Context graalContext;
    private final ExecutorService scriptExecutor;
    private final ScheduledExecutorService timerExecutor;
    private final ReentrantReadWriteLock contextLock = new ReentrantReadWriteLock();

    private static final Queue<Runnable> scriptExecutionQueue = new ConcurrentLinkedQueue<>();

    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);
    private final AtomicLong timerIdCounter = new AtomicLong(0);
    private final ConcurrentHashMap<Long, ScheduledFuture<?>> activeTimers = new ConcurrentHashMap<>();

    public ClientScriptingRuntime(ClientAPIService apiService) {
        this.apiService = apiService;
        this.scriptExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "ClientScript-Executor");
            t.setDaemon(true);
            t.setUncaughtExceptionHandler((thread, throwable) -> LOGGER.error("Uncaught exception in script executor thread", throwable));
            return t;
        });
        this.timerExecutor = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "ClientScript-Timer");
            t.setDaemon(true);
            t.setUncaughtExceptionHandler((thread, throwable) -> LOGGER.error("Uncaught exception in timer executor thread", throwable));
            return t;
        });
    }

    public static synchronized ClientScriptingRuntime getInstance(ClientAPIService apiService) {
        if (instance == null) {
            if (apiService == null) {
                throw new IllegalArgumentException("ClientAPIService cannot be null");
            }
            instance = new ClientScriptingRuntime(apiService);
        } else {
            LOGGER.warn("ClientScriptingRuntime is already initialized. Returning existing instance.");
        }
        return instance;
    }

    public static ClientScriptingRuntime getInstance() {
        if (instance == null) {
            throw new IllegalStateException("ClientScriptingRuntime not initialized. Call getInstance(ClientAPIService) first.");
        }
        return instance;
    }

    public void initialize() {
        if (shuttingDown.get()) {
            LOGGER.warn("Cannot initialize runtime during shutdown");
            return;
        }

        contextLock.writeLock().lock();
        try {
            if (initialized.get()) {
                LOGGER.info("Runtime already initialized. Skipping.");
                return;
            }

            closeExistingContext();

            LOGGER.info("Initializing GraalVM context for client scripts.");
            this.graalContext = createGraalContext();
            bindApiToContext();
            bindTimerFunctionsToContext();

            this.initialized.set(true);
            LOGGER.info("Client scripting runtime GraalVM context created.");
        } catch (Exception e) {
            LOGGER.error("Failed to initialize GraalVM context!", e);
            this.initialized.set(false);
            closeExistingContext();
        } finally {
            contextLock.writeLock().unlock();
        }
    }

    private void closeExistingContext() {
        if (graalContext != null) {
            try {
                graalContext.close(true); // Force close to interrupt threads
                LOGGER.info("Previous GraalVM context closed.");
            } catch (Exception e) {
                LOGGER.warn("Error closing previous context", e);
            } finally {
                graalContext = null;
            }
        }
    }

    private Context createGraalContext() {
        return Context.newBuilder("js")
                .allowHostAccess(HostAccess.ALL)
                .allowCreateThread(true)
                .allowExperimentalOptions(true)
                .option("js.shared-array-buffer", "true")
                .build();
    }

    private void bindApiToContext() {
        this.graalContext.getBindings("js").putMember("moudAPI", this.apiService);
        this.graalContext.getBindings("js").putMember("Moud", this.apiService); // Duplicate binding for compatibility
    }

    private void bindTimerFunctionsToContext() {
        this.graalContext.getBindings("js").putMember("setTimeout", new TimeoutFunction());
        this.graalContext.getBindings("js").putMember("clearTimeout", new ClearTimeoutFunction());
        this.graalContext.getBindings("js").putMember("setInterval", new IntervalFunction());
        this.graalContext.getBindings("js").putMember("clearInterval", new ClearIntervalFunction());
    }

    public CompletableFuture<Void> loadScripts(Map<String, byte[]> scriptsData) {
        if (!initialized.get() || shuttingDown.get()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Runtime not initialized or shutting down"));
        }
        if (scriptsData == null || scriptsData.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        LOGGER.info("Scheduling script loading with {} scripts...", scriptsData.size());
        return CompletableFuture.runAsync(() -> loadScriptsInternal(scriptsData), scriptExecutor)
                .exceptionally(ex -> {
                    LOGGER.error("Exception during script loading", ex);
                    return null;
                });
    }

    private void loadScriptsInternal(Map<String, byte[]> scriptsData) {
        contextLock.readLock().lock();
        try {
            if (graalContext == null || shuttingDown.get()) {
                throw new IllegalStateException("Context is not available");
            }

            graalContext.enter();
            try {
                for (Map.Entry<String, byte[]> entry : scriptsData.entrySet()) {
                    String scriptName = entry.getKey();
                    String scriptContent = new String(entry.getValue());

                    LOGGER.debug("Loading client script: {}", scriptName);
                    try {
                        graalContext.eval("js", scriptContent);
                    } catch (PolyglotException e) {
                        LOGGER.error("Syntax or runtime error in script '{}'", scriptName, e);
                        throw e;
                    }
                }
                LOGGER.info("Client scripts loaded and executed successfully.");
            } finally {
                graalContext.leave();
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load client scripts", e);
            this.initialized.set(false);
            throw new RuntimeException(e);
        } finally {
            contextLock.readLock().unlock();
        }
    }

    public CompletableFuture<Void> loadScripts() {
        if (!initialized.get() || shuttingDown.get()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Runtime not initialized or shutting down"));
        }

        LOGGER.info("Scheduling empty script loading (no-op)...");
        return CompletableFuture.runAsync(() -> LOGGER.info("No scripts to load."), scriptExecutor);
    }

    public void triggerNetworkEvent(String eventName, String data) {
        if (!isInitialized() || shuttingDown.get()) {
            LOGGER.debug("Skipping network event '{}' - runtime not ready", eventName);
            return;
        }
        if (eventName == null || eventName.isEmpty()) {
            LOGGER.warn("Invalid event name provided");
            return;
        }

        scriptExecutor.execute(() -> triggerNetworkEventInternal(eventName, data));
    }

    private void triggerNetworkEventInternal(String eventName, String data) {
        contextLock.readLock().lock();
        try {
            if (graalContext == null || shuttingDown.get() || !initialized.get()) {
                LOGGER.debug("Skipping network event '{}' - context closed", eventName);
                return;
            }

            graalContext.enter();
            try {
                graalContext.getBindings("js")
                        .getMember("moudAPI")
                        .getMember("network")
                        .invokeMember("triggerEvent", eventName, data);
            } catch (PolyglotException e) {
                LOGGER.error("Polyglot error triggering network event '{}'", eventName, e);
            } finally {
                graalContext.leave();
            }
        } catch (Exception e) {
            LOGGER.error("Failed to trigger network event '{}' in script", eventName, e);
        } finally {
            contextLock.readLock().unlock();
        }
    }

    public boolean isInitialized() {
        return this.initialized.get() && !shuttingDown.get();
    }

    public Context getContext() {
        contextLock.readLock().lock();
        try {
            if (graalContext == null) {
                throw new IllegalStateException("GraalVM context is not available");
            }
            return graalContext;
        } finally {
            contextLock.readLock().unlock();
        }
    }

    public ExecutorService getExecutor() {
        return scriptExecutor;
    }

    public void error(String message, Throwable throwable) {
        LOGGER.error(message, throwable);
    }

    public static void scheduleScriptTask(Runnable scriptTask) {
        if (scriptTask == null) {
            return;
        }
        scriptExecutionQueue.add(scriptTask);
    }

    public void processScriptQueue() {
        if (!isInitialized()) {
            return;
        }

        Runnable task;
        while ((task = scriptExecutionQueue.poll()) != null) {
            contextLock.readLock().lock();
            try {
                if (graalContext == null || shuttingDown.get() || !initialized.get()) {
                    break;
                }

                graalContext.enter();
                try {
                    task.run();
                } catch (PolyglotException e) {
                    LOGGER.error("Polyglot error executing scheduled script task", e);
                } finally {
                    graalContext.leave();
                }
            } catch (Exception e) {
                LOGGER.error("Error executing scheduled script task", e);
            } finally {
                contextLock.readLock().unlock();
            }
        }
    }

    public void shutdown() {
        if (!shuttingDown.compareAndSet(false, true)) {
            LOGGER.debug("Shutdown already initiated");
            return;
        }

        LOGGER.info("Shutting down client scripting runtime");
        this.initialized.set(false);

        // Cancel all timers
        activeTimers.forEach((id, future) -> future.cancel(true));
        activeTimers.clear();

        // Clear queues
        scriptExecutionQueue.clear();

        contextLock.writeLock().lock();
        try {
            closeExistingContext();
        } finally {
            contextLock.writeLock().unlock();
        }

        shutdownExecutor(scriptExecutor, "scriptExecutor");
        shutdownExecutor(timerExecutor, "timerExecutor");
    }

    private void shutdownExecutor(ExecutorService executor, String name) {
        if (!executor.isShutdown()) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    LOGGER.warn("Executor {} did not terminate in time, forcing shutdown", name);
                    executor.shutdownNow();
                    if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                        LOGGER.error("Executor {} did not terminate after force shutdown", name);
                    }
                }
            } catch (InterruptedException e) {
                LOGGER.warn("Interrupted while awaiting termination of {}", name, e);
                Thread.currentThread().interrupt();
                executor.shutdownNow();
            }
        }
    }

    public void reinitialize() {
        LOGGER.info("Reinitializing client scripting runtime");
        shutdown();
        shuttingDown.set(false);
        initialize();
        if (apiService != null) {
            apiService.updateScriptingContext(graalContext);
        }
    }

    public class TimeoutFunction {
        public long setTimeout(Runnable callback, long delay) {
            if (callback == null || delay < 0) {
                LOGGER.warn("Invalid setTimeout parameters");
                return -1;
            }
            long id = timerIdCounter.incrementAndGet();
            ScheduledFuture<?> future = timerExecutor.schedule(() -> executeCallback(callback, id, false), delay, TimeUnit.MILLISECONDS);
            activeTimers.put(id, future);
            return id;
        }
    }

    public class ClearTimeoutFunction {
        public void clearTimeout(long id) {
            cancelTimer(id);
        }
    }

    public class IntervalFunction {
        public long setInterval(Runnable callback, long delay) {
            if (callback == null || delay <= 0) {
                LOGGER.warn("Invalid setInterval parameters");
                return -1;
            }
            long id = timerIdCounter.incrementAndGet();
            ScheduledFuture<?> future = timerExecutor.scheduleAtFixedRate(() -> executeCallback(callback, id, true), delay, delay, TimeUnit.MILLISECONDS);
            activeTimers.put(id, future);
            return id;
        }
    }

    public class ClearIntervalFunction {
        public void clearInterval(long id) {
            cancelTimer(id);
        }
    }

    private void executeCallback(Runnable callback, long id, boolean isInterval) {
        scriptExecutor.execute(() -> {
            contextLock.readLock().lock();
            try {
                if (graalContext != null && !shuttingDown.get()) {
                    graalContext.enter();
                    try {
                        callback.run();
                    } catch (PolyglotException e) {
                        LOGGER.error("Polyglot error executing {} callback", isInterval ? "setInterval" : "setTimeout", e);
                    } finally {
                        graalContext.leave();
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Error executing {} callback", isInterval ? "setInterval" : "setTimeout", e);
            } finally {
                contextLock.readLock().unlock();
                if (!isInterval) {
                    activeTimers.remove(id);
                }
            }
        });
    }

    private void cancelTimer(long id) {
        ScheduledFuture<?> future = activeTimers.remove(id);
        if (future != null) {
            future.cancel(false);
        } else {
            LOGGER.debug("No timer found for id: {}", id);
        }
    }
}