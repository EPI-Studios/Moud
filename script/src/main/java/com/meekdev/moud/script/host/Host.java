package com.meekdev.moud.script.host;

import com.meekdev.moud.core.clazz.ClassRegistry;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.math.UDim2;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.script.api.AudioRef;
import com.meekdev.moud.script.api.BlockRef;
import com.meekdev.moud.script.api.CameraRef;
import com.meekdev.moud.script.api.ChatRef;
import com.meekdev.moud.script.api.DebugRef;
import com.meekdev.moud.script.api.FileRef;
import com.meekdev.moud.script.api.HistoryRef;
import com.meekdev.moud.script.api.InputRef;
import com.meekdev.moud.script.api.ModuleSource;
import com.meekdev.moud.script.api.PlayerRef;
import com.meekdev.moud.script.api.PostRef;
import com.meekdev.moud.script.api.StoreRef;
import com.meekdev.moud.script.engine.ScriptEngine;
import com.meekdev.moud.script.engine.ScriptLanguage;
import com.meekdev.moud.script.err.ScriptError;
import com.meekdev.moud.script.host.chat.ChatLibrary;
import com.meekdev.moud.script.host.player.Players;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class Host {

    public enum Op { ADD, SUB, MUL, DIV, UNM }

    public record Script(String path, String code) {}

    private final Instance world;
    private final ClassRegistry classes;
    private final boolean client;
    private final Map<String, Object> globals = new LinkedHashMap<>();
    private final Map<String, Members> extensions = new LinkedHashMap<>();
    private final Api api = new Api();
    private final Ownership ownership = new Ownership();
    private final Profiler profiler = new Profiler();
    private final Scheduler scheduler = new Scheduler(this);
    private final InstanceAccess instances;
    private final List<Runnable> closers = new ArrayList<>();
    private final List<Consumer<Double>> steps = new ArrayList<>();
    private final List<Consumer<Double>> renderSteps = new ArrayList<>();

    private final HostSignal stepped = new HostSignal(this, "StepSignal", "stepped");
    private final HostSignal renderStepped = new HostSignal(this, "StepSignal", "renderStepped");
    private final HostSignal reloaded = new HostSignal(this, "AnySignal", "reloaded");
    private final HostSignal joined = new HostSignal(this, "PlayerSignal", "joined");
    private final HostSignal leaving = new HostSignal(this, "PlayerSignal", "leaving");

    private Consumer<ScriptError> onError = e -> { throw e; };
    private Consumer<String> printer = System.out::println;
    private ScriptEngine engine;
    private ScriptLanguage language;
    private ScriptValue persist;
    private Map<String, Object> carried = Map.of();
    private ChatLibrary chatLibrary;

    private PostRef post;
    private BlockRef blocks;
    private ModuleSource modules = path -> null;
    private FileRef files;
    private StoreRef store;
    private ChatRef chat;
    private HistoryRef history;
    private DebugRef debug;
    private AudioRef audio;
    private CameraRef lens;
    private InputRef input;
    private Instance camera;
    private Supplier<Instance> own = () -> null;

    public Host(Instance world, ClassRegistry classes, boolean client) {
        this.world = world;
        this.classes = classes;
        this.client = client;
        this.instances = new InstanceAccess(this);
    }

    public ScriptEngine start(ScriptLanguage language) {
        this.language = language;
        Libraries.install(this);
        engine = language.start(this);
        return engine;
    }

    public Instance world() { return world; }
    public ClassRegistry classes() { return classes; }
    public boolean client() { return client; }
    public Api api() { return api; }
    public Ownership ownership() { return ownership; }
    public Profiler profiler() { return profiler; }
    public Scheduler scheduler() { return scheduler; }
    public InstanceAccess instances() { return instances; }
    public ScriptEngine engine() { return engine; }
    public ScriptLanguage language() { return language; }
    public Map<String, Object> globals() { return globals; }

    public HostSignal stepped() { return stepped; }
    public HostSignal renderStepped() { return renderStepped; }
    public HostSignal reloadedSignal() { return reloaded; }
    public HostSignal joinedSignal() { return joined; }
    public HostSignal leavingSignal() { return leaving; }

    public PostRef post() { return post; }
    public BlockRef blocks() { return blocks; }
    public ModuleSource modules() { return modules; }
    public FileRef files() { return files; }
    public StoreRef store() { return store; }
    public ChatRef chat() { return chat; }
    public HistoryRef history() { return history; }
    public DebugRef debug() { return debug; }
    public AudioRef audio() { return audio; }
    public CameraRef lens() { return lens; }
    public InputRef input() { return input; }
    public Instance camera() { return camera; }
    public Supplier<Instance> own() { return own; }

    public Host post(PostRef post) { this.post = post; return this; }
    public Host blocks(BlockRef blocks) { this.blocks = blocks; return this; }
    public Host modules(ModuleSource modules) { this.modules = modules; return this; }
    public Host files(FileRef files) { this.files = files; return this; }
    public Host store(StoreRef store) { this.store = store; return this; }
    public Host chat(ChatRef chat) { this.chat = chat; return this; }
    public Host history(HistoryRef history) { this.history = history; return this; }
    public Host debug(DebugRef debug) { this.debug = debug; return this; }
    public Host audio(AudioRef audio) { this.audio = audio; return this; }

    public Host clientSide(Instance camera, CameraRef lens, InputRef input, Supplier<Instance> own) {
        this.camera = camera;
        this.lens = lens;
        this.input = input;
        this.own = own;
        return this;
    }

    public Host onError(Consumer<ScriptError> handler) {
        onError = handler;
        return this;
    }

    public Host onPrint(Consumer<String> handler) {
        printer = handler;
        return this;
    }

    public Script readScript(String path) {
        int slash = path.lastIndexOf('/');
        List<String> candidates = path.indexOf('.', slash + 1) > slash + 1 || language == null
                ? List.of(path)
                : language.extensions().stream().map(extension -> path + "." + extension).toList();
        for (String candidate : candidates) {
            String code;
            try {
                code = modules.read(candidate);
            } catch (IllegalArgumentException e) {
                throw new HostError(e.getMessage());
            }
            if (code != null) return new Script(candidate, code);
        }
        return null;
    }

    public String me() {
        return post == null ? "" : post.me();
    }

    ScriptValue persistTable() {
        if (persist == null) persist = engine.table(carried);
        return persist;
    }

    void persistTable(Object value) {
        if (!(value instanceof Map<?, ?> map)) throw new HostError("game.persist must be a table");
        if (persist != null) persist.release();
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) map;
        persist = engine.table(data);
    }

    public Map<String, Object> persist() {
        return persist == null ? carried : engine.read(persist);
    }

    public void persist(Map<String, Object> data) {
        carried = data;
        if (persist != null) {
            persist.release();
            persist = null;
        }
    }

    void chatLibrary(ChatLibrary library) {
        chatLibrary = library;
    }

    public Object[] chatHook(String name, Object... args) {
        return chatLibrary == null ? null : chatLibrary.hook(name, args);
    }

    public void chatEvent(String name, Object... args) {
        if (chatLibrary != null) chatLibrary.fire(name, args);
    }

    public void global(String name, String type, Object value) {
        globals.put(name, value);
        api.global(name, type);
    }

    public void extend(String name, Members members) {
        extensions.put(name, members);
    }

    public Map<String, Members> extensions() {
        return extensions;
    }

    public void declare(Members members) {
        api.declare(members.decl());
    }

    public void onClose(Runnable closer) {
        closers.add(closer);
    }

    public void onStep(Consumer<Double> step) {
        steps.add(step);
    }

    public void onRenderStep(Consumer<Double> step) {
        renderSteps.add(step);
    }

    public void print(String line) {
        printer.accept(line);
    }

    public void error(String where, RuntimeException e) {
        ScriptError error = e instanceof ScriptError script ? script : new ScriptError(where, e.getMessage(), e);
        onError.accept(error);
    }

    public Object[] call(Callable fn, String where, Object... args) {
        long started = System.nanoTime();
        try {
            return fn.call(args);
        } catch (RuntimeException e) {
            error(where, e);
            return null;
        } finally {
            profiler.add(where.equals("signal") ? Profiler.owner(ownership.current()) : where, System.nanoTime() - started);
        }
    }

    public Object first(Callable fn, String where, Object... args) {
        Object[] out = call(fn, where, args);
        return out == null || out.length == 0 ? null : out[0];
    }

    public Object invoke(Builtin fn, Object[] args) {
        return fn.body().call(new Args(this, fn.name(), args));
    }

    public Object index(Object target, String key) {
        return switch (target) {
            case Instance instance -> instances.get(instance, key);
            case HostObject object -> object.get(key);
            case Vector3 v -> Values.get(v, key);
            case CFrame c -> Values.get(c, key);
            case Quat q -> Values.get(q, key);
            case Color c -> Values.get(c, key);
            case UDim2 u -> Values.get(u, key);
            case null -> throw new HostError("attempt to index nil with '%s'", key);
            default -> throw new HostError("%s has no member '%s'", typeOf(target), key);
        };
    }

    public void assign(Object target, String key, Object value) {
        switch (target) {
            case Instance instance -> instances.set(instance, key, value);
            case HostObject object -> object.set(key, value);
            default -> throw new HostError("%s.%s cannot be assigned", typeOf(target), key);
        }
    }

    public Object operate(Op op, Object a, Object b) {
        return Values.operate(op, a, b);
    }

    public boolean equal(Object a, Object b) {
        return a == b || a != null && a.equals(b);
    }

    public String text(Object value) {
        return switch (value) {
            case Instance instance -> instance.name();
            case HostObject object -> object.typeName();
            case null -> "nil";
            default -> Values.text(value);
        };
    }

    public static String typeOf(Object value) {
        return switch (value) {
            case null -> "nil";
            case Boolean b -> "boolean";
            case Number n -> "number";
            case String s -> "string";
            case Vector3 v -> "vec3";
            case CFrame c -> "cframe";
            case Quat q -> "quat";
            case Color c -> "color";
            case UDim2 u -> "udim2";
            case Instance i -> i.def().name();
            case HostObject o -> o.typeName();
            case Callable c -> "function";
            case Builtin b -> "function";
            case List<?> l -> "table";
            case Map<?, ?> m -> "table";
            default -> value.getClass().getSimpleName();
        };
    }

    public void runScripts() {
        instances.scripts().poll(world.tree());
    }

    public void step(double dt) {
        instances.scripts().poll(world.tree());
        scheduler.advance(dt);
        for (Consumer<Double> step : steps) {
            try {
                step.accept(dt);
            } catch (RuntimeException e) {
                error("step", e);
            }
        }
        stepped.fire(dt);
    }

    public void renderStep(double dt) {
        if (client) instances.scripts().poll(world.tree());
        for (Consumer<Double> step : renderSteps) {
            try {
                step.accept(dt);
            } catch (RuntimeException e) {
                error("renderStep", e);
            }
        }
        renderStepped.fire(dt);
    }

    public void reloaded() {
        reloaded.fire();
    }

    public void joined(PlayerRef player) {
        joined.fire(Players.wrap(this, player));
    }

    public void leaving(PlayerRef player) {
        leaving.fire(Players.wrap(this, player));
    }

    public int sleepingTasks() {
        return scheduler.sleepingCount();
    }

    public void close() {
        instances.scripts().stopAll();
        scheduler.stopAll();
        ownership.releaseAll();
        for (Runnable closer : closers) {
            try {
                closer.run();
            } catch (RuntimeException ignored) {
            }
        }
        instances.close();
        if (persist != null) persist.release();
        if (engine != null) engine.close();
    }
}
