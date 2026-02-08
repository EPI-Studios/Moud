package com.moud.plugin.api.services;

import com.moud.plugin.api.services.scheduler.ScheduledTask;

import java.time.Duration;

public interface SchedulerService {
    ScheduledTask runLater(Runnable runnable, Duration delay);
    ScheduledTask runRepeating(Runnable runnable, Duration delay, Duration interval);
    ScheduledTask runAsync(Runnable runnable);
    void cancelAll();
}
