package com.meekdev.moud.script.vm;

import com.meekdev.moud.core.asset.Res;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.script.LocalScript;
import com.meekdev.moud.core.script.Script;
import com.meekdev.moud.script.api.ModuleSource;
import com.meekdev.moud.script.bind.Proxies;
import com.meekdev.moud.script.err.ScriptError;
import com.meekdev.moud.script.sched.Ownership;
import com.meekdev.moud.script.sched.Scheduler;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import net.hollowcube.luau.LuaState;
import net.hollowcube.luau.compiler.LuauCompileException;
import net.hollowcube.luau.compiler.LuauCompiler;

final class ScriptInstances {

    private final LuaState state;
    private final Scheduler scheduler;
    private final ModuleSource modules;
    private final boolean client;
    private final Consumer<ScriptError> onError;
    private final Set<Instance> running = Collections.newSetFromMap(new IdentityHashMap<>());

    ScriptInstances(LuaState state, Scheduler scheduler, ModuleSource modules, boolean client, Consumer<ScriptError> onError) {
        this.state = state;
        this.scheduler = scheduler;
        this.modules = modules;
        this.client = client;
        this.onError = onError;
    }

    void poll(InstanceTree tree) {
        for (Instance instance : new ArrayList<>(running)) {
            if (!instance.isAlive() || instance.tree() != tree || !enabled(instance)) {
                running.remove(instance);
                Ownership.of(state).release(instance);
            }
        }
        List<? extends Instance> scripts = client ? tree.ofClass(Classes.LOCAL_SCRIPT) : tree.ofClass(Classes.SCRIPT);
        for (Instance instance : new ArrayList<>(scripts)) {
            if (enabled(instance) && running.add(instance)) start(instance);
        }
    }

    private static boolean enabled(Instance instance) {
        return switch (instance) {
            case Script s -> s.enabled;
            case LocalScript s -> s.enabled;
            default -> false;
        };
    }

    private void start(Instance instance) {
        String name = where(instance);
        String code;
        try {
            code = source(instance);
        } catch (IllegalArgumentException e) {
            onError.accept(new ScriptError(name, e.getMessage(), e));
            return;
        }
        if (code == null) {
            onError.accept(new ScriptError(name, "has no code and its source is not there", null));
            return;
        }
        byte[] bytecode;
        try {
            bytecode = LuauCompiler.DEFAULT.compile("local script = ...; " + code);
        } catch (LuauCompileException e) {
            onError.accept(new ScriptError(name, e.getMessage(), e));
            return;
        }
        LuaState thread = state.newThread();
        int ref = state.ref(-1);
        state.pop(1);
        thread.load(name, bytecode);
        Proxies.push(thread, instance);
        scheduler.start(thread, ref, 1, instance);
    }

    private String source(Instance instance) {
        String code = instance instanceof Script s ? s.code : ((LocalScript) instance).code;
        if (!code.isEmpty()) return code;
        String source = instance instanceof Script s ? s.source : ((LocalScript) instance).source;
        if (source.isEmpty()) return null;
        return modules.read(Res.parse(source));
    }

    private static String where(Instance instance) {
        StringBuilder path = new StringBuilder(instance.name());
        for (Instance at = instance.parent(); at != null; at = at.parent()) path.insert(0, at.name() + "/");
        return path.toString();
    }

    void stopAll() {
        for (Instance instance : running) Ownership.of(state).release(instance);
        running.clear();
    }
}
