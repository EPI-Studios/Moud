package com.moud.server.plugin.newplugin.core;


import com.moud.plugin.api.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Enregistre les plug-ins actifs et gère leur cycle de vie. */
public final class PluginManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(PluginManager.class);
    private final Path pluginDirectory;
    private final Path projectRoot;
    private final List<Plugin> plugins = new ArrayList<>();

    public void register(Plugin plugin) { plugins.add(plugin); }

    public PluginManager(Path projectRoot) {
        this.projectRoot = projectRoot;
        this.pluginDirectory = projectRoot.resolve(".moud").resolve("plugins");
        try {
            Files.createDirectories(pluginDirectory);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create plugin directory " + pluginDirectory, e);
        }

    }

    public Plugin getPlugin(String pluginID){
        for (Plugin plugin : plugins) {
            if(plugin.description().id().equalsIgnoreCase(pluginID)){
                return plugin;
            }
        }
        return null;
    }

    /** Appelé à la fin de l'application ou lors d'un reload. */
    public void shutdown() {
        Collections.reverse(plugins);          // d'abord les dépendants
        for (Plugin p : plugins) {
            try { p.onDisable(); }
            catch (Exception e) { e.printStackTrace(); }
        }
        plugins.clear();
    }

    public Path getPluginDir() {
        return pluginDirectory;
    }

    public Path getProjectRoot() {
        return projectRoot;
    }
}
