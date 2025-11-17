package com.moud.plugin.api;

/**
 * Base contract for every server-side plugin.
 */
public interface MoudPlugin {
    /**
     * Provides the plugin metadata that is used by the loader.
     */
    PluginDescription description();

    /**
     * Invoked right after the plugin instance is created and before it becomes active.
     */
    default void onLoad(PluginContext context) throws Exception {
    }

    /**
     * Invoked when the plugin should register listeners, commands, etc.
     */
    default void onEnable(PluginContext context) throws Exception {
    }

    /**
     * Invoked before the plugin is unloaded.
     */
    default void onDisable() {
    }
}
