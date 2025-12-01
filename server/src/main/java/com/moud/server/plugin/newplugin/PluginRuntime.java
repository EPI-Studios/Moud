package com.moud.server.plugin.newplugin;

import com.moud.plugin.api.MoudPlugin;
import com.moud.plugin.api.PluginDescription;
import com.moud.server.plugin.context.PluginContextImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLClassLoader;
import java.nio.file.Path;

final class PluginRuntime {
    private static final Logger LOGGER = LoggerFactory.getLogger(PluginRuntime.class);

    private final MoudPlugin plugin;
    private final PluginContextImpl context;
    private final URLClassLoader classLoader;
    private final Path source;

    PluginRuntime(MoudPlugin plugin, PluginContextImpl context, URLClassLoader classLoader, Path source) {
        this.plugin = plugin;
        this.context = context;
        this.classLoader = classLoader;
        this.source = source;
    }

    PluginDescription description() {
        return context.description();
    }

    void enable() throws Exception {
        plugin.onLoad(context);
        LOGGER.info("Loaded plugin {} ({}) from {}", plugin.description().name(), plugin.description().id(), source.getFileName());
        plugin.onEnable(context);
        LOGGER.info("✔ Plug-in activé : {} {}", plugin.description().name(), plugin.description().version());

    }

    void disable() {
        try {
            plugin.onDisable();
        } catch (Exception e) {
            LOGGER.error("Plugin {} threw during onDisable", context.description().id(), e);
        } finally {
            context.shutdown();
            try {
                classLoader.close();
            } catch (Exception e) {
                LOGGER.warn("Failed to close class loader for plugin {}", context.description().id(), e);
            }
        }
    }

    Path source() {
        return source;
    }
}
