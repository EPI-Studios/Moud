package com.moud.server.plugin.core;

import com.moud.plugin.api.Plugin;

public final class PluginContainer {

    private final PluginDescription desc;
    private final PluginClassLoader loader;
    private Plugin instance;

    public PluginContainer(PluginDescription desc, PluginClassLoader loader) {
        this.desc = desc;
        this.loader = loader;
    }


    /**Gets the plugin description.
     * @return the plugin description
     */
    public PluginDescription getDescription() {
        return desc;
    }

    /** Gets the plugin class loader.
     * @return the plugin class loader
     */
    public PluginClassLoader getClassLoader() {
        return loader;
    }

    /**
     * Gets the plugin instance.
     * @return the plugin instance
     */
    public Plugin getInstance() {
        return instance;
    }

    /**
     * Sets the plugin instance.
     * @param plugin the plugin instance
     */
    public void setInstance(Plugin plugin) {
        if (instance != null) {
            throw new IllegalStateException("Plugin instance already set for " + desc.name);
        }
        this.instance = plugin;
    }
}
