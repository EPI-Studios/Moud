package com.meekdev.moud.script.host;

import java.util.concurrent.SynchronousQueue;
import java.util.function.Function;

public final class ThreadFiber implements Fiber {

    private static final ThreadLocal<ThreadFiber> CURRENT = new ThreadLocal<>();

    private sealed interface Signal {}

    private record Parked(Suspend suspend) implements Signal {}

    private record Finished(RuntimeException failure) implements Signal {}

    private record Resume(Object[] values) implements Signal {}

    private record Stop() implements Signal {}

    private static final class Stopped extends RuntimeException {
        Stopped() {
            super(null, null, false, false);
        }
    }

    private final Function<Object[], Object> body;
    private final String name;
    private final SynchronousQueue<Signal> toFiber = new SynchronousQueue<>();
    private final SynchronousQueue<Signal> toCaller = new SynchronousQueue<>();
    private Thread thread;
    private boolean done;

    public ThreadFiber(String name, Function<Object[], Object> body) {
        this.name = name;
        this.body = body;
    }

    public static boolean inside() {
        return CURRENT.get() != null;
    }

    public static Object[] await(Suspend suspend) {
        ThreadFiber fiber = CURRENT.get();
        if (fiber == null) {
            suspend.cancel().run();
            throw new HostError("cannot wait here, run this inside task.spawn or a script");
        }
        fiber.hand(fiber.toCaller, new Parked(suspend));
        Signal next = fiber.take(fiber.toFiber);
        if (next instanceof Resume resume) return resume.values();
        throw new Stopped();
    }

    @Override
    public Suspend resume(Object... values) {
        if (done) return null;
        if (thread == null) {
            thread = Thread.ofPlatform().name(name).daemon().unstarted(() -> run(values));
            thread.start();
        } else {
            hand(toFiber, new Resume(values));
        }
        return settle(take(toCaller));
    }

    private void run(Object[] values) {
        CURRENT.set(this);
        RuntimeException failure = null;
        try {
            body.apply(values);
        } catch (Stopped ignored) {
        } catch (RuntimeException e) {
            failure = e;
        } finally {
            CURRENT.remove();
        }
        hand(toCaller, new Finished(failure));
    }

    private Suspend settle(Signal signal) {
        return switch (signal) {
            case Parked parked -> parked.suspend();
            case Finished finished -> {
                done = true;
                if (finished.failure() != null) throw finished.failure();
                yield null;
            }
            default -> throw new IllegalStateException("unexpected " + signal);
        };
    }

    @Override
    public void cancel() {
        if (done || thread == null) {
            done = true;
            return;
        }
        done = true;
        hand(toFiber, new Stop());
        take(toCaller);
    }

    private void hand(SynchronousQueue<Signal> queue, Signal signal) {
        try {
            queue.put(signal);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new Stopped();
        }
    }

    private Signal take(SynchronousQueue<Signal> queue) {
        try {
            return queue.take();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new Stopped();
        }
    }
}
