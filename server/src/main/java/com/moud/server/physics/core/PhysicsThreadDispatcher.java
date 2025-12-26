package com.moud.server.physics.core;

import com.moud.server.logging.MoudLogger;

import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class PhysicsThreadDispatcher {
    private final Queue<Runnable> queue = new ConcurrentLinkedQueue<>();
    private volatile Thread physicsThread;

    public void setPhysicsThread(Thread thread) {
        this.physicsThread = thread;
    }

    public boolean isOnPhysicsThread() {
        Thread currentPhysicsThread = physicsThread;
        return currentPhysicsThread != null && Thread.currentThread() == currentPhysicsThread;
    }

    public void execute(Runnable task) {
        Objects.requireNonNull(task, "task");
        if (isOnPhysicsThread()) {
            task.run();
            return;
        }
        queue.add(task);
    }

    public void drain(MoudLogger logger) {
        Runnable command;
        while ((command = queue.poll()) != null) {
            try {
                command.run();
            } catch (Exception e) {
                if (logger != null) {
                    logger.error("Error executing queued physics command", e);
                }
            }
        }
    }
}

