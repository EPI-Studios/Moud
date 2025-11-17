package com.moud.server.plugin;

import com.moud.plugin.api.MoudPlugin;
import com.moud.plugin.api.PluginDescription;
import com.moud.server.plugin.context.PluginContextImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

public final class PluginManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(PluginManager.class);

    private final Path pluginDirectory;
    private final Path projectRoot;
    private final Map<String, PluginRuntime> activePlugins = new ConcurrentHashMap<>();

    public PluginManager(Path projectRoot) {
        this.projectRoot = projectRoot;
        this.pluginDirectory = projectRoot.resolve(".moud").resolve("plugins");
        try {
            Files.createDirectories(pluginDirectory);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create plugin directory " + pluginDirectory, e);
        }
    }

    public void loadPlugins() {
        try (DirectoryStream<Path> jars = Files.newDirectoryStream(pluginDirectory, "*.jar")) {
            for (Path jar : jars) {
                loadJar(jar);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to enumerate plugin directory {}", pluginDirectory, e);
        }
    }

    private void loadJar(Path jarPath) {
        URL url;
        try {
            url = jarPath.toUri().toURL();
        } catch (MalformedURLException e) {
            LOGGER.error("Invalid plugin path {}", jarPath, e);
            return;
        }

        URLClassLoader loader = new URLClassLoader(new URL[]{url}, PluginManager.class.getClassLoader());
        ServiceLoader<MoudPlugin> serviceLoader = ServiceLoader.load(MoudPlugin.class, loader);
        boolean loaded = false;
        try {
            for (MoudPlugin plugin : serviceLoader) {
                PluginDescription description = plugin.description();
                if (description == null) {
                    LOGGER.warn("Plugin in {} does not provide description, skipping", jarPath);
                    continue;
                }
            if (activePlugins.containsKey(description.id())) {
                LOGGER.warn("Plugin id {} already loaded, skipping {}", description.id(), jarPath);
                continue;
            }
            PluginContextImpl context = new PluginContextImpl(description, projectRoot, plugin);
            PluginRuntime runtime = new PluginRuntime(plugin, context, loader, jarPath);
            try {
                runtime.enable();
                activePlugins.put(description.id(), runtime);
                LOGGER.info("Loaded plugin {} ({}) from {}", description.name(), description.id(), jarPath.getFileName());
                loaded = true;
                } catch (Exception e) {
                    LOGGER.error("Failed to enable plugin {} from {}", description.id(), jarPath, e);
                    runtime.disable();
                }
            }
        } catch (Throwable error) {
            LOGGER.error("Failed to load plugin definitions from {}", jarPath, error);
        }
        if (!loaded) {
            LOGGER.warn("No plugin entry found in {}", jarPath);
            try {
                loader.close();
            } catch (IOException e) {
                LOGGER.warn("Failed to close classloader for {}", jarPath, e);
            }
        }
    }

    public void shutdown() {
        activePlugins.values().forEach(PluginRuntime::disable);
        activePlugins.clear();
    }
}
