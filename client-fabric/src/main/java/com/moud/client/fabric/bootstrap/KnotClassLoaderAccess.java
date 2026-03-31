package com.moud.client.fabric.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Injects engine jars/directories into Fabric's KnotClassLoader at preLaunch time.
 * <p>
 * Uses the same reflection technique as EarlyLoadingScreen:
 * accesses KnotClassDelegate's internal {@code addCodeSource(Path)} and
 * {@code setAllowedPrefixes(Path, String[])} methods.
 */
final class KnotClassLoaderAccess {

    private static final Logger LOGGER = LoggerFactory.getLogger("moud-bootstrap");

    private KnotClassLoaderAccess() {}

    /**
     * Injects all jars found under {@code engineDir} into the current thread's
     * context classloader (expected to be Knot's classloader).
     */
    static void injectEngine(Path engineDir) {
        if (engineDir == null || !Files.isDirectory(engineDir)) {
            LOGGER.warn("[moud-bootstrap] Engine directory not found: {}", engineDir);
            return;
        }

        ClassLoader contextCl = Thread.currentThread().getContextClassLoader();
        if (contextCl == null) {
            LOGGER.warn("[moud-bootstrap] No context classloader, cannot inject engine");
            return;
        }

        try {
            // Get the KnotClassDelegate from the classloader
            Method getDelegate = contextCl.getClass().getDeclaredMethod("getDelegate");
            getDelegate.setAccessible(true);
            Object delegate = getDelegate.invoke(contextCl);

            Class<?> delegateClass = Class.forName("net.fabricmc.loader.impl.launch.knot.KnotClassDelegate");

            Method setAllowedPrefixes = delegateClass.getDeclaredMethod("setAllowedPrefixes", Path.class, String[].class);
            setAllowedPrefixes.setAccessible(true);

            Method addCodeSource = delegateClass.getDeclaredMethod("addCodeSource", Path.class);
            addCodeSource.setAccessible(true);

            // Find all jars in the engine directory (including subdirectories like engine/mods/)
            try (Stream<Path> walk = Files.walk(engineDir, 3)) {
                walk.filter(p -> p.getFileName().toString().endsWith(".jar"))
                    .filter(Files::isRegularFile)
                    .forEach(jar -> {
                        try {
                            LOGGER.info("[moud-bootstrap] Injecting: {}", jar);
                            setAllowedPrefixes.invoke(delegate, jar, new String[0]);
                            addCodeSource.invoke(delegate, jar);
                        } catch (Exception e) {
                            LOGGER.error("[moud-bootstrap] Failed to inject jar: {}", jar, e);
                        }
                    });
            }

            LOGGER.info("[moud-bootstrap] Engine injection complete from {}", engineDir);

        } catch (NoSuchMethodException e) {
            LOGGER.error("[moud-bootstrap] Fabric Loader version incompatible — KnotClassDelegate API not found. " +
                    "Engine injection skipped.", e);
        } catch (Exception e) {
            LOGGER.error("[moud-bootstrap] Failed to inject engine into classloader", e);
        }
    }
}
