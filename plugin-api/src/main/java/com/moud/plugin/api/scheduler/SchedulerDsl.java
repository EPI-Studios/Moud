package com.moud.plugin.api.scheduler;

import com.moud.plugin.api.services.SchedulerService;

import java.time.Duration;

/**
 * Wrapper around the scheduler service for interval tasks.
 */
public final class SchedulerDsl {
    private final SchedulerService scheduler;

    public SchedulerDsl(SchedulerService scheduler) {
        this.scheduler = scheduler;
    }

    public Interval every(long value) {
        return new Interval(value);
    }

    public final class Interval {
        private final long value;

        private Interval(long value) {
            this.value = value;
        }

        /**
         * Run a task every N seconds.
         */
        public void seconds(Runnable runnable) {
            Duration interval = Duration.ofSeconds(value);
            scheduler.runRepeating(runnable, interval, interval);
        }

        /**
         * Run a task every N ticks (20 ticks = 1 second).
         */
        public void ticks(Runnable runnable) {
            Duration interval = Duration.ofMillis(value * 50L);
            scheduler.runRepeating(runnable, interval, interval);
        }

        /**
         * Run a task every N minutes.
         */
        public void minutes(Runnable runnable) {
            Duration interval = Duration.ofMinutes(value);
            scheduler.runRepeating(runnable, interval, interval);
        }
    }
}
