package com.moud.plugin.api.services.scheduler;

public interface ScheduledTask {
    void cancel();
    boolean cancelled();
}
