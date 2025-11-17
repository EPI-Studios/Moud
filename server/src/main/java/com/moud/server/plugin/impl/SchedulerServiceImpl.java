package com.moud.server.plugin.impl;

import com.moud.plugin.api.services.SchedulerService;
import com.moud.plugin.api.services.scheduler.ScheduledTask;
import net.minestom.server.MinecraftServer;
import net.minestom.server.timer.Scheduler;
import net.minestom.server.timer.Task;
import net.minestom.server.timer.TaskSchedule;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class SchedulerServiceImpl implements SchedulerService {
    private final String pluginId;
    private final Logger logger;
    private final Scheduler scheduler = MinecraftServer.getSchedulerManager();
    private final Set<ScheduledTask> tasks = ConcurrentHashMap.newKeySet();

    public SchedulerServiceImpl(String pluginId, Logger logger) {
        this.pluginId = pluginId;
        this.logger = logger;
    }

    @Override
    public ScheduledTask runLater(Runnable runnable, Duration delay) {
        Task task = scheduler.buildTask(wrap(runnable))
                .delay(TaskSchedule.duration(delay))
                .schedule();
        return track(task);
    }

    @Override
    public ScheduledTask runRepeating(Runnable runnable, Duration delay, Duration interval) {
        Task task = scheduler.buildTask(wrap(runnable))
                .delay(TaskSchedule.duration(delay))
                .repeat(TaskSchedule.duration(interval))
                .schedule();
        return track(task);
    }

    @Override
    public ScheduledTask runAsync(Runnable runnable) {
        CompletableFuture<?> future = CompletableFuture.runAsync(wrap(runnable));
        PluginFutureTask handle = new PluginFutureTask(future, this);
        tasks.add(handle);
        return handle;
    }

    private Runnable wrap(Runnable runnable) {
        return () -> {
            try {
                runnable.run();
            } catch (Throwable throwable) {
                logger.error("Plugin {} task threw an exception", pluginId, throwable);
            }
        };
    }

    private ScheduledTask track(Task task) {
        PluginScheduledTask handle = new PluginScheduledTask(task, this);
        tasks.add(handle);
        return handle;
    }

    void untrack(ScheduledTask task) {
        tasks.remove(task);
    }

    @Override
    public void cancelAll() {
        tasks.forEach(ScheduledTask::cancel);
        tasks.clear();
    }

    private static final class PluginScheduledTask implements ScheduledTask {
        private final Task task;
        private final SchedulerServiceImpl owner;
        private volatile boolean cancelled;

        private PluginScheduledTask(Task task, SchedulerServiceImpl owner) {
            this.task = task;
            this.owner = owner;
        }

        @Override
        public void cancel() {
            task.cancel();
            owner.untrack(this);
            cancelled = true;
        }

        @Override
        public boolean cancelled() {
            return cancelled;
        }
    }

    private static final class PluginFutureTask implements ScheduledTask {
        private final CompletableFuture<?> future;
        private final SchedulerServiceImpl owner;

        private PluginFutureTask(CompletableFuture<?> future, SchedulerServiceImpl owner) {
            this.future = future;
            this.owner = owner;
        }

        @Override
        public void cancel() {
            future.cancel(true);
            owner.untrack(this);
        }

        @Override
        public boolean cancelled() {
            return future.isCancelled();
        }
    }
}
