package com.moud.client.runtime;

import com.moud.client.api.service.ClientAPIService;
import com.moud.client.network.ClientPacketWrapper;
import com.moud.client.ui.UIOverlayManager;
import com.moud.network.MoudPackets;
import net.minecraft.client.MinecraftClient;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class ClientScriptingRuntime {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientScriptingRuntime.class);
    private static final Queue<Runnable> scriptTaskQueue = new ConcurrentLinkedQueue<>();

    private Context graalContext;
    private final ThreadPoolExecutor scriptExecutor;
    private final ScheduledExecutorService timerExecutor;
    private final ClientAPIService apiService;
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    private final AtomicLong timerIdCounter = new AtomicLong(0);
    private final ConcurrentHashMap<Long, ScheduledFuture<?>> activeTimers = new ConcurrentHashMap<>();

    private final List<Value> worldLoadCallbacks = new CopyOnWriteArrayList<>();

    private final long startTime = System.nanoTime();

    public ClientScriptingRuntime(ClientAPIService apiService) {
        this.apiService = apiService;
        this.scriptExecutor = new ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingDeque<>(),
                r -> {
                    Thread t = new Thread(r, "ClientScript-Executor");
                    t.setDaemon(true);
                    t.setPriority(Thread.NORM_PRIORITY + 1);
                    return t;
                },
                new ThreadPoolExecutor.DiscardOldestPolicy()
        );
        this.timerExecutor = Executors.newScheduledThreadPool(2, r -> new Thread(r, "ClientScript-Timer"));
    }

    public static void scheduleScriptTask(Runnable task) {
        scriptTaskQueue.offer(task);
    }

    public CompletableFuture<Void> initialize() {
        if (initialized.get()) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(() -> {
            try {
                LOGGER.info("Initializing client scripting runtime...");
                // Todo: Make this stuff more secure
                HostAccess hostAccess = HostAccess.newBuilder()
                        .allowAccessAnnotatedBy(HostAccess.Export.class)
                        .allowAllImplementations(true)
                        .allowAllClassImplementations(true)
                        .allowArrayAccess(true)
                        .allowListAccess(true)
                        .allowMapAccess(true)
                        .build();

                this.graalContext = Context.newBuilder("js")
                        .allowHostAccess(hostAccess)
                        .allowIO(true)
                        .build();

                bindGlobalAPIs();
                bindTimerFunctionsToContext();
                bindAnimationFrameAPI();
                bindPerformanceAPI();

                initialized.set(true);
                LOGGER.info("Client scripting runtime initialized successfully.");

            } catch (Exception e) {
                LOGGER.error("Failed to initialize client scripting runtime", e);
                throw new RuntimeException(e);
            }
        }, scriptExecutor);
    }

    private void bindGlobalAPIs() {
        Value bindings = this.graalContext.getBindings("js");
        Value moudObj = this.graalContext.eval("js", "({})");

        if (apiService.network != null) {
            moudObj.putMember("network", apiService.network);
        }
        if (apiService.rendering != null) {
            moudObj.putMember("rendering", apiService.rendering);
        }
        if (apiService.ui != null) {
            moudObj.putMember("ui", apiService.ui);
        }
        if (apiService.camera != null) {
            moudObj.putMember("camera", apiService.camera);
        }
        if (apiService.cursor != null) {
            moudObj.putMember("cursor", apiService.cursor);
        }
        if (apiService.lighting != null) {
            moudObj.putMember("lighting", apiService.lighting);
        }
        if (apiService.audio != null) {
            moudObj.putMember("audio", apiService.audio);
        }
        if (apiService.shared != null) {
            moudObj.putMember("shared", apiService.shared);
        }
        if (apiService.events != null) {
            moudObj.putMember("events", apiService.events);
        }
        if (apiService.gamepad != null) {
            moudObj.putMember("gamepad", apiService.gamepad);
        }

        bindings.putMember("Moud", moudObj);
        bindings.putMember("console", apiService.console);


    }




    public void triggerWorldLoad() {
        if (!initialized.get() || shutdown.get()) return;

        scriptExecutor.execute(() -> {
            if (graalContext == null || shutdown.get()) return;

            try {
                graalContext.enter();
                for (Value callback : worldLoadCallbacks) {
                    try {
                        if (callback.canExecute()) {
                            callback.execute();
                        }
                    } catch (Exception e) {
                        LOGGER.error("Error executing world load callback", e);
                    }
                }
                worldLoadCallbacks.clear();
            } finally {
                graalContext.leave();
            }
        });
    }
    public void updateMoudBindings() {
        if (graalContext == null) return;

        scriptExecutor.execute(() -> {
            try {
                graalContext.enter();
                Value moudObj = graalContext.getBindings("js").getMember("Moud");

                if (apiService.input != null && !moudObj.hasMember("input")) {
                    moudObj.putMember("input", apiService.input);
                    LOGGER.info("Input service bound to Moud object");
                }

                if (apiService.audio != null && !moudObj.hasMember("audio")) {
                    moudObj.putMember("audio", apiService.audio);
                    LOGGER.info("Audio service bound to Moud object");
                }

                if (apiService.gamepad != null && !moudObj.hasMember("gamepad")) {
                    moudObj.putMember("gamepad", apiService.gamepad);
                    LOGGER.info("Gamepad service bound to Moud object");
                }

            } catch (Exception e) {
                LOGGER.error("Failed to update Moud bindings", e);
            } finally {
                graalContext.leave();
            }
        });
    }

    private void bindAnimationFrameAPI() {
        this.graalContext.getBindings("js").putMember("requestAnimationFrame", (ProxyExecutable) args -> {
            if (args.length < 1 || !args[0].canExecute()) {
                LOGGER.warn("Invalid arguments for requestAnimationFrame. Expected (function).");
                return null;
            }
            Value callback = args[0];
            return apiService.rendering.requestAnimationFrame(callback);
        });

        this.graalContext.getBindings("js").putMember("cancelAnimationFrame", (ProxyExecutable) args -> {
            if (args.length < 1 || !args[0].isString()) return null;
            apiService.rendering.cancelAnimationFrame(args[0].asString());
            return null;
        });
    }

    private void bindPerformanceAPI() {
        Value bindings = this.graalContext.getBindings("js");
        Value performanceObj = this.graalContext.eval("js", "({})");

        performanceObj.putMember("now", (ProxyExecutable) args -> {
            return (System.nanoTime() - startTime) / 1_000_000.0;
        });

        bindings.putMember("performance", performanceObj);
    }

    private void bindTimerFunctionsToContext() {
        this.graalContext.getBindings("js").putMember("setTimeout", (ProxyExecutable) args -> {
            if (args.length < 2 || !args[0].canExecute() || !args[1].isNumber()) {
                LOGGER.warn("Invalid arguments for setTimeout. Expected (function, number).");
                return -1L;
            }
            Value callback = args[0];
            long delay = args[1].asLong();
            long id = timerIdCounter.incrementAndGet();
            ScheduledFuture<?> future = timerExecutor.schedule(() -> executeCallback(callback, id, false), delay, TimeUnit.MILLISECONDS);
            activeTimers.put(id, future);
            return id;
        });

        this.graalContext.getBindings("js").putMember("clearTimeout", (ProxyExecutable) args -> {
            if (args.length < 1 || !args[0].isNumber()) return null;
            cancelTimer(args[0].asLong());
            return null;
        });

        this.graalContext.getBindings("js").putMember("setInterval", (ProxyExecutable) args -> {
            if (args.length < 2 || !args[0].canExecute() || !args[1].isNumber()) {
                LOGGER.warn("Invalid arguments for setInterval. Expected (function, number).");
                return -1L;
            }
            Value callback = args[0];
            long delay = args[1].asLong();
            if (delay <= 0) {
                LOGGER.warn("setInterval delay must be greater than 0.");
                return -1L;
            }
            long id = timerIdCounter.incrementAndGet();
            ScheduledFuture<?> future = timerExecutor.scheduleAtFixedRate(() -> executeCallback(callback, id, true), delay, delay, TimeUnit.MILLISECONDS);
            activeTimers.put(id, future);
            return id;
        });

        this.graalContext.getBindings("js").putMember("clearInterval", (ProxyExecutable) args -> {
            if (args.length < 1 || !args[0].isNumber()) return null;
            cancelTimer(args[0].asLong());
            return null;
        });
    }

    private void executeCallback(Value callback, long timerId, boolean isInterval) {
        if (graalContext == null || shutdown.get()) return;

        scheduleScriptTask(() -> {
            if (graalContext == null || shutdown.get()) return;

            try {
                graalContext.enter();
                if (callback.canExecute()) {
                    callback.execute();
                }
            } catch (Exception e) {
                handleScriptException(e, "timer callback");
            } finally {
                if (graalContext != null) {
                    graalContext.leave();
                }
            }

            if (!isInterval) {
                activeTimers.remove(timerId);
            }
        });
    }

    private void cancelTimer(long id) {
        ScheduledFuture<?> future = activeTimers.remove(id);
        if (future != null) {
            future.cancel(false);
        }
    }

    public CompletableFuture<Void> loadScripts(Map<String, byte[]> scriptsData) {
        if (!initialized.get()) {
            LOGGER.error("Cannot load scripts - runtime not initialized");
            return CompletableFuture.failedFuture(new IllegalStateException("Runtime not initialized"));
        }

        return CompletableFuture.runAsync(() -> {
            try {
                loadScriptsInternal(scriptsData);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, scriptExecutor);
    }

    private void loadScriptsInternal(Map<String, byte[]> scriptsData) throws Exception {
        LOGGER.info("Loading {} client scripts", scriptsData.size());

        graalContext.enter();
        try {
            byte[] sharedPhysicsData = scriptsData.get("shared/physics.js");
            if (sharedPhysicsData != null && sharedPhysicsData.length > 0) {
                String sharedPhysicsSource = new String(sharedPhysicsData, StandardCharsets.UTF_8);
                try {
                    com.moud.client.physics.ClientPhysicsScriptLoader loader =
                            new com.moud.client.physics.ClientPhysicsScriptLoader(graalContext);
                    boolean registered = loader.loadSharedPhysics(sharedPhysicsSource);
                    LOGGER.info("Loaded shared physics script (registeredController={})", registered);
                } catch (Exception e) {
                    handleScriptException(e, "shared/physics.js");
                    throw e;
                }
            }

            List<String> scriptNames = new ArrayList<>(scriptsData.keySet());
            scriptNames.sort(String::compareTo);

            for (String scriptName : scriptNames) {
                if (scriptName == null || scriptName.isBlank()) {
                    continue;
                }
                if ("shared/physics.js".equals(scriptName)) {
                    continue;
                }
                if (scriptName.endsWith(".map")) {
                    continue;
                }
                byte[] scriptBytes = scriptsData.get(scriptName);
                if (scriptBytes == null || scriptBytes.length == 0) {
                    continue;
                }
                String scriptContent = new String(scriptBytes, StandardCharsets.UTF_8);

                try {
                    LOGGER.info("Executing client script: {}", scriptName);
                    graalContext.eval("js", scriptContent);
                    LOGGER.info("Successfully executed script: {}", scriptName);
                } catch (PolyglotException e) {
                    handleScriptException(e, scriptName);
                    throw new RuntimeException(e);
                }
            }

            LOGGER.info("Client scripts loaded, sending ready signal to server");
            ClientPacketWrapper.sendToServer(new MoudPackets.ClientReadyPacket());
        } finally {
            graalContext.leave();
        }
    }

    public void processScriptQueue() {
        if (!initialized.get() || !apiService.isContextValid()) return;

        scriptExecutor.execute(() -> {
            if (apiService.rendering != null) {
                double timestamp = System.nanoTime() / 1_000_000.0;
                apiService.rendering.processAnimationFrames(timestamp);
            }
        });
    }

    public void triggerNetworkEvent(String eventName, String eventData) {
        if (!initialized.get() || shutdown.get()) return;

        scriptExecutor.execute(() -> {
            if (graalContext == null || shutdown.get()) return;

            try {
                graalContext.enter();
                try {
                    apiService.network.triggerEvent(eventName, eventData);
                } finally {
                    graalContext.leave();
                }
            } catch (Exception e) {
                handleScriptException(e, "network event: " + eventName);
            }
        });
    }


    private void handleScriptException(Throwable e, String context) {
        if (e instanceof PolyglotException polyglotException) {
            if (polyglotException.isGuestException()) {
                LOGGER.error("Script error in {}: {}", context, polyglotException.getMessage());
                LOGGER.debug("Stack trace: ", polyglotException);
            } else {
                LOGGER.error("Host error during script execution in {}: {}", context, polyglotException.getMessage());
            }
        } else {
            LOGGER.error("Unexpected error in script context '{}': {}", context, e.getMessage(), e);
        }
    }

    public boolean isInitialized() {
        return initialized.get() && !shutdown.get();
    }

    public Context getContext() {
        return graalContext;
    }

    public ExecutorService getExecutor() {
        return scriptExecutor;
    }

    public void executePriority(Runnable runnable) {
        if (scriptExecutor.isShutdown()) {
            return;
        }
        BlockingQueue<Runnable> queue = scriptExecutor.getQueue();
        if (queue instanceof LinkedBlockingDeque<Runnable> deque) {
            if (!deque.offerFirst(runnable)) {
                LOGGER.warn("Failed to enqueue priority task; queue size={}", deque.size());
            }
        } else {
            scriptExecutor.execute(runnable);
        }
    }

    public void shutdown() {
        if (!shutdown.compareAndSet(false, true)) {
            return;
        }

        LOGGER.info("Shutting down client scripting runtime...");

        activeTimers.forEach((id, future) -> future.cancel(false));
        activeTimers.clear();

        if (graalContext != null) {
            try {
                graalContext.close(true);
            } catch (Exception e) {
                LOGGER.error("Error closing GraalVM context", e);
            }
            graalContext = null;
        }

        shutdownExecutor(scriptExecutor, "Script Executor");
        shutdownExecutor(timerExecutor, "Timer Executor");

        initialized.set(false);
        LOGGER.info("Client scripting runtime shut down");
    }

    private void shutdownExecutor(ExecutorService executor, String name) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(3, TimeUnit.SECONDS)) {
                LOGGER.warn("{} did not terminate gracefully, forcing shutdown", name);
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            LOGGER.warn("Interrupted while waiting for {} to terminate", name);
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    public void processAnimationFrameQueue() {
        if (!initialized.get() || !apiService.isContextValid() || apiService.rendering == null) return;


        scriptExecutor.execute(() -> {
            double timestamp = System.nanoTime() / 1_000_000.0;
            apiService.rendering.processAnimationFrames(timestamp);
        });
    }

    public void processGeneralTaskQueue() {
        if (!initialized.get()) return;

        scriptExecutor.execute(() -> {
            while (!scriptTaskQueue.isEmpty()) {
                Runnable task = scriptTaskQueue.poll();
                if (task != null) {
                    try {
                        task.run();
                    } catch (Exception e) {
                        LOGGER.error("Error processing queued script task", e);
                    }
                }
            }
        });
    }

}
