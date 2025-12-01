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


    public PluginDescription getDescription() {
        return desc;
    }

    public PluginClassLoader getClassLoader() {
        return loader;
    }

    public Plugin getInstance() {
        return instance;
    }

    public void setInstance(Plugin plugin) {
        if (instance != null) {
            throw new IllegalStateException("Plugin instance already set for " + desc.name);
        }
        this.instance = plugin;
    }
}
