package com.moud.plugin.api.services;

/**
 * Exposes server world controls to plugins.
 */
public interface WorldService {
    /**
     * @return the current time of day (0-24000) in the primary instance.
     */
    long getTime();

    /**
     * Sets the current time of day.
     *
     * @param time value between 0 and 24000; vanilla semantics apply.
     */
    void setTime(long time);

    /**
     * @return The current rate at which time advances.
     */
    int getTimeRate();

    /**
     * Updates the speed at which time passes. Set to 0 to freeze the sun.
     *
     * @param timeRate non-negative rate multiplier.
     */
    void setTimeRate(int timeRate);

    /**
     * @return The tick interval between time synchronization packets.
     */
    int getTimeSynchronizationTicks();

    /**
     * Sets how frequently clients are synced to the server time (0 disables syncing).
     */
    void setTimeSynchronizationTicks(int ticks);
}
