package com.moud.plugin.api.services;

import com.moud.plugin.api.world.DisplayHandle;
import com.moud.plugin.api.world.DisplayOptions;
import com.moud.plugin.api.world.TextHandle;
import com.moud.plugin.api.world.TextOptions;
import net.minestom.server.instance.Instance;

import java.nio.file.Path;

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

    /**
     * Creates a media display surface in the world (images, videos, sequences).
     * @param options configuration for the display.
     * @return a handle to control the display.
     */
    DisplayHandle createDisplay(DisplayOptions options);

    /**
     * Creates floating text in the world.
     * @param options configuration for the text display.
     * @return a handle to control the text display.
     */
    TextHandle createText(TextOptions options);

    /**
     * Loads a world into a named instance.
     *
     * Supports Anvil world folders and `.polar` world save files.
     *
     * @param name      instance registry name for the world.
     * @param worldPath path to the world folder or file.
     * @return the loaded instance.
     */
    Instance loadWorld(String name, Path worldPath);

    /**
     * Loads a world into a named instance.
     *
     * Supports Anvil world folders and `.polar` world save files.
     *
     * @param name      instance registry name for the world.
     * @param worldPath path to the world folder or file.
     * @param sceneId   scene id used for world metadata (only used for `.polar` files).
     * @return the loaded instance.
     */
    Instance loadWorld(String name, Path worldPath, String sceneId);
}
