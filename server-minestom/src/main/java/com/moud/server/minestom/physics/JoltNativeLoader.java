package com.moud.server.minestom.physics;

import com.moud.server.minestom.util.DebugLog;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Best-effort native loader for jolt-jni. By default it never uses the network.
 *
 * <p>Order:
 * <ol>
 *   <li>{@code MOUD_JOLT_NATIVE} env var: absolute path to native binary</li>
 *   <li>{@code -Dmoud.physics.native=/path/to/libjoltjni.so}</li>
 *   <li>{@code System.loadLibrary("joltjni")} (java.library.path)</li>
 *   <li>Optional managed download if enabled via {@code MOUD_JOLT_DOWNLOAD=1} or {@code -Dmoud.physics.autoDownload=true}</li>
 * </ol>
 */
final class JoltNativeLoader {
    private static final String LOG = "physics-native";
    private static final AtomicBoolean LOADED = new AtomicBoolean(false);

    private static final String VERSION = System.getProperty("moud.physics.nativeVersion", "3.5.0");
    private static final String DEFAULT_CLASSIFIER = System.getProperty("moud.physics.nativeClassifier", "DebugSp");
    private static final String PROP_NATIVE_PATH = "moud.physics.native";
    private static final String PROP_NATIVE_DIR = "moud.physics.nativeDir";

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();

    private JoltNativeLoader() {
    }

    static boolean loadOnce() {
        if (LOADED.get()) {
            return true;
        }
        synchronized (JoltNativeLoader.class) {
            if (LOADED.get()) {
                return true;
            }

            Optional<Path> explicit = explicitNativePath();
            if (explicit.isPresent()) {
                loadFromPath(explicit.get());
                return true;
            }

            Optional<Path> fromDir = nativeInConfiguredDir();
            if (fromDir.isPresent() && Files.isRegularFile(fromDir.get())) {
                loadFromPath(fromDir.get());
                return true;
            }

            try {
                System.loadLibrary("joltjni");
                DebugLog.info(LOG, "Loaded joltjni from java.library.path");
                LOADED.set(true);
                return true;
            } catch (UnsatisfiedLinkError ignored) {
                DebugLog.debug(LOG, "joltjni not found on java.library.path");
            }

            PlatformVariant variant = detectVariant().orElse(null);
            if (variant == null) {
                DebugLog.warn(LOG, "Unsupported OS/arch for jolt-jni");
                return false;
            }

            Path localLibrary = resolveLocalPath(variant);
            if (!Files.exists(localLibrary)) {
                try {
                    downloadAndExtract(variant, localLibrary);
                } catch (Exception ex) {
                    DebugLog.error(LOG,
                            "Failed to download jolt-jni native library automatically. " +
                                    "Set MOUD_JOLT_NATIVE (or -Dmoud.physics.native) to the extracted native file.",
                            ex);
                    return false;
                }
            }

            loadFromPath(localLibrary);
            return true;
        }
    }

