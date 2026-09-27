package com.meekdev.moud.script.host;

import java.util.ArrayList;
import java.util.List;

public final class Scheduler {

    static final class Task {
        final int id;
        final Fiber fiber;
        final Object owner;
        Suspend waiting;
        Runnable forget = () -> { };
        boolean done;
        boolean parallel;

        Task(int id, Fiber fiber, Object owner) {
            this.id = id;
            this.fiber = fiber;
            this.owner = owner;
        }
    }

    record Resumption(Task task, Object[] values) {}

    private static final Object[] NOTHING = new Object[0];

    private final Host host;
    private final List<Task> sleeping = new ArrayList<>();
    private List<Resumption> desynced = new ArrayList<>();
    private List<Task> synced = new ArrayList<>();
    private int nextId = 1;

    Scheduler(Host host) {
        this.host = host;
    }

    public int start(Fiber fiber, Object owner, Object... args) {
        Task task = task(fiber, owner);
        resume(task, args);
        return task.id;
    }

    public int later(Fiber fiber, Object owner, Suspend first) {
        Task task = task(fiber, owner);
        task.waiting = first;
        sleeping.add(task);
        return task.id;
    }

    void adopt(Fiber fiber, Object owner, Suspend next) {
        Task task = task(fiber, owner);
        task.parallel = true;
        settle(task, next);
    }

    private Task task(Fiber fiber, Object owner) {
        Task task = new Task(nextId++, fiber, owner);
        task.forget = host.ownership().onRelease(owner, () -> stop(task));
        return task;
    }

    public void cancel(int id) {
        for (Task task : new ArrayList<>(sleeping)) {
            if (task.id == id) stop(task);
        }
        for (Resumption waiting : desynced) {
            if (waiting.task().id == id) stop(waiting.task());
        }
        for (Task task : new ArrayList<>(synced)) {
            if (task.id == id) stop(task);
        }
    }

    public int sleepingCount() {
        return sleeping.size() + desynced.size() + synced.size();
    }

    public void advance(double dt) {
        if (sleeping.isEmpty()) return;
        List<Task> ready = new ArrayList<>();
        List<Object[]> values = new ArrayList<>();
        for (Task task : new ArrayList<>(sleeping)) {
            Object[] out;
            try {
                out = task.waiting.waiter().poll(dt);
            } catch (RuntimeException e) {
                stop(task);
                report(task, e);
                continue;
            }
            if (out == null) continue;
            sleeping.remove(task);
            ready.add(task);
            values.add(out);
        }
        for (int n = 0; n < ready.size(); n++) {
            Task task = ready.get(n);
            if (task.parallel) desynced.add(new Resumption(task, values.get(n)));
            else resume(task, values.get(n));
        }
    }

    List<Resumption> takeDesynced() {
        List<Resumption> taken = desynced;
        desynced = new ArrayList<>();
        taken.removeIf(waiting -> waiting.task().done);
        return taken;
    }

    void resumeSynced() {
        List<Task> taken = synced;
        synced = new ArrayList<>();
        for (Task task : taken) resume(task, NOTHING);
    }

    private void resume(Task task, Object[] args) {
        if (task.done) return;
        Object before = host.ownership().enter(task.owner);
        host.profiler().enter();
        long started = System.nanoTime();
        Suspend next;
        try {
            next = task.fiber.resume(args);
        } catch (RuntimeException e) {
            finish(task);
            host.error("task " + task.id, e);
            return;
        } finally {
            host.profiler().leave(System.nanoTime() - started);
            host.ownership().leave(before);
        }
        settle(task, next);
    }

    void settle(Task task, Suspend next) {
        if (task.done) return;
        if (next == null) {
            finish(task);
            return;
        }
        if (next == Parallel.DESYNCHRONIZE) {
            task.parallel = true;
            desynced.add(new Resumption(task, NOTHING));
            return;
        }
        if (next == Parallel.SYNCHRONIZE) {
            task.parallel = false;
            synced.add(task);
            return;
        }
        task.waiting = next;
        sleeping.add(task);
    }

    void failed(Task task, RuntimeException e) {
        finish(task);
        report(task, e);
    }

    private void report(Task task, RuntimeException e) {
        Object before = host.ownership().enter(task.owner);
        try {
            host.error("task " + task.id, e);
        } finally {
            host.ownership().leave(before);
        }
    }

    private void finish(Task task) {
        task.done = true;
        task.forget.run();
    }

    private void stop(Task task) {
        if (task.done) return;
        finish(task);
        if (sleeping.remove(task) && task.waiting != null) task.waiting.cancel().run();
        synced.remove(task);
        task.fiber.cancel();
    }

    void stopAll() {
        for (Task task : new ArrayList<>(sleeping)) stop(task);
        for (Resumption waiting : desynced) stop(waiting.task());
        for (Task task : new ArrayList<>(synced)) stop(task);
        desynced.clear();
        synced.clear();
    }
}
