package com.moud.server.plugin.core;


import com.moud.plugin.api.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Save the active plugins and manage their lifecycle.
 */
@SuppressWarnings("unused")
public final class PluginManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(PluginManager.class);
    private final Path pluginDirectory;
    private final Path projectRoot;
    private final List<Plugin> plugins = new ArrayList<>();

    public void register(Plugin plugin) {
        plugins.add(plugin);
    }


    /**
     * Constructeur.
     * create the plugin directory if it does not exist.
     *
     * @param projectRoot the root path of the project (where server launches)
     */
    public PluginManager(Path projectRoot) {
        this.projectRoot = projectRoot;
        this.pluginDirectory = projectRoot.resolve(".moud").resolve("plugins");
        try {
            Files.createDirectories(pluginDirectory);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create plugin directory " + pluginDirectory, e);
        }

    }

    /**
     * Get a plugin by its ID.
     *
     * @param pluginID the plugin ID
     * @return the plugin, or null if not found
     */
    public Plugin getPlugin(String pluginID) {
        for (Plugin plugin : plugins) {
            if (plugin.description().id().equalsIgnoreCase(pluginID)) {
                return plugin;
            }
        }
        return null;
    }

    /**
     * Apply onDisable to all plugins in reverse order of loading.
     */
    public void shutdown() {
        Collections.reverse(plugins);          // d'abord les dépendants
        for (Plugin p : plugins) {
            try {
                p.onDisable();
            } catch (Exception e) {
                LOGGER.error("Failed to shutdown plugin " + p, e);
            }
        }
        plugins.clear();
    }

    /**
     * Get the plugin directory path.
     *
     * @return the plugin directory path
     */
    public Path getPluginDir() {
        return pluginDirectory;
    }

    /**
     * Get the project root path.
     *
     * @return the project root path
     */
    public Path getProjectRoot() {
        return projectRoot;
    }
}
