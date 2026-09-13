package com.meekdev.moud.script.vm;

import com.meekdev.moud.core.clazz.ClassRegistry;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.nav.Walkers;
import com.meekdev.moud.core.tween.Tween;
import com.meekdev.moud.script.api.AudioRef;
import com.meekdev.moud.script.api.BlockRef;
import com.meekdev.moud.script.api.CameraRef;
import com.meekdev.moud.script.api.ChatRef;
import com.meekdev.moud.script.api.DebugRef;
import com.meekdev.moud.script.api.FileRef;
import com.meekdev.moud.script.api.Game;
import com.meekdev.moud.script.api.HistoryRef;
import com.meekdev.moud.script.api.InputRef;
import com.meekdev.moud.script.api.ModuleSource;
import com.meekdev.moud.script.api.PlayerRef;
import com.meekdev.moud.script.api.PostRef;
import com.meekdev.moud.script.api.StoreRef;
import com.meekdev.moud.script.bind.Callbacks;
import com.meekdev.moud.script.bind.InstanceSignals;
import com.meekdev.moud.script.bind.LuaTables;
import com.meekdev.moud.script.bind.Profiler;
import com.meekdev.moud.script.bind.Proxies;
import com.meekdev.moud.script.bind.Signals;
import com.meekdev.moud.script.bind.Values;
import com.meekdev.moud.script.bind.audio.Audio;
import com.meekdev.moud.script.bind.audio.SoundMethods;
import com.meekdev.moud.script.bind.chat.Chat;
import com.meekdev.moud.script.bind.data.Stores;
import com.meekdev.moud.script.bind.debug.DebugBinding;
import com.meekdev.moud.script.bind.java.Java;
import com.meekdev.moud.script.bind.java.Mixins;
import com.meekdev.moud.script.bind.player.BodyMethods;
import com.meekdev.moud.script.bind.player.CameraMethods;
import com.meekdev.moud.script.bind.player.Inputs;
import com.meekdev.moud.script.bind.player.Players;
import com.meekdev.moud.script.bind.remote.Remotes;
import com.meekdev.moud.script.bind.tween.TweenMethods;
import com.meekdev.moud.script.bind.world.Blocks;
import com.meekdev.moud.script.bind.world.History;
import com.meekdev.moud.script.bind.world.Paths;
import com.meekdev.moud.script.bind.world.Scenes;
import com.meekdev.moud.script.bind.world.Tags;
import com.meekdev.moud.script.bind.world.TreeMethods;
import com.meekdev.moud.script.bind.world.WorldMethods;
import com.meekdev.moud.script.bind.world.ZoneMethods;
import com.meekdev.moud.script.engine.ScriptEngine;
import com.meekdev.moud.script.err.ScriptError;
import com.meekdev.moud.script.reload.Persist;
import com.meekdev.moud.script.sched.Ownership;
import com.meekdev.moud.script.sched.Scheduler;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.hollowcube.luau.BuilinLibrary;
import net.hollowcube.luau.LuaState;
import net.hollowcube.luau.compiler.LuauCompileException;
import net.hollowcube.luau.compiler.LuauCompiler;

public final class Vm implements ScriptEngine {

    private static final BuilinLibrary[] LIBRARIES = {
        BuilinLibrary.BASE,
        BuilinLibrary.COROUTINE,
        BuilinLibrary.TABLE,
        BuilinLibrary.STRING,
        BuilinLibrary.MATH,
        BuilinLibrary.BIT32,
        BuilinLibrary.BUFFER,
        BuilinLibrary.UTF8,
        BuilinLibrary.VECTOR,
    };

    private final LuaState state;
    private final Game game = new Game();
    private Chat chat;
    private Consumer<ScriptError> onError = e -> { throw e; };
    private Consumer<String> printer = System.out::println;
    private final Scheduler scheduler;
    private AudioRef audio;
    private Tags tags;
    private ScriptInstances scripts;
    private final List<Tween> tweens = new ArrayList<>();
    private ModuleSource modules;
    private boolean client;
    private final Signals.Handlers beat = new Signals.Handlers();
    private final Signals.Handlers bar = new Signals.Handlers();
    private Instance world;
    private ClassRegistry registry;

    public Vm() {
        state = LuaState.newState();
        state.openLibs(LIBRARIES);
        LuaTables.global(state, "print", s -> {
            StringBuilder line = new StringBuilder();
            for (int n = 1; n <= s.top(); n++) {
                if (n > 1) line.append('\t');
                line.append(s.toStringRepr(n));
            }
            printer.accept(line.toString());
            return 0;
        });
        scheduler = new Scheduler(state, e -> onError.accept(e));
    }

    public void bind(Instance world, ClassRegistry registry) {
        this.world = world;
        this.registry = registry;
        Values.install(state);
        Signals.install(state);
        Players.install(state);
        InstanceSignals.install(state, e -> onError.accept(e));
        Java.install(state);
        Mixins.install(state, e -> onError.accept(e));
        Callbacks.install(state, e -> onError.accept(e));
        Proxies.install(state, registry);
        SoundMethods.install(state);
        BodyMethods.install(state, world);
        WorldMethods.install(state);
        TreeMethods.install(state);
        TweenMethods.install(state, tweens, e -> onError.accept(e));
        game.install(state, world);
        ZoneMethods.install(state, world);
        Paths.install(state, world);
        tags = new Tags(state, world.tree(), e -> onError.accept(e));
        tags.install();
        scheduler.install(state);
        run("task", scheduler.prelude());
        run("proximity", Luau.source("proximity.luau"));
        run("math", Luau.source("math.luau"));
        run("tree", Luau.source("tree.luau"));
        run("timing", Luau.source("timing.luau"));
    }

