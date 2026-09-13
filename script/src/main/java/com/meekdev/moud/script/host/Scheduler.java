package com.meekdev.moud.script.host;

import java.util.ArrayList;
import java.util.List;

public final class Scheduler {

    private static final class Task {
        final int id;
        final Fiber fiber;
        final Object owner;
        Suspend waiting;
        boolean done;

        Task(int id, Fiber fiber, Object owner) {
            this.id = id;
            this.fiber = fiber;
            this.owner = owner;
        }
    }

    private final Host host;
    private final List<Task> sleeping = new ArrayList<>();
    private int nextId = 1;

    Scheduler(Host host) {
        this.host = host;
    }

    public int start(Fiber fiber, Object owner, Object... args) {
        Task task = new Task(nextId++, fiber, owner);
        host.ownership().onRelease(owner, () -> stop(task));
        resume(task, args);
        return task.id;
    }

    public int later(Fiber fiber, Object owner, Suspend first) {
        Task task = new Task(nextId++, fiber, owner);
        host.ownership().onRelease(owner, () -> stop(task));
        task.waiting = first;
        sleeping.add(task);
        return task.id;
    }

    public void cancel(int id) {
        for (Task task : new ArrayList<>(sleeping)) {
            if (task.id == id) stop(task);
        }
    }

    public int sleepingCount() {
        return sleeping.size();
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
                sleeping.remove(task);
                host.error("task " + task.id, e);
                continue;
            }
            if (out == null) continue;
            sleeping.remove(task);
            ready.add(task);
            values.add(out);
        }
        for (int n = 0; n < ready.size(); n++) resume(ready.get(n), values.get(n));
    }

    private void resume(Task task, Object[] args) {
        if (task.done) return;
        Object before = host.ownership().enter(task.owner);
        Suspend next;
        try {
            next = task.fiber.resume(args);
        } catch (RuntimeException e) {
            task.done = true;
            host.error("task " + task.id, e);
            return;
        } finally {
            host.ownership().leave(before);
        }
        if (next == null) {
            task.done = true;
            return;
        }
        task.waiting = next;
        sleeping.add(task);
    }

    private void stop(Task task) {
        if (task.done) return;
        task.done = true;
        if (sleeping.remove(task) && task.waiting != null) task.waiting.cancel().run();
        task.fiber.cancel();
    }

    void stopAll() {
        for (Task task : new ArrayList<>(sleeping)) stop(task);
    }
}
