package com.moud.physics.rapier;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class RapierNatives {

    private static volatile boolean loaded;

    private RapierNatives() {}

    public static synchronized void load() {
        if (loaded) return;
        String dir = platformDir();
        String lib = libFileName();
        String resource = "/natives/" + dir + "/" + lib;
        try (InputStream in = RapierNatives.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new UnsupportedOperationException(
                        "Rapier native not bundled for " + dir + " (looked for " + resource + ")");
            }
            Path tmp = Files.createTempFile("rapier_moud-", suffix());
            tmp.toFile().deleteOnExit();
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            System.load(tmp.toAbsolutePath().toString());
            loaded = true;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to extract Rapier native " + resource, e);
        }
    }

    private static String platformDir() {
        String os   = System.getProperty("os.name", "").toLowerCase();
        String arch = System.getProperty("os.arch", "").toLowerCase();
        boolean arm = arch.contains("aarch64") || arch.contains("arm64");
        if (os.contains("linux"))   return arm ? "linux-arm64"   : "linux-x64";
        if (os.contains("mac"))     return arm ? "macos-arm64"   : "macos-x64";
        if (os.contains("windows")) return "windows-x64";
        throw new UnsupportedOperationException("Unsupported OS: " + os + "/" + arch);
    }

    private static String libFileName() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("windows")) return "rapier_moud.dll";
        if (os.contains("mac"))     return "librapier_moud.dylib";
        return "librapier_moud.so";
    }

    private static String suffix() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("windows")) return ".dll";
        if (os.contains("mac"))     return ".dylib";
        return ".so";
    }
}
