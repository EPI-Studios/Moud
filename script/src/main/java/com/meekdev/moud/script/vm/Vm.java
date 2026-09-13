package com.meekdev.moud.script.vm;

import com.meekdev.moud.core.clazz.ClassRegistry;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.script.api.Game;
import com.meekdev.moud.script.bind.InstanceSignals;
import com.meekdev.moud.script.api.AudioRef;
import com.meekdev.moud.script.api.BlockRef;
import com.meekdev.moud.script.api.CameraRef;
import com.meekdev.moud.script.api.InputRef;
import com.meekdev.moud.script.api.PlayerRef;
import com.meekdev.moud.script.bind.CameraMethods;
import com.meekdev.moud.script.api.ModuleSource;
import com.meekdev.moud.script.api.PostRef;
import com.meekdev.moud.script.bind.Audio;
import com.meekdev.moud.script.bind.Remotes;
import com.meekdev.moud.script.bind.Inputs;
import com.meekdev.moud.script.bind.Players;
import com.meekdev.moud.script.bind.Proxies;
import com.meekdev.moud.script.bind.Signals;
import com.meekdev.moud.script.bind.SoundMethods;
import com.meekdev.moud.script.reload.Persist;
import com.meekdev.moud.script.sched.Scheduler;
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
    private Consumer<ScriptError> onError = e -> { throw e; };
    private final Scheduler scheduler;
    private AudioRef audio;
    private final Signals.Handlers beat = new Signals.Handlers();
    private final Signals.Handlers bar = new Signals.Handlers();

    public Vm() {
        state = LuaState.newState();
        state.openLibs(LIBRARIES);
        scheduler = new Scheduler(state, e -> onError.accept(e));
    }

    public void bind(Instance world, ClassRegistry registry) {
        Values.install(state);
        Signals.install(state);
        Players.install(state);
        InstanceSignals.install(state, e -> onError.accept(e));
        Proxies.install(state, registry);
        SoundMethods.install(state);
        game.install(state, world);
        scheduler.install(state);
        run("task", scheduler.prelude());
    }

    // the client half of the surface, which only exists where there is a screen and someone
    // looking at it. a server vm never sees these globals rather than seeing dead ones
    @Override
    public void bindPost(PostRef post, boolean client) {
        Remotes.install(state, post, client);
    }

    // the client's half only: a server has no speakers
    @Override
    public void bindAudio(AudioRef audio) {
        this.audio = audio;
        Audio.install(state, audio, beat, bar);
    }

    // the level's blocks, so a ray from the world can stop at a wall
    @Override
    public void bindBlocks(BlockRef blocks) {
        Proxies.blocks(state, blocks);
    }

    @Override
    public void bindModules(ModuleSource source) {
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

    public void step(double dt) {
        scheduler.advance(dt);
        fire(game.stepped(), dt);
    }

    public void renderStep(double dt) {
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
        Proxies.forgetBlocks(state);
        state.close();
    }
}
