package com.moud.server.plugin;

import com.moud.plugin.api.Plugin;
import com.moud.plugin.api.PluginApi;
import com.moud.server.assets.AssetsExtractor;
import com.moud.server.plugin.context.PluginContextImpl;
import com.moud.server.plugin.core.DependencyResolver;
import com.moud.server.plugin.core.PluginClassLoader;
import com.moud.server.plugin.core.PluginContainer;
import com.moud.server.plugin.core.PluginDescription;
import com.moud.server.plugin.core.PluginManager;
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
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class PluginLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(PluginLoader.class);
    private static Path PLUGINS_DIR;

    private final PluginManager manager;

    public PluginLoader(PluginManager manager) {
        this.manager = manager;
        PLUGINS_DIR = manager.getPluginDir();
    }

    /**
     * Load only the assets of all plugins present in the plugins directory.
     * <p>
     * For each plugin JAR that contains an {@code assets/} folder, its content is copied to:
     * <pre>
     *   projectRoot/assets/&lt;jarFileNameWithoutExtension&gt;/
     * </pre>
     *
     * Behavior:
     * <ul>
     *     <li>Does not delete any existing directories.</li>
     *     <li>Replaces only the files that are present in the JAR (using {@code REPLACE_EXISTING}).</li>
     *     <li>Leaves any extra/custom files (not present in the JAR) untouched.</li>
     * </ul>
     */
    public void loadAssets() {
        if (!Files.isDirectory(PLUGINS_DIR)) {
            LOGGER.warn("Plugins directory does not exist or is not a directory: {}", PLUGINS_DIR);
            return;
        }

        try (DirectoryStream<Path> ds = Files.newDirectoryStream(PLUGINS_DIR, "*.jar")) {
            for (Path jarPath : ds) {
                try (JarFile jar = new JarFile(jarPath.toFile())) {
                    JarEntry assetsEntry = jar.getJarEntry("assets/");
                    if (assetsEntry != null && assetsEntry.isDirectory()) {
                        LOGGER.info("Assets folder detected in plugin JAR: {}", jarPath.getFileName());
                        try {
                            AssetsExtractor.copyAssetsFromJar(jar, jarPath, manager.getProjectRoot());
                        } catch (IOException e) {
                            LOGGER.error("Error copying assets from plugin: {}", jarPath.getFileName(), e);
                        }
                    }
                } catch (IOException e) {
                    LOGGER.error("Error while reading plugin JAR for assets: {}", jarPath.getFileName(), e);
                }
            }
        } catch (IOException e) {
            LOGGER.error("Error while scanning plugins directory for assets", e);
        }
    }

    /**
     * Load and enable all plugins (without handling assets).
     * <p>
     * Steps:
     * <ol>
     *     <li>Scan the plugins directory for {@code *.jar} files.</li>
     *     <li>Inspect each JAR to read {@code plugin.yml} and build a {@link PluginContainer}.</li>
     *     <li>Resolve dependencies between plugins using {@link DependencyResolver}.</li>
     *     <li>Instantiate each plugin and call its lifecycle ({@code enable()}).</li>
     * </ol>
     * <p>
     */
    public void loadPlugins() {
        try {
            List<PluginContainer> discovered = new ArrayList<>();
            if (Files.isDirectory(PLUGINS_DIR)) {
                try (DirectoryStream<Path> ds = Files.newDirectoryStream(PLUGINS_DIR, "*.jar")) {
                    for (Path jarPath : ds) {
                        PluginContainer pc = inspectJar(jarPath);
                        if (pc != null) {
                            LOGGER.info("Plug-in detected: {} {}", pc.getDescription().name, pc.getDescription().version);
                            discovered.add(pc);
                        }
                    }
                }
            }

            List<PluginContainer> sorted = DependencyResolver.sort(discovered);

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
                    LOGGER.info("✔ Plug-in activated: {} {}", pc.getDescription().name, pc.getDescription().version);
                } catch (Exception e) {
                    LOGGER.error("Failed to enable plugin {} from {}", description.id, jarPath, e);
                    runtime.disable();
                }
                pc.setInstance(plugin);
                manager.register(pc.getInstance());
            }
        } catch (Exception e) {
            LOGGER.error("Error while loading plugins", e);
        }
    }

    /**
     * Inspect a single plugin JAR file and build a {@link PluginContainer} for it.
     * <p>
     * This method:
     * <ul>
     *     <li>Checks for the presence of {@code plugin.yml} at the root of the JAR.</li>
     *     <li>Parses {@link PluginDescription} from {@code plugin.yml}.</li>
     *     <li>Validates {@code api-version} against {@link PluginApi#API_VERSION}.</li>
     *     <li>Creates an isolated {@link PluginClassLoader} for the plugin.</li>
     * </ul>
     *
     * @param jarPath the path to the plugin JAR.
     * @return a {@link PluginContainer} if the JAR is a valid plugin, or {@code null} if:
     * <ul>
     *     <li>{@code plugin.yml} is missing, or</li>
     *     <li>{@code api-version} is incompatible.</li>
     * </ul>
     * @throws IOException if the JAR cannot be opened or read.
     *
     * <p><b>Note:</b> This method does <u>not</u> handle assets anymore.
     * Asset extraction is performed separately in {@link #loadAssets()}.
     */
    private PluginContainer inspectJar(Path jarPath) throws IOException {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            JarEntry entry = jar.getJarEntry("plugin.yml");
            if (entry == null) {
                LOGGER.warn("The plugin.yml file is missing in plugin: {}", jarPath.getFileName());
                return null;
            }

            try (InputStream in = jar.getInputStream(entry)) {
                PluginDescription desc = new PluginDescription(in);

                // API compatibility check
                if (!PluginApi.API_VERSION.equals(desc.apiVersion)) {
                    LOGGER.warn(String.format("⚠ %s: api-version \"%s\" is incompatible (core=%s)",
                            jarPath.getFileName(), desc.apiVersion, PluginApi.API_VERSION));
                    return null;
                }

                // Isolated class loader for this plugin
                URL jarUrl = jarPath.toUri().toURL();
                PluginClassLoader cl = new PluginClassLoader(jarUrl, getClass().getClassLoader());

                return new PluginContainer(desc, cl);
            }
        }
    }
}
