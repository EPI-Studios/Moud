package com.moud.server.plugin;

import com.moud.plugin.api.MoudPlugin;
import com.moud.plugin.api.PluginDescription;
import com.moud.server.plugin.context.PluginContextImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLClassLoader;
import java.nio.file.Path;

@SuppressWarnings("unused")
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

    /**
     * Get the plugin description.
     * @return the plugin description
     */
    PluginDescription description() {
        return context.description();
    }

    /**
     * Enable the plugin.
     * @throws Exception if an error occurs during enabling
     */
    void enable() throws Exception {
        plugin.onLoad(context);
        LOGGER.info("Loaded plugin {} ({}) from {}", plugin.description().name(), plugin.description().id(), source.getFileName());
        plugin.onEnable(context);
        LOGGER.info("✔ Plug-in activated : {} {}", plugin.description().name(), plugin.description().version());

    }

    /**
     * Disable and shutdown the plugin
     */
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

    /**
     * Get the source path of the plugin.
     * @return the source path
     */
    Path source() {
        return source;
    }
}
