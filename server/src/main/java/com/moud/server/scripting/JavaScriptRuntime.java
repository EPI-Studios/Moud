package com.moud.server.scripting;

import com.moud.api.math.Vector3;
import com.moud.api.math.Quaternion;
import com.moud.api.math.Matrix4;
import com.moud.api.math.Transform;
import com.moud.api.math.MathUtils;
import com.moud.api.math.GeometryUtils;
import com.moud.server.ConsoleAPI;
import com.moud.server.MoudEngine;
import com.moud.server.api.exception.APIException;
import com.moud.server.logging.MoudLogger;
import com.moud.server.proxy.CameraLockProxy;
import com.moud.server.proxy.MathProxy;
import com.moud.server.typescript.TypeScriptTranspiler;
import org.graalvm.polyglot.*;
import org.graalvm.polyglot.io.IOAccess;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyExecutable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class JavaScriptRuntime {
    private static final MoudLogger LOGGER = MoudLogger.getLogger(JavaScriptRuntime.class);
    private static final String LANGUAGE_ID = "js";

    private final Context context;
    private final ExecutorService scriptExecutor;
    private final ScheduledExecutorService timerExecutor;
    private final AtomicBoolean running = new AtomicBoolean(true);

    private final AtomicLong timerIdCounter = new AtomicLong(0);
    private final ConcurrentHashMap<Long, ScheduledFuture<?>> activeTimers = new ConcurrentHashMap<>();

    public JavaScriptRuntime(MoudEngine engine) {
        this.scriptExecutor = Executors.newSingleThreadExecutor(r -> new Thread(r, "JavaScriptRuntime-Main"));
        this.timerExecutor = Executors.newScheduledThreadPool(2, r -> new Thread(r, "JavaScriptRuntime-Timer"));
        try {
            this.context = createSecureContext();
            bindTimerFunctionsToContext();
            bindMathUtilities();
        } catch (Exception e) {
            LOGGER.critical("Failed to initialize JavaScript runtime", e);
            throw new APIException("RUNTIME_INIT_FAILED", "Failed to initialize JavaScript runtime", e);
        }
    }

    private Context createSecureContext() throws NoSuchMethodException, NoSuchFieldException {
        HostAccess.Builder hostAccessBuilder = HostAccess.newBuilder()
                .allowAccessAnnotatedBy(HostAccess.Export.class)
                .allowImplementations(ProxyArray.class);

        hostAccessBuilder.allowAccess(CameraLockProxy.class.getConstructor(net.minestom.server.entity.Player.class));

        hostAccessBuilder.allowAccess(Vector3.class.getConstructor());
        hostAccessBuilder.allowAccess(Vector3.class.getConstructor(double.class, double.class, double.class));
        hostAccessBuilder.allowAccess(Vector3.class.getConstructor(float.class, float.class, float.class));
        hostAccessBuilder.allowAccess(Vector3.class.getMethod("add", Vector3.class));
        hostAccessBuilder.allowAccess(Vector3.class.getMethod("multiply", double.class));
        hostAccessBuilder.allowAccess(Vector3.class.getMethod("toString"));
        hostAccessBuilder.allowAccess(Vector3.class.getField("x"));
        hostAccessBuilder.allowAccess(Vector3.class.getField("y"));
        hostAccessBuilder.allowAccess(Vector3.class.getField("z"));

        hostAccessBuilder.allowAccess(Quaternion.class.getConstructor());
        hostAccessBuilder.allowAccess(Quaternion.class.getConstructor(float.class, float.class, float.class, float.class));
        hostAccessBuilder.allowAccess(Quaternion.class.getField("x"));
        hostAccessBuilder.allowAccess(Quaternion.class.getField("y"));
        hostAccessBuilder.allowAccess(Quaternion.class.getField("z"));
        hostAccessBuilder.allowAccess(Quaternion.class.getField("w"));

        hostAccessBuilder.allowAccess(Matrix4.class.getConstructor());
        hostAccessBuilder.allowAccess(Matrix4.class.getField("m"));

        hostAccessBuilder.allowAccess(Transform.class.getConstructor());
        hostAccessBuilder.allowAccess(Transform.class.getField("position"));
        hostAccessBuilder.allowAccess(Transform.class.getField("rotation"));
        hostAccessBuilder.allowAccess(Transform.class.getField("scale"));

        HostAccess hostAccess = hostAccessBuilder.build();

        return Context.newBuilder(LANGUAGE_ID)
                .allowHostAccess(hostAccess)
                .allowIO(IOAccess.NONE)
                .allowNativeAccess(false)
                .allowCreateThread(false)
                .allowCreateProcess(false)
                .allowEnvironmentAccess(EnvironmentAccess.NONE)
                .allowPolyglotAccess(PolyglotAccess.NONE)
                .build();
    }

    private void bindMathUtilities() {
        executeTask(() -> {
            Value bindings = context.getBindings(LANGUAGE_ID);
            bindings.putMember("Math", new MathProxy());
            bindings.putMember("Vector3", Vector3.class);
            bindings.putMember("Quaternion", Quaternion.class);
            bindings.putMember("Matrix4", Matrix4.class);
            bindings.putMember("Transform", Transform.class);
            bindings.putMember("MathUtils", MathUtils.class);
            bindings.putMember("GeometryUtils", GeometryUtils.class);
        });
    }

    private Object convertPolyglotValue(Value value) {
        if (value.isHostObject()) {
            return value.asHostObject();
        }
        if (value.isString()) {
            return value.asString();
        }
        if (value.isNumber()) {
            return value.asDouble();
        }
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        if (value.isNull()) {
            return "null";
        }
        return value.toString();
    }

    public void bindConsole(ConsoleAPI consoleImpl) {
        executeTask(() -> {
            Value bindings = context.getBindings(LANGUAGE_ID);
            Value consoleObj = context.eval(LANGUAGE_ID, "({})");

            ProxyExecutable logProxy = args -> {
                Object[] javaArgs = Arrays.stream(args).map(this::convertPolyglotValue).toArray();
                consoleImpl.log(javaArgs);
                return null;
            };
            ProxyExecutable warnProxy = args -> {
                Object[] javaArgs = Arrays.stream(args).map(this::convertPolyglotValue).toArray();
                consoleImpl.warn(javaArgs);
                return null;
            };
            ProxyExecutable errorProxy = args -> {
                Object[] javaArgs = Arrays.stream(args).map(this::convertPolyglotValue).toArray();
                consoleImpl.error(javaArgs);
                return null;
            };
            ProxyExecutable debugProxy = args -> {
                Object[] javaArgs = Arrays.stream(args).map(this::convertPolyglotValue).toArray();
                consoleImpl.debug(javaArgs);
                return null;
            };

            consoleObj.putMember("log", logProxy);
            consoleObj.putMember("warn", warnProxy);
            consoleObj.putMember("error", errorProxy);
            consoleObj.putMember("debug", debugProxy);

            bindings.putMember("console", consoleObj);
        });
    }

    private void bindTimerFunctionsToContext() {
        Value bindings = context.getBindings(LANGUAGE_ID);
        bindings.putMember("setTimeout", (ProxyExecutable) args -> {
            if (args.length < 2 || !args[0].canExecute() || !args[1].isNumber()) return -1L;
            long id = timerIdCounter.incrementAndGet();
            ScheduledFuture<?> future = timerExecutor.schedule(() -> executeCallback(args[0]), args[1].asLong(), TimeUnit.MILLISECONDS);
            activeTimers.put(id, future);
            return id;
        });

        bindings.putMember("setInterval", (ProxyExecutable) args -> {
            if (args.length < 2 || !args[0].canExecute() || !args[1].isNumber()) return -1L;
            long delay = args[1].asLong();
            if (delay <= 0) return -1L;
            long id = timerIdCounter.incrementAndGet();
            ScheduledFuture<?> future = timerExecutor.scheduleAtFixedRate(() -> executeCallback(args[0]), delay, delay, TimeUnit.MILLISECONDS);
            activeTimers.put(id, future);
            return id;
        });

        ProxyExecutable clearTimeout = (args) -> {
            if (args.length > 0 && args[0].isNumber()) {
                ScheduledFuture<?> future = activeTimers.remove(args[0].asLong());
                if (future != null) future.cancel(false);
            }
            return null;
        };
        bindings.putMember("clearTimeout", clearTimeout);
        bindings.putMember("clearInterval", clearTimeout);
    }

    public void executeTask(Runnable task) {
        if (running.get()) {
            scriptExecutor.execute(task);
        }
    }

    public void executeCallback(Value callback, Object... args) {
        executeTask(() -> {
            try {
                context.enter();
                callback.execute(args);
            } catch (Exception e) {
                handleScriptException(e, "in callback");
            } finally {
                context.leave();
            }
        });
    }
    public void bindGlobal(String name, Object value) {
        executeTask(() -> context.getBindings(LANGUAGE_ID).putMember(name, value));
    }

    public CompletableFuture<Value> executeScript(Path scriptPath) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String content = loadScriptContent(scriptPath);
                Source source = Source.newBuilder(LANGUAGE_ID, content, scriptPath.getFileName().toString()).build();
                LOGGER.info("Executing script: {}", scriptPath.getFileName());
                context.enter();
                try {
                    return context.eval(source);
                } finally {
                    context.leave();
                }
            } catch (Exception e) {
                handleScriptException(e, scriptPath.toString());
                throw new CompletionException(e);
            }
        }, scriptExecutor);
    }

    private String loadScriptContent(Path scriptPath) throws Exception {
        if (scriptPath.toString().endsWith(".ts")) {
            return TypeScriptTranspiler.transpile(scriptPath).get();
        }
        return Files.readString(scriptPath);
    }

    private void handleScriptException(Throwable e, String contextInfo) {
        if (e instanceof PolyglotException polyglotException) {
            SourceSection location = polyglotException.getSourceLocation();
            String fileName = location != null ? location.getSource().getName() : "Unknown Script";
            int line = location != null ? location.getStartLine() : 0;
            int column = location != null ? location.getStartColumn() : 0;

            LOGGER.scriptError("Execution failed in {} at line {}, column {}", fileName, line, column);
            LOGGER.error("└─> {}", polyglotException.getMessage());
            // LOGGER.debug("Guest stack trace:\n{}", polyglotException.getPolyglotStackTrace());
        } else {
            LOGGER.scriptError("An unexpected Java error occurred while executing script code in context: {}", contextInfo, e);
        }
    }

    public void shutdown() {
        if (!running.compareAndSet(true, false)) return;
        LOGGER.shutdown("Shutting down JavaScript runtime...");
        shutdownExecutor(scriptExecutor, "Script Executor");
        shutdownExecutor(timerExecutor, "Timer Executor");
        context.close(true);
    }

    private void shutdownExecutor(ExecutorService executor, String name) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) executor.shutdownNow();
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}