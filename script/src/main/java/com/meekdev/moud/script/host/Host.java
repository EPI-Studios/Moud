package com.meekdev.moud.script.host;

import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.clazz.Classes;
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
import com.meekdev.moud.script.api.ControlsRef;
import com.meekdev.moud.script.api.CoreGuiRef;
import com.meekdev.moud.script.api.DebugRef;
import com.meekdev.moud.script.api.DevicesRef;
import com.meekdev.moud.script.api.FileRef;
import com.meekdev.moud.script.api.GameRef;
import com.meekdev.moud.script.api.HistoryRef;
import com.meekdev.moud.script.api.InputRef;
import com.meekdev.moud.script.api.InvokeRef;
import com.meekdev.moud.script.api.MemoryRef;
import com.meekdev.moud.script.api.ModuleSource;
import com.meekdev.moud.script.api.PlayerRef;
import com.meekdev.moud.script.api.PartPhysicsRef;
import com.meekdev.moud.script.api.PostRef;
import com.meekdev.moud.script.api.HttpRef;
import com.meekdev.moud.script.api.ImagesRef;
import com.meekdev.moud.script.api.PushRef;
import com.meekdev.moud.script.api.ToolRef;
import com.meekdev.moud.script.api.RosterRef;
import com.meekdev.moud.script.api.SettingsRef;
import com.meekdev.moud.script.api.ShaderRef;
import com.meekdev.moud.script.api.SpawnRef;
import com.meekdev.moud.script.api.StoreRef;
import com.meekdev.moud.script.api.WindowRef;
import com.meekdev.moud.script.engine.ScriptEngine;
import com.meekdev.moud.script.engine.ScriptLanguage;
import com.meekdev.moud.script.err.ScriptError;
import com.meekdev.moud.script.host.chat.ChatLibrary;
import com.meekdev.moud.script.host.player.Players;
import com.meekdev.moud.script.host.player.UserInput;
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
    private final RenderSteps renderBindings = new RenderSteps(this);

    private final HostSignal stepped = new HostSignal(this, "StepSignal", "stepped");
    private final HostSignal renderStepped = new HostSignal(this, "StepSignal", "renderStepped");
    private final HostSignal reloaded = new HostSignal(this, "AnySignal", "reloaded");
    private final HostSignal joined = new HostSignal(this, "PlayerSignal", "joined");
    private final HostSignal leaving = new HostSignal(this, "PlayerSignal", "leaving");
    private final HostSignal spawned = new HostSignal(this, "PlayerSignal", "spawned");
    private final HostSignal pauseRequested = new HostSignal(this, "PauseSignal", "pauseRequested");

    private Consumer<ScriptError> onError = e -> { throw e; };
    private Consumer<String> printer = System.out::println;
    private ScriptEngine engine;
    private ScriptLanguage language;
    private ScriptValue persist;
    private Map<String, Object> carried = Map.of();
    private ChatLibrary chatLibrary;

    private PostRef post;
    private InvokeRef invoke;
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
    private DevicesRef devices;
    private UserInput userInput;
    private ShaderRef shaders;
    private SpawnRef spawns;
    private ControlsRef controls;
    private PushRef push;
    private PartPhysicsRef physics;
    private ToolRef tools;
    private HttpRef http;
    private MemoryRef memory;
    private ImagesRef images;
    private GameRef game;
    private SettingsRef settings;
    private WindowRef window;
    private CoreGuiRef coreGui;
    private RosterRef roster;
    private HostSignal windowClosing;
    private Instance camera;
    private Supplier<Instance> own = () -> null;
    private boolean studio;

    public Host(Instance world, ClassRegistry classes, boolean client) {
        this.world = world;
        this.classes = classes;
        this.client = client;
        this.instances = new InstanceAccess(this);
    }

    public static Api describe(ClassRegistry classes) {
        Api api = new Api();
        for (boolean client : new boolean[] {false, true}) {
            Instance world = Instances.createRoot(new InstanceTree(), Classes.SPATIAL, "World");
            Host host = new Host(world, classes, client)
                    .post(inert(PostRef.class))
                    .invoke(inert(InvokeRef.class))
                    .blocks(inert(BlockRef.class))
                    .modules(inert(ModuleSource.class))
                    .files(inert(FileRef.class))
                    .store(inert(StoreRef.class))
                    .chat(inert(ChatRef.class))
                    .history(inert(HistoryRef.class))
                    .debug(inert(DebugRef.class))
                    .audio(inert(AudioRef.class))
                    .shaders(inert(ShaderRef.class))
                    .push(inert(PushRef.class));
            if (client) {
                Instance camera = Instances.createLocal(Classes.CAMERA, world, "Camera");
                host.clientSide(camera, inert(CameraRef.class), inert(InputRef.class), () -> null)
                        .controls(inert(ControlsRef.class))
                        .game(inert(GameRef.class))
                        .settings(inert(SettingsRef.class))
                        .window(inert(WindowRef.class))
                        .coreGui(inert(CoreGuiRef.class))
                        .devices(inert(DevicesRef.class));
            } else {
                host.http(inert(HttpRef.class))
                        .tools(inert(ToolRef.class))
                        .physics(inert(PartPhysicsRef.class))
                        .spawns(inert(SpawnRef.class))
                        .roster(inert(RosterRef.class));
            }
            try {
                Libraries.install(host);
                api.merge(host.api());
            } finally {
                host.close();
            }
        }
        return api;
    }

    private static <T> T inert(Class<T> type) {
        return type.cast(java.lang.reflect.Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (proxy, method, args) -> {
            Class<?> result = method.getReturnType();
            if (result == boolean.class) return false;
            if (result == int.class) return 0;
            if (result == long.class) return 0L;
            if (result == double.class) return 0.0;
            if (result == float.class) return 0.0f;
            if (result == short.class) return (short) 0;
            if (result == byte.class) return (byte) 0;
            if (result == char.class) return '\0';
            return null;
        }));
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
    public HostSignal spawnedSignal() { return spawned; }
    public HostSignal pauseSignal() { return pauseRequested; }

    public PostRef post() { return post; }
    public InvokeRef invoke() { return invoke; }
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
    public DevicesRef devices() { return devices; }
    public ShaderRef shaders() { return shaders; }
    public SpawnRef spawns() { return spawns; }
    public ControlsRef controls() { return controls; }
    public PushRef push() { return push; }
    public PartPhysicsRef physics() { return physics; }
    public ToolRef tools() { return tools; }
    public HttpRef http() { return http; }
    public MemoryRef memory() { return memory; }
    public ImagesRef images() { return images; }
    public GameRef game() { return game; }
    public SettingsRef settings() { return settings; }
    public WindowRef window() { return window; }
    public CoreGuiRef coreGui() { return coreGui; }
    public RosterRef roster() { return roster; }
    public HostSignal windowClosingSignal() { return windowClosing; }
    void windowClosing(HostSignal signal) { windowClosing = signal; }
    public Instance camera() { return camera; }
    public Supplier<Instance> own() { return own; }
    public boolean studio() { return studio; }
    RenderSteps renderBindings() { return renderBindings; }

    public Host post(PostRef post) { this.post = post; return this; }
    public Host invoke(InvokeRef invoke) { this.invoke = invoke; return this; }
    public Host blocks(BlockRef blocks) { this.blocks = blocks; return this; }
    public Host modules(ModuleSource modules) { this.modules = modules; return this; }
    public Host files(FileRef files) { this.files = files; return this; }
    public Host store(StoreRef store) { this.store = store; return this; }
    public Host chat(ChatRef chat) { this.chat = chat; return this; }
    public Host history(HistoryRef history) { this.history = history; return this; }
    public Host debug(DebugRef debug) { this.debug = debug; return this; }
    public Host audio(AudioRef audio) { this.audio = audio; return this; }
    public Host shaders(ShaderRef shaders) { this.shaders = shaders; return this; }
    public Host spawns(SpawnRef spawns) { this.spawns = spawns; return this; }
    public Host controls(ControlsRef controls) { this.controls = controls; return this; }
    public Host push(PushRef push) { this.push = push; return this; }
    public Host physics(PartPhysicsRef physics) { this.physics = physics; return this; }
    public Host tools(ToolRef tools) { this.tools = tools; return this; }
    public Host http(HttpRef http) { this.http = http; return this; }
    public Host memory(MemoryRef memory) { this.memory = memory; return this; }
    public Host images(ImagesRef images) { this.images = images; return this; }
    public Host game(GameRef game) { this.game = game; return this; }
    public Host settings(SettingsRef settings) { this.settings = settings; return this; }
    public Host window(WindowRef window) { this.window = window; return this; }
    public Host coreGui(CoreGuiRef coreGui) { this.coreGui = coreGui; return this; }
    public Host roster(RosterRef roster) { this.roster = roster; return this; }
    public Host devices(DevicesRef devices) { this.devices = devices; return this; }
    public void userInput(UserInput events) { userInput = events; }
    public Host studio(boolean studio) { this.studio = studio; return this; }

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
        api.extension(name, members.typeName());
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
        if (!client) scheduler.advance(dt);
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
        if (client) {
            instances.scripts().poll(world.tree());
            scheduler.advance(dt);
        }
        renderBindings.beforeCamera(dt);
        for (Consumer<Double> step : renderSteps) {
            try {
                step.accept(dt);
            } catch (RuntimeException e) {
                error("renderStep", e);
            }
        }
        renderStepped.fire(dt);
    }

    public void cameraUpdated() {
        renderBindings.afterCamera();
    }

    public boolean windowClosing() {
        if (windowClosing == null || windowClosing.count() == 0) return false;
        windowClosing.fire();
        return true;
    }

    public boolean pauseRequested(String reason) {
        if (pauseRequested.count() == 0) return false;
        pauseRequested.fire(reason);
        return true;
    }

    public boolean input(DevicesRef.Event event) {
        return userInput != null && userInput.dispatch(event);
    }

    public void gamepad(int index, boolean connected) {
        if (userInput != null) userInput.gamepad(index, connected);
    }

    public void reloaded() {
        reloaded.fire();
    }

    public void joined(PlayerRef player) {
        joined.fire(Players.wrap(this, player));
    }

    public void spawned(PlayerRef player) {
        spawned.fire(Players.wrap(this, player));
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
        renderBindings.clear();
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
