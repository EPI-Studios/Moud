package com.meekdev.moud.addon.revo;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;

final class Baton implements AutoCloseable {

    private static final long STACK = 64L << 20;
    private static final Runnable WAKE = () -> {};

    private final BlockingQueue<Runnable> toVm = new LinkedBlockingQueue<>();
    private final BlockingQueue<Runnable> toCaller = new LinkedBlockingQueue<>();
    private final Thread vm;
    private volatile boolean closed;

    Baton(String name) {
        vm = new Thread(null, this::serve, name, STACK);
        vm.setDaemon(true);
        vm.start();
    }

    private void serve() {
        while (!closed) {
            try {
                toVm.take().run();
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    boolean onVmThread() {
        return Thread.currentThread() == vm;
    }

    <T> T onVm(Callable<T> job) {
        if (onVmThread()) return direct(job);
        return handOff(job, toVm, toCaller);
    }

    <T> T onCaller(Callable<T> job) {
        if (!onVmThread()) return direct(job);
        return handOff(job, toCaller, toVm);
    }

    private <T> T handOff(Callable<T> job, BlockingQueue<Runnable> there, BlockingQueue<Runnable> here) {
        FutureTask<T> task = new FutureTask<>(job) {
            @Override
            protected void done() {
                here.add(WAKE);
            }
        };
        there.add(task);
        while (!task.isDone()) {
            try {
                here.take().run();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted while waiting for revo", e);
            }
        }
        try {
            return task.get();
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException runtime) throw runtime;
            if (e.getCause() instanceof Error error) throw error;
            throw new IllegalStateException(e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static <T> T direct(Callable<T> job) {
        try {
            return job.call();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void close() {
        closed = true;
        toVm.add(WAKE);
    }
}
