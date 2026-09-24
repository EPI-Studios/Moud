package com.meekdev.moud.script.host;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.Actor;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.script.engine.ScriptEngine;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class Parallel {

    static final Suspend DESYNCHRONIZE = new Suspend(dt -> new Object[0]);
    static final Suspend SYNCHRONIZE = new Suspend(dt -> new Object[0]);

    private static final long STACK = 16L << 20;

    private record Mail(Instance actor, String topic, Object[] args) {}

    private record Binding(Callable fn, Object owner, boolean parallel, Connection connection) {}

    private record Call(Callable fn, Object[] args, Object owner, Connection connection) {}

    private record Outcome(Scheduler.Task task, Fiber fiber, Object owner, Suspend next, RuntimeException failure) {}

    private static final class Vm {
        final Instance actor;
        final ScriptEngine engine;
        final Map<String, List<Binding>> bindings = new HashMap<>();
        List<Call> calls = new ArrayList<>();

        Vm(Instance actor, ScriptEngine engine) {
            this.actor = actor;
            this.engine = engine;
        }
    }

    private final Host host;
    private final Map<Instance, Vm> byActor = new ConcurrentHashMap<>();
    private final Map<ScriptEngine, Vm> byEngine = new ConcurrentHashMap<>();
    private final Queue<Mail> mail = new ConcurrentLinkedQueue<>();
    private final Queue<String> printed = new ConcurrentLinkedQueue<>();
    private final Map<String, SharedTable> named = new ConcurrentHashMap<>();
    private final ThreadLocal<Vm> phase = new ThreadLocal<>();
    private ExecutorService workers;

    Parallel(Host host) {
        this.host = host;
    }

    static void install(Host host) {
        host.api().alias("SharedTable", "{ [string | number]: any }");
        Members tables = new Members("SharedTables")
                .function("new", "(data: { [any]: any }?) -> SharedTable", a -> SharedTable.from(a.get(0)))
                .function("named", "(name: string) -> SharedTable", a -> host.parallel().named(a.string(0)))
                .function("size", "(table: SharedTable) -> number", a -> (double) table(a).size())
                .function("clear", "(table: SharedTable) -> ()", a -> {
                    table(a).clear();
                    return null;
                })
                .function("clone", "(table: SharedTable, deep: boolean?) -> SharedTable", a -> table(a).copy(a.bool(1, false), false))
                .function("cloneAndFreeze", "(table: SharedTable, deep: boolean?) -> SharedTable", a -> table(a).copy(a.bool(1, false), true))
                .function("isFrozen", "(table: SharedTable) -> boolean", a -> table(a).frozen())
                .function("increment", "(table: SharedTable, key: string | number, delta: number) -> number",
                        a -> table(a).increment(a.get(1), a.number(2)))
                .function("update", "(table: SharedTable, key: string | number, fn: (value: any) -> any) -> ()", a -> {
                    table(a).update(a.get(1), a.callable(2));
                    return null;
                })
                .parallelAll();
        tables.declareMethod("__call", "(data: { [any]: any }?) -> SharedTable");
        host.global("sharedTable", "SharedTables", new Invocable() {
            @Override
            public Object invoke(Args args) {
                return SharedTable.from(args.get(0));
            }

            @Override
            public boolean parallel() {
                return true;
            }

            @Override
            public String typeName() {
                return tables.typeName();
            }

            @Override
            public Object get(String key) {
                return tables.get(key);
            }
        });
        host.declare(tables);

        host.instances().of(Classes.ACTOR)
                .method("sendMessage", "(topic: string, ...any) -> ()", a -> {
                    host.parallel().send(a.self(), a.string(1), a.from(2));
                    return null;
                })
                .method("bindToMessage", "(topic: string, handler: (...any) -> ()) -> Connection",
                        a -> host.parallel().bind(a.self(), a.string(1), a.callable(2), false))
                .method("bindToMessageParallel", "(topic: string, handler: (...any) -> ()) -> Connection",
                        a -> host.parallel().bind(a.self(), a.string(1), a.callable(2), true))
                .parallel("sendMessage");

        host.instances().shared()
                .method("getActor", "() -> Instance?", a -> actorOf(a.self()))
                .parallel("getActor", "children", "find", "isA", "hasTag", "getTags", "findFirstDescendant", "descendants",
                        "childrenOfClass", "firstAncestorOfClass", "firstChildOfClass", "firstAncestor", "isDescendantOf",
                        "isAncestorOf", "getFullName", "values", "getAttribute", "getAttributes");
    }

    private static SharedTable table(Args a) {
        if (a.get(0) instanceof SharedTable table) return table;
        throw a.error("argument 1 expects a SharedTable, got %s", Host.typeOf(a.get(0)));
    }

    public static Instance actorOf(Instance instance) {
        for (Instance at = instance; at != null; at = at.parent()) {
            if (at instanceof Actor) return at;
        }
        return null;
    }

    ScriptEngine engineFor(Instance script) {
        Instance actor = actorOf(script);
        if (actor == null) return null;
        return byActor.computeIfAbsent(actor, key -> {
            Vm vm = new Vm(key, host.language().start(host));
            byEngine.put(vm.engine, vm);
            return vm;
        }).engine;
    }

    public boolean active() {
        return phase.get() != null;
    }

    private Vm current() {
        Vm running = phase.get();
        if (running != null) return running;
        ScriptEngine engine = host.engine();
        return engine == null ? null : byEngine.get(engine);
    }

    public ScriptEngine running() {
        Vm running = phase.get();
        return running == null ? null : running.engine;
    }

    public void guard(String what) {
        if (active()) throw new HostError("%s cannot be called in parallel, call task.synchronize() first", what);
    }

    Object desynchronize() {
        if (active()) return null;
        if (current() == null) throw new HostError("task.desynchronize can only be called from a script inside an Actor");
        return DESYNCHRONIZE;
    }

    Object synchronize() {
        if (active()) return SYNCHRONIZE;
        if (current() == null) throw new HostError("task.synchronize can only be called from a script inside an Actor");
        return null;
    }

    Connection connect(HostSignal signal, Callable fn) {
        Vm vm = current();
        if (vm == null) throw new HostError("connectParallel can only be used by a script inside an Actor");
        Callable kept = fn.retain();
        Object owner = host.ownership().current();
        Connection[] link = new Connection[1];
        link[0] = signal.connect(new Callable() {
            @Override
            public Object[] call(Object... args) {
                vm.calls.add(new Call(kept, args, owner, link[0]));
                return null;
            }

            @Override
            public void release() {
                kept.release();
            }
        });
        return link[0];
    }

    private Connection bind(Instance actor, String topic, Callable fn, boolean parallel) {
        Vm vm = current();
        if (vm == null || vm.actor != actor) {
            throw new HostError("%s can only be used by a script inside this Actor", parallel ? "bindToMessageParallel" : "bindToMessage");
        }
        Callable kept = fn.retain();
        Object owner = host.ownership().current();
        List<Binding> bound = vm.bindings.computeIfAbsent(topic, key -> new ArrayList<>());
        Binding[] made = new Binding[1];
        Connection connection = new Connection(() -> {
            bound.remove(made[0]);
            kept.release();
        });
        made[0] = new Binding(kept, owner, parallel, connection);
        bound.add(made[0]);
        host.ownership().onRelease(owner, connection::disconnect);
        return connection;
    }

    private void send(Instance actor, String topic, Object[] args) {
        for (Object arg : args) check(arg);
        mail.add(new Mail(actor, topic, args));
    }

    private static void check(Object value) {
        switch (value) {
            case null -> { }
            case Boolean b -> { }
            case Number n -> { }
            case String s -> { }
            case Instance i -> { }
            case SharedTable t -> { }
            case List<?> list -> list.forEach(Parallel::check);
            case Map<?, ?> map -> map.values().forEach(Parallel::check);
            default -> {
                if (!Values.isValue(value)) throw new HostError("a message cannot carry a %s", Host.typeOf(value));
            }
        }
    }

    private SharedTable named(String name) {
        if (name.isEmpty()) throw new HostError("sharedTable.named needs a name");
        return named.computeIfAbsent(name, key -> new SharedTable());
    }

    void print(String line) {
        printed.add(line);
    }

    void run() {
        if (byActor.isEmpty()) {
            mail.clear();
            return;
        }
        deliver();
        Map<Vm, List<Object>> work = new LinkedHashMap<>();
        for (Scheduler.Resumption waiting : host.scheduler().takeDesynced()) {
            Vm vm = waiting.task().fiber.engine() == null ? null : byEngine.get(waiting.task().fiber.engine());
            if (vm != null) work.computeIfAbsent(vm, key -> new ArrayList<>()).add(waiting);
        }
        for (Vm vm : byActor.values()) {
            if (vm.calls.isEmpty()) continue;
            work.computeIfAbsent(vm, key -> new ArrayList<>()).addAll(vm.calls);
            vm.calls = new ArrayList<>();
        }
        if (!work.isEmpty()) settle(phase(work));
        host.scheduler().resumeSynced();
        deliver();
    }

    private List<Outcome> phase(Map<Vm, List<Object>> work) {
        long started = System.nanoTime();
        List<Future<List<Outcome>>> running = new ArrayList<>();
        for (Map.Entry<Vm, List<Object>> entry : work.entrySet()) {
            running.add(workers().submit(() -> work(entry.getKey(), entry.getValue())));
        }
        List<Outcome> outcomes = new ArrayList<>();
        RuntimeException broken = null;
        boolean interrupted = false;
        for (Future<List<Outcome>> future : running) {
            while (true) {
                try {
                    outcomes.addAll(future.get());
                    break;
                } catch (InterruptedException e) {
                    interrupted = true;
                } catch (ExecutionException e) {
                    if (broken == null) broken = new IllegalStateException("a parallel worker failed", e.getCause());
                    break;
                }
            }
        }
        if (interrupted) Thread.currentThread().interrupt();
        host.profiler().add("parallel", System.nanoTime() - started);
        String line;
        while ((line = printed.poll()) != null) host.print(line);
        if (broken != null) host.error("parallel", broken);
        return outcomes;
    }

    private List<Outcome> work(Vm vm, List<Object> items) {
        List<Outcome> out = new ArrayList<>();
        phase.set(vm);
        ScriptEngine before = host.enter(vm.engine);
        try {
            for (Object item : items) {
                switch (item) {
                    case Scheduler.Resumption waiting -> {
                        Scheduler.Task task = waiting.task();
                        if (task.done) continue;
                        try {
                            out.add(new Outcome(task, null, null, task.fiber.resume(waiting.values()), null));
                        } catch (RuntimeException e) {
                            out.add(new Outcome(task, null, null, null, e));
                        }
                    }
                    case Call call -> {
                        if (!call.connection().connected()) continue;
                        Fiber fiber = null;
                        try {
                            fiber = vm.engine.fiber(call.fn());
                            if (fiber != null) out.add(new Outcome(null, fiber, call.owner(), fiber.resume(call.args()), null));
                        } catch (RuntimeException e) {
                            out.add(new Outcome(null, fiber, call.owner(), null, e));
                        }
                    }
                    default -> { }
                }
            }
        } finally {
            host.leave(before);
            phase.remove();
        }
        return out;
    }

    private void settle(List<Outcome> outcomes) {
        for (Outcome outcome : outcomes) {
            if (outcome.task() != null) {
                if (outcome.failure() != null) host.scheduler().failed(outcome.task(), outcome.failure());
                else host.scheduler().settle(outcome.task(), outcome.next());
            } else if (outcome.failure() != null) {
                host.error("parallel", outcome.failure());
            } else if (outcome.next() != null) {
                host.scheduler().adopt(outcome.fiber(), outcome.owner(), outcome.next());
            }
        }
    }

    private void deliver() {
        for (int left = mail.size(); left > 0; left--) {
            Mail next = mail.poll();
            if (next == null) return;
            Vm vm = byActor.get(next.actor());
            List<Binding> bound = vm == null ? null : vm.bindings.get(next.topic());
            if (bound == null) continue;
            for (Binding binding : List.copyOf(bound)) {
                if (!binding.connection().connected()) continue;
                if (binding.parallel()) {
                    vm.calls.add(new Call(binding.fn(), next.args(), binding.owner(), binding.connection()));
                    continue;
                }
                Object before = host.ownership().enter(binding.owner());
                try {
                    Tasks.spawn(host, binding.fn(), next.args());
                } finally {
                    host.ownership().leave(before);
                }
            }
        }
    }

    private ExecutorService workers() {
        if (workers == null) {
            int count = Math.max(1, Runtime.getRuntime().availableProcessors());
            workers = Executors.newFixedThreadPool(count, Thread.ofPlatform().name("moud-parallel-", 1).daemon().stackSize(STACK).factory());
        }
        return workers;
    }

    void sweep() {
        for (Vm vm : List.copyOf(byActor.values())) {
            if (vm.actor.isAlive() && vm.actor.tree() == host.world().tree()) continue;
            close(vm);
        }
    }

    private void close(Vm vm) {
        byActor.remove(vm.actor);
        byEngine.remove(vm.engine);
        vm.calls.clear();
        host.engineClosed(vm.engine);
        vm.engine.close();
    }

    void close() {
        for (Vm vm : List.copyOf(byActor.values())) close(vm);
        mail.clear();
        named.clear();
        if (workers != null) workers.shutdownNow();
        workers = null;
    }
}
