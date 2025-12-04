package com.moud.server.assets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public final class AssetsExtractor {

    private static final Logger LOGGER = LoggerFactory.getLogger(AssetsExtractor.class);


    /**
     * Copy assets from the "assets/" folder inside the JAR to:
     * projectRoot/assets/<jarNameWithoutExtension>/
     * * - Does not delete any existing folders
     * - Only replaces files that already exist if necessary
     *
     * @param jarFile     already opened JAR (JarFile)
     * @param jarPath     path of the .jar file (to get the name)
     * @param projectRoot project root
     * @throws IOException in case of I/O error
     */
    public static void copyAssetsFromJar(JarFile jarFile, Path jarPath, Path projectRoot) throws IOException {
        String jarFileName = jarPath.getFileName().toString();

        final String ASSETS_PREFIX = "assets/";
        Path baseDest = projectRoot
                .resolve("assets");

        LOGGER.info("Assets folder detected in plugin : {}", jarFileName);
        LOGGER.info("Copying assets to: {}", baseDest);

        Enumeration<JarEntry> entries = jarFile.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String entryName = entry.getName();

            if (!entryName.startsWith(ASSETS_PREFIX)) {
                continue;
            }

            if (entry.isDirectory()) {
                continue;
            }

            String relativePath = entryName.substring(ASSETS_PREFIX.length());

            Path destFile = baseDest.resolve(relativePath);

            Path parentDir = destFile.getParent();
            if (parentDir != null) {
                Files.createDirectories(parentDir);
            }

            try (InputStream in = jarFile.getInputStream(entry)) {
                Files.copy(in, destFile, StandardCopyOption.REPLACE_EXISTING);
                LOGGER.debug("Copied asset file: {}", destFile);
            } catch (IOException e) {
                LOGGER.error("Error copying asset file '{}' to '{}'", entryName, destFile, e);
            }
        }

        LOGGER.info("Assets copy finished for plugin: {}", jarFileName);
    }
}