    @Override
    public void bindPost(PostRef post, boolean client) {
        this.client = client;
        Remotes.install(state, post, client);
    }

    @Override
    public void bindAudio(AudioRef audio) {
        this.audio = audio;
        Audio.install(state, audio, beat, bar);
    }

    @Override
    public void bindHistory(HistoryRef history) {
        History.install(state, history);
    }

    @Override
    public void bindDebug(DebugRef debug) {
        DebugBinding.install(state, debug);
    }

    @Override
    public void bindChat(ChatRef chat) {
        chat().install(chat);
        run("chat", Luau.source("chat.luau"));
    }

    @Override
    public Object[] chatHook(String name, Object... args) {
        return chat == null ? null : chat.hook(name, args);
    }

    @Override
    public void chatEvent(String name, Object... args) {
        if (chat != null) chat.fire(name, args);
    }

    private Chat chat() {
        if (chat == null) chat = new Chat(state, e -> onError.accept(e));
        return chat;
    }

    @Override
    public void bindStore(StoreRef store) {
        Stores.install(state, store);
        run("store", Luau.source("store.luau"));
    }

    @Override
    public void bindFiles(FileRef files) {
        Scenes.install(state, world, registry, files);
    }

    @Override
    public void bindBlocks(BlockRef blocks) {
        Proxies.blocks(state, blocks);
        Blocks.install(state, blocks);
    }

    @Override
    public void bindModules(ModuleSource source) {
        this.modules = source;
        Modules.install(state, source);
    }

    public void bindClient(Instance camera, CameraRef lens, InputRef input,
            Supplier<Instance> own) {
        Inputs.install(state);
        CameraMethods.install(state, lens);
        Proxies.push(state, camera);
        state.setGlobal("camera");
        Inputs.push(state, input);
        state.setGlobal("input");

        state.getGlobal("game");
        state.rawGetField(-1, "players");
        LuaTables.function(state, "players", "me", s -> {
            Instance character = own.get();
            if (character == null || !character.isAlive()) {
                s.pushNil();
            } else {
                Proxies.push(s, character);
            }
            return 1;
        });
        state.pop(2);
    }

    @Override
    public int sleepingTasks() {
        return scheduler.sleepingCount();
    }

    public Scheduler scheduler() {
        return scheduler;
    }

    public Map<String, Object> persist() {
        return Persist.capture(state);
    }

    public void persist(Map<String, Object> data) {
        Persist.restore(state, data);
    }

    public void reloaded() {
        Signals.fire(state, game.reloaded(), onError, s -> 0);
    }

    public void joined(PlayerRef player) {
        Signals.fire(state, game.joined(), onError, s -> {
            Players.push(s, player);
            return 1;
        });
    }

    public void leaving(PlayerRef player) {
        Signals.fire(state, game.leaving(), onError, s -> {
            Players.push(s, player);
            return 1;
        });
    }

    public void onError(Consumer<ScriptError> handler) {
        onError = handler;
    }

    @Override
    public void onPrint(Consumer<String> handler) {
        printer = handler;
    }

    private void stepTweens(double dt) {
        if (tweens.isEmpty()) return;
        for (Tween tween : new ArrayList<>(tweens)) {
            try {
                if (!tween.step(dt)) tweens.remove(tween);
            } catch (RuntimeException e) {
                tweens.remove(tween);
                onError.accept(new ScriptError("tween", e.getMessage(), e));
            }
        }
    }

    @Override
    public void runScripts() {
        scripts = new ScriptInstances(state, scheduler, modules == null ? path -> null : modules, client,
                e -> onError.accept(e));
        scripts.poll(world.tree());
    }

    public void step(double dt) {
        if (scripts != null) scripts.poll(world.tree());
        if (!client) stepTweens(dt);
        scheduler.advance(dt);
        Blocks.drain(state, world, onError);
        Walkers.step(Paths.finder(state, world), System.nanoTime() / 1e9);
        fire(game.stepped(), dt);
    }

    public void renderStep(double dt) {
        if (scripts != null && client) scripts.poll(world.tree());
        if (client) stepTweens(dt);
        if (client) Blocks.drain(state, world, onError);
        if (audio != null) audio.drainBeats(n -> fire(beat, n), n -> fire(bar, n));
        fire(game.renderStepped(), dt);
    }

    private void fire(Signals.Handlers signal, double dt) {
        Signals.fire(state, signal, onError, s -> {
            s.pushNumber(dt);
            return 1;
        });
    }

    public void run(String chunkName, String source) {
        byte[] bytecode;
        try {
            bytecode = LuauCompiler.DEFAULT.compile(source);
        } catch (LuauCompileException e) {
            throw new ScriptError(chunkName, e.getMessage(), e);
        }
        state.load(chunkName, bytecode);
        state.call(0, 0);
    }

    public double number(String global) {
        state.getGlobal(global);
        double value = state.toNumber(-1);
        state.pop(1);
        return value;
    }

    public boolean bool(String global) {
        state.getGlobal(global);
        boolean value = state.toBoolean(-1);
        state.pop(1);
        return value;
    }

    public String text(String global) {
        state.getGlobal(global);
        String value = state.toString(-1);
        state.pop(1);
        return value;
    }

    @Override
    public void close() {
        Remotes.forget(state);
        Callbacks.forget(state);
        Mixins.forget(state);
        InstanceSignals.forget(state);
        Profiler.forget(state);
        Blocks.forget(state);
        if (world != null) Paths.forget(world);
        if (scripts != null) scripts.stopAll();
        Ownership.forget(state);
        if (tags != null) tags.close();
        Proxies.forgetBlocks(state);
        state.close();
    }
}
