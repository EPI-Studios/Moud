package com.moud.server.plugin;

import com.moud.plugin.api.Plugin;
import com.moud.server.plugin.context.PluginContextImpl;
import com.moud.server.plugin.core.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;

public class PluginLoader {


    private static final Logger LOGGER = LoggerFactory.getLogger(PluginLoader.class);
    public static final String API_VERSION = "1.0";               // mise à jour à chaque breaking change
    private static Path PLUGINS_DIR;

    private final PluginManager manager;

    public PluginLoader(PluginManager manager) {
        this.manager = manager;
        PLUGINS_DIR = manager.getPluginDir();
    }

    /**
     * Charge tous les plug-ins, gère dépendances et cycle de vie.
     */
    public void loadAll() {

        try {
            // 1) Découverte brutale des JAR
            List<PluginContainer> discovered = new ArrayList<>();
            if (Files.isDirectory(PLUGINS_DIR)) {
                try (DirectoryStream<Path> ds = Files.newDirectoryStream(PLUGINS_DIR, "*.jar")) {
                    for (Path jarPath : ds) {
                        PluginContainer pc = inspectJar(jarPath);
                        if (pc != null) {
                            LOGGER.info("Plug-in détecté : " + pc.getDescription().name + " " + pc.getDescription().version);
                            discovered.add(pc);
                        }
                    }
                }
            }

            // 2) Tri topologique par dépendances
            List<PluginContainer> sorted = DependencyResolver.sort(discovered);

            // 3) Cycle de vie
            for (PluginContainer pc : sorted) {
                Plugin plugin =
                        (Plugin) pc.getClassLoader().loadClass(pc.getDescription().mainClass)
                                .getDeclaredConstructor().newInstance();

                PluginDescription description = pc.getDescription();
                URL jarURL = pc.getClassLoader().getURLs()[0];
                Path jarPath = Paths.get(jarURL.toURI());
                PluginClassLoader loader = pc.getClassLoader();

                PluginContextImpl context = new PluginContextImpl(description, manager.getProjectRoot(), plugin);
                PluginRuntime runtime = new PluginRuntime(plugin, context, loader, jarPath);

                try {
                    runtime.enable();
                    LOGGER.info("✔ Plug-in activé : {} {}", pc.getDescription().name, pc.getDescription().version);
                } catch (Exception e) {
                    LOGGER.error("Failed to enable plugin {} from {}", description.id, jarPath, e);
                    runtime.disable();
                }
                pc.setInstance(plugin);
                manager.register(pc.getInstance());
            }
        } catch (Exception e) {
            LOGGER.error("Erreur lors du chargement des plugins", e);
        }


    }

    /**
     * Inspecte un JAR, renvoie null si pas de plugin.yml ou api-version incompatible.
     */
    private PluginContainer inspectJar(Path jarPath) throws IOException {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            var entry = jar.getJarEntry("plugin.yml");
            if (entry == null) {
                LOGGER.warn("Le fichier plugin.yml est manquant dans le plugin " + jarPath.getFileName());
                return null;
            }

            try (InputStream in = jar.getInputStream(entry)) {
                PluginDescription desc = new PluginDescription(in);

                // Vérif API
                if (!API_VERSION.equals(desc.apiVersion)) {
                    LOGGER.warn(String.format("⚠ %s : api-version \"%s\" incompatible (core=%s)",
                            jarPath.getFileName(), desc.apiVersion, API_VERSION));
                    return null;
                }

                // Chargeur isolé
                URL jarUrl = jarPath.toUri().toURL();
                PluginClassLoader cl = new PluginClassLoader(jarUrl, getClass().getClassLoader());

                return new PluginContainer(desc, cl);
            }
        }
    }
}