    private static Optional<Path> explicitNativePath() {
        String env = System.getenv("MOUD_JOLT_NATIVE");
        if (env != null && !env.isBlank()) {
            Path path = Paths.get(env.trim());
            if (!Files.isRegularFile(path)) {
                throw new IllegalStateException("MOUD_JOLT_NATIVE does not exist: " + path.toAbsolutePath());
            }
            return Optional.of(path);
        }

        String configured = System.getProperty(PROP_NATIVE_PATH);
        if (configured == null || configured.isBlank()) {
            return Optional.empty();
        }
        Path path = Paths.get(configured.trim());
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException("Configured -D" + PROP_NATIVE_PATH + " does not exist: " + path.toAbsolutePath());
        }
        return Optional.of(path);
    }

    private static Optional<Path> nativeInConfiguredDir() {
        String baseDir = System.getProperty(PROP_NATIVE_DIR);
        if (baseDir == null || baseDir.isBlank()) {
            return Optional.empty();
        }
        PlatformVariant variant = detectVariant().orElse(null);
        if (variant == null) {
            return Optional.empty();
        }
        Path targetDir = Paths.get(baseDir.trim(), variant.directoryName);
        return Optional.of(targetDir.resolve(variant.libraryFileName));
    }

    private static void loadFromPath(Path path) {
        System.load(path.toAbsolutePath().toString());
        DebugLog.info(LOG, "Loaded joltjni from " + path.toAbsolutePath());
        LOADED.set(true);
    }

    private static Path resolveLocalPath(PlatformVariant variant) {
        String baseDir = System.getProperty(PROP_NATIVE_DIR, "libs/jolt");
        Path targetDir = Paths.get(baseDir, variant.directoryName);
        return targetDir.resolve(variant.libraryFileName);
    }

    private static void downloadAndExtract(PlatformVariant variant, Path targetPath) throws Exception {
        Files.createDirectories(targetPath.getParent());

        String classifier = DEFAULT_CLASSIFIER;
        String artifact = String.format("%s-%s-%s.jar", variant.artifactId, VERSION, classifier);
        String url = String.format(
                "https://repo1.maven.org/maven2/com/github/stephengold/%s/%s/%s",
                variant.artifactId,
                VERSION,
                artifact
        );

        DebugLog.info(LOG, "Downloading jolt-jni native: " + url);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .header("User-Agent", "MOUD/PhysicsNativeLoader")
                .GET()
                .build();
        HttpResponse<InputStream> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode() + " while downloading " + url);
        }

        Path tempJar = Files.createTempFile("joltjni", ".jar");
        try {
            try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(tempJar)) {
                in.transferTo(out);
            }

            boolean extracted = false;
            try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(tempJar))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    if (!entry.getName().endsWith(variant.librarySuffix())) {
                        continue;
                    }
                    Files.createDirectories(targetPath.getParent());
                    try (OutputStream out = Files.newOutputStream(targetPath)) {
                        zis.transferTo(out);
                    }
                    extracted = true;
                    break;
                }
            }

            if (!extracted) {
                throw new IOException("Native library not found inside downloaded artifact: " + url);
            }
        } finally {
            try {
                Files.deleteIfExists(tempJar);
            } catch (Exception ignored) {
            }
        }

        DebugLog.info(LOG, "Installed jolt-jni native to " + targetPath.toAbsolutePath());
    }

    private static Optional<PlatformVariant> detectVariant() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        if (osName.contains("win")) {
            if (arch.contains("64")) {
                return Optional.of(PlatformVariant.WINDOWS_X64);
            }
            return Optional.empty();
        }
        if (osName.contains("mac") || osName.contains("darwin")) {
            if (arch.contains("aarch") || arch.contains("arm")) {
                return Optional.of(PlatformVariant.MAC_ARM64);
            }
            return Optional.of(PlatformVariant.MAC_X64);
        }
        if (osName.contains("linux")) {
            if (arch.contains("aarch") || arch.contains("arm")) {
                return Optional.of(PlatformVariant.LINUX_ARM64);
            }
            return Optional.of(PlatformVariant.LINUX_X64);
        }
        return Optional.empty();
    }

    private static boolean parseBool(String v) {
        if (v == null) {
            return false;
        }
        String s = v.trim().toLowerCase(Locale.ROOT);
        return "1".equals(s) || "true".equals(s) || "yes".equals(s) || "y".equals(s) || "on".equals(s);
    }

    private enum PlatformVariant {
        LINUX_X64("linux-x86_64", "jolt-jni-Linux64", "libjoltjni.so"),
        LINUX_ARM64("linux-aarch64", "jolt-jni-Linux_ARM64", "libjoltjni.so"),
        WINDOWS_X64("windows-x86_64", "jolt-jni-Windows64", "joltjni.dll"),
        MAC_X64("macos-x86_64", "jolt-jni-MacOSX", "libjoltjni.dylib"),
        MAC_ARM64("macos-aarch64", "jolt-jni-MacOSX_ARM64", "libjoltjni.dylib");

        final String directoryName;
        final String artifactId;
        final String libraryFileName;

        PlatformVariant(String directoryName, String artifactId, String libraryFileName) {
            this.directoryName = directoryName;
            this.artifactId = artifactId;
            this.libraryFileName = libraryFileName;
        }

        String librarySuffix() {
            int dot = libraryFileName.lastIndexOf('.');
            return dot < 0 ? libraryFileName : libraryFileName.substring(dot);
        }
    }
}
