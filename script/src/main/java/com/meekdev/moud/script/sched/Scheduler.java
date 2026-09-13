package com.meekdev.moud.script.sched;

import com.meekdev.moud.script.err.ScriptError;
import com.meekdev.moud.script.vm.Luau;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.hollowcube.luau.LuaFunc;
import net.hollowcube.luau.LuaState;
import net.hollowcube.luau.LuaStatus;

public final class Scheduler {

    private final LuaState main;
    private final Consumer<ScriptError> onError;
    private final List<Task> sleeping = new ArrayList<>();
    private int nextId = 1;

    public Scheduler(LuaState main, Consumer<ScriptError> onError) {
        this.main = main;
        this.onError = onError;
    }

    public String prelude() {
        return Luau.source("task.luau");
    }

    public void install(LuaState state) {
        state.pushFunction(LuaFunc.wrap(this::spawn, "task.spawn"));
        state.setGlobal("__moud_spawn");
        state.pushFunction(LuaFunc.wrap(this::cancel, "task.cancel"));
        state.setGlobal("__moud_cancel");
    }

    public int sleepingCount() {
        return sleeping.size();
    }

    public void advance(double dt) {
        if (sleeping.isEmpty()) return;
        List<Task> ready = null;
        for (int n = sleeping.size() - 1; n >= 0; n--) {
            Task task = sleeping.get(n);
            task.remaining -= dt;
            if (task.remaining <= 0) {
                sleeping.remove(n);
                if (ready == null) ready = new ArrayList<>();
                ready.add(task);
            }
        }
        if (ready == null) return;
        for (Task task : ready) resume(task);
    }

    private int spawn(LuaState state) {
        if (!state.isFunction(1)) throw state.error("task.spawn wants a function");
        LuaState thread = state.newThread();
        int ref = state.ref(-1);
        state.pop(1);
        state.pushValue(1);
        state.xmove(thread, 1);

        Task task = new Task(nextId++, ref, thread, Ownership.of(main).current());
        Ownership.of(main).onRelease(task.owner, () -> stop(task));
        resume(task);
        state.pushInteger(task.id);
        return 1;
    }

    private int cancel(LuaState state) {
        int id = (int) state.checkNumber(1);
        for (int n = 0; n < sleeping.size(); n++) {
            if (sleeping.get(n).id == id) {
                main.unref(sleeping.remove(n).ref);
                return 0;
            }
        }
        return 0;
    }

    public void start(LuaState thread, int ref, int args, Object owner) {
        Task task = new Task(nextId++, ref, thread, owner);
        Ownership.of(main).onRelease(owner, () -> stop(task));
        task.args = args;
        resume(task);
    }

    private void stop(Task task) {
        if (sleeping.remove(task)) main.unref(task.ref);
    }

    private void resume(Task task) {
        LuaStatus status;
        Ownership owners = Ownership.of(main);
        Object before = owners.enter(task.owner);
        int args = task.args;
        task.args = 0;
        try {
            status = task.thread.resume(main, args);
        } catch (RuntimeException e) {
            onError.accept(new ScriptError("task " + task.id, e.getMessage(), e));
            main.unref(task.ref);
            return;
        } finally {
            owners.leave(before);
        }

        if (status == LuaStatus.YIELD) {
            task.remaining = task.thread.isNumber(-1) ? task.thread.toNumber(-1) : 0;
            task.thread.pop(task.thread.top());
            sleeping.add(task);
            return;
        }
        if (status != LuaStatus.OK) {
            onError.accept(new ScriptError("task " + task.id, task.thread.toString(-1), null));
        }
        main.unref(task.ref);
    }

    private static final class Task {
        final int id;
        final int ref;
        final LuaState thread;
        final Object owner;
        double remaining;
        int args;

        Task(int id, int ref, LuaState thread, Object owner) {
            this.id = id;
            this.ref = ref;
            this.thread = thread;
            this.owner = owner;
        }
    }
}
