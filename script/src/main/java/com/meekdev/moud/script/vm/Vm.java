package com.meekdev.moud.script.vm;

import com.meekdev.moud.core.clazz.ClassRegistry;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.script.api.Game;
import com.meekdev.moud.script.bind.InstanceSignals;
import com.meekdev.moud.script.api.CameraRef;
import com.meekdev.moud.script.api.InputRef;
import com.meekdev.moud.script.api.PlayerRef;
import com.meekdev.moud.script.bind.CameraMethods;
import com.meekdev.moud.script.bind.Inputs;
import com.meekdev.moud.script.bind.Players;
import com.meekdev.moud.script.bind.Proxies;
import com.meekdev.moud.script.bind.Signals;
import com.meekdev.moud.script.reload.Persist;
import com.meekdev.moud.script.sched.Scheduler;
import java.util.Map;
import java.util.function.Consumer;
import com.meekdev.moud.script.bind.Values;
import com.meekdev.moud.script.err.ScriptError;
import net.hollowcube.luau.BuilinLibrary;
import net.hollowcube.luau.LuaState;
import net.hollowcube.luau.compiler.LuauCompileException;
import net.hollowcube.luau.compiler.LuauCompiler;

public final class Vm implements AutoCloseable {

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
        game.install(state, world);
        scheduler.install(state);
        run("task", scheduler.prelude());
    }

    // the client half of the surface, which only exists where there is a screen and someone
    // looking at it. a server vm never sees these globals rather than seeing dead ones
    public void bindClient(Instance camera, CameraRef lens, InputRef input) {
        Inputs.install(state);
        CameraMethods.install(state, lens);
        Proxies.push(state, camera);
        state.setGlobal("camera");
        Inputs.push(state, input);
        state.setGlobal("input");
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
        state.close();
    }
}
