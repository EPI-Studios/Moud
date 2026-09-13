package com.meekdev.moud.script.vm;

import com.meekdev.moud.core.clazz.ClassRegistry;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.script.api.Game;
import com.meekdev.moud.script.bind.InstanceSignals;
import com.meekdev.moud.script.api.AudioRef;
import com.meekdev.moud.script.api.BlockRef;
import com.meekdev.moud.script.api.CameraRef;
import com.meekdev.moud.script.api.FileRef;
import com.meekdev.moud.script.api.InputRef;
import com.meekdev.moud.script.api.PlayerRef;
import com.meekdev.moud.script.bind.CameraMethods;
import com.meekdev.moud.script.api.ModuleSource;
import com.meekdev.moud.script.api.PostRef;
import com.meekdev.moud.script.api.ChatRef;
import com.meekdev.moud.script.api.HistoryRef;
import com.meekdev.moud.script.api.StoreRef;
import com.meekdev.moud.script.bind.Audio;
import com.meekdev.moud.script.bind.Blocks;
import com.meekdev.moud.script.bind.Remotes;
import com.meekdev.moud.script.bind.Scenes;
import com.meekdev.moud.script.bind.Inputs;
import com.meekdev.moud.script.bind.Players;
import com.meekdev.moud.script.bind.Proxies;
import com.meekdev.moud.script.bind.Callbacks;
import com.meekdev.moud.script.bind.Chat;
import com.meekdev.moud.script.bind.History;
import com.meekdev.moud.script.bind.Signals;
import com.meekdev.moud.script.bind.Stores;
import com.meekdev.moud.script.bind.SoundMethods;
import com.meekdev.moud.script.bind.Tags;
import com.meekdev.moud.script.reload.Persist;
import com.meekdev.moud.script.sched.Ownership;
import com.meekdev.moud.script.sched.Scheduler;
import com.meekdev.moud.core.tween.Tween;
import com.meekdev.moud.script.bind.TweenMethods;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import com.meekdev.moud.script.bind.Values;
import com.meekdev.moud.script.engine.ScriptEngine;
import com.meekdev.moud.script.err.ScriptError;
import net.hollowcube.luau.BuilinLibrary;
import java.util.function.Supplier;
import net.hollowcube.luau.LuaFunc;
import net.hollowcube.luau.LuaState;
import net.hollowcube.luau.compiler.LuauCompileException;
import net.hollowcube.luau.compiler.LuauCompiler;

public final class Vm implements ScriptEngine {

    // no io, no os, no package: a place reaches the world through our api or not at all
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
    private final Scheduler scheduler;
    private AudioRef audio;
    private Tags tags;
    private ScriptInstances scripts;
    private final List<Tween> tweens = new ArrayList<>();
    private ModuleSource modules;
    private boolean client;
    private final Signals.Handlers beat = new Signals.Handlers();
    private final Signals.Handlers bar = new Signals.Handlers();

    public Vm() {
        state = LuaState.newState();
        state.openLibs(LIBRARIES);
        scheduler = new Scheduler(state, e -> onError.accept(e));
    }

    private Instance world;
    private ClassRegistry registry;

    public void bind(Instance world, ClassRegistry registry) {
        this.world = world;
        this.registry = registry;
        Values.install(state);
        Signals.install(state);
        Players.install(state);
        InstanceSignals.install(state, e -> onError.accept(e));
        Callbacks.install(state, e -> onError.accept(e));
        Proxies.install(state, registry);
        SoundMethods.install(state);
        TweenMethods.install(state, tweens, e -> onError.accept(e));
        game.install(state, world);
        tags = new Tags(state, world.tree(), e -> onError.accept(e));
        tags.install();
        scheduler.install(state);
        run("task", scheduler.prelude());
    }

    // the client half of the surface, which only exists where there is a screen and someone
    // looking at it. a server vm never sees these globals rather than seeing dead ones
    @Override
    public void bindPost(PostRef post, boolean client) {
        this.client = client;
        Remotes.install(state, post, client);
    }

    // the client's half only: a server has no speakers
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
    public void bindChat(ChatRef chat) {
        chat().install(chat);
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

    // the level's blocks, so a ray from the world can stop at a wall
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

        // the body this client drives, and the only thing that tells it apart from everyone
        // else's. a call rather than a field: a respawn and a reload both hand out a new one, and
        // a reference held across either points at something destroyed
        state.getGlobal("game");
        state.rawGetField(-1, "players");
        state.pushFunction(LuaFunc.wrap(s -> {
            Instance character = own.get();
            if (character == null || !character.isAlive()) {
                s.pushNil();
            } else {
                Proxies.push(s, character);
            }
            return 1;
        }, "players.me"));
        state.rawSetField(-2, "me");
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

    // no delta: a reload is not a step, and a handler that took one was handed a zero to ignore
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

    // a script error kills its handler, not the game
    public void onError(Consumer<ScriptError> handler) {
        onError = handler;
    }

    // Script instances on a server vm and LocalScript ones on a client, started and stopped as the tree
    // changes. asked for once the vm knows its side and where modules come from
    private void stepTweens(double dt) {
        if (tweens.isEmpty()) return;
        // stepped from a copy, because a completed handler may start another tween
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
        fire(game.stepped(), dt);
    }

    public void renderStep(double dt) {
        if (scripts != null && client) scripts.poll(world.tree());
        // per frame on a client, so a tween is as smooth as the screen
        if (client) stepTweens(dt);
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
        // before the state goes, because what is keyed by it cannot be dropped after: a state's
        // identity is a native pointer, and the next state may be handed the same one
        Remotes.forget(state);
        Callbacks.forget(state);
        if (scripts != null) scripts.stopAll();
        Ownership.forget(state);
        if (tags != null) tags.close();
        Proxies.forgetBlocks(state);
        state.close();
    }
}
